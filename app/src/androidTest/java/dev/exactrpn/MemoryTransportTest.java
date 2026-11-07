// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import android.os.FileObserver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public final class MemoryTransportTest {
    private FileObserver watch(File directory, AtomicInteger accesses) {
        FileObserver observer =
                new FileObserver(directory.getAbsolutePath(), FileObserver.ALL_EVENTS) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null
                                && (path.startsWith("calculation-")
                                        || path.startsWith("calculator-state")
                                        || path.equals("locked-calculations")))
                            accesses.incrementAndGet();
                    }
                };
        observer.startWatching();
        return observer;
    }

    private Bundle request(String key, Bundle accepted) {
        Bundle request = new Bundle();
        request.putString("key", key);
        request.putString("angle", "RAD");
        request.putString("notation", "SCIENTIFIC");
        request.putString("locale", "en-US");
        if (accepted != null) {
            request.putByteArray("input", accepted.getByteArray("state"));
            request.putString("inputId", accepted.getString("checkpoint"));
        }
        return request;
    }

    @Test
    public void snapshotsAndClipboardLargerThanBinderSurviveWorkerReplacementWithoutFiles()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicInteger accesses = new AtomicInteger();
        FileObserver saved = watch(context.getNoBackupFilesDir(), accesses);
        FileObserver cache = watch(context.getCacheDir(), accesses);
        try {
            RpnCalculator calculator = new RpnCalculator();
            calculator.setInputProfile(new LocaleNumbers(java.util.Locale.US).profile());
            List<String> literals = new ArrayList<>();
            int depth = 1100;
            char[] digits = new char[990];
            java.util.Arrays.fill(digits, '9');
            String prefix = new String(digits);
            for (int i = 0; i < depth; i++) {
                String literal = prefix + String.format(java.util.Locale.ROOT, "%04d", i);
                literals.add(literal);
                calculator.paste(literal);
                calculator.key("ENTER");
            }
            calculator.clearUndo();
            byte[] state = calculator.save();
            assertTrue(state.length > 1024 * 1024);
            Bundle seed = new Bundle();
            seed.putByteArray("state", state);
            Bundle initial =
                    worker.submit(() -> remote.evaluate(request("RESTORE", seed)))
                            .get(45, TimeUnit.SECONDS);
            assertEquals(depth, initial.getStringArrayList("rows").size());
            assertArrayEquals(state, initial.getByteArray("state"));
            String rows =
                    java.util.stream.IntStream.range(0, depth)
                            .mapToObj(Integer::toString)
                            .collect(java.util.stream.Collectors.joining(","));
            Bundle copied =
                    worker.submit(() -> remote.evaluate(request("COPY_ROWS:" + rows, initial)))
                            .get(30, TimeUnit.SECONDS);
            assertEquals(String.join("\n", literals), copied.getString("copyText"));
            remote.cancel();
            Bundle restored =
                    worker.submit(() -> remote.evaluate(request("RESTORE", initial)))
                            .get(30, TimeUnit.SECONDS);
            assertArrayEquals(state, restored.getByteArray("state"));
            assertEquals(initial.getStringArrayList("rows"), restored.getStringArrayList("rows"));
            assertEquals(
                    "Arithmetic and copy must never touch calculation files", 0, accesses.get());
        } finally {
            remote.close();
            worker.shutdownNow();
            saved.stopWatching();
            cache.stopWatching();
        }
    }

    @Test
    public void failedRequestReloadsAcceptedMemoryOnTheNextRequest() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            RpnCalculator calculator = new RpnCalculator();
            calculator.paste("42");
            calculator.key("ENTER");
            Bundle seed = new Bundle();
            seed.putByteArray("state", calculator.save());
            Bundle accepted =
                    worker.submit(() -> remote.evaluate(request("RESTORE", seed)))
                            .get(20, TimeUnit.SECONDS);
            Bundle invalid = request("RESTORE", accepted);
            invalid.putString("notation", "invalid");
            try {
                worker.submit(() -> remote.evaluate(invalid)).get(15, TimeUnit.SECONDS);
                fail("Invalid request unexpectedly succeeded");
            } catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof RemoteCalculator.UserFailure);
            }
            Bundle copied =
                    worker.submit(() -> remote.evaluate(request("COPY_ROWS:0", accepted)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals("42", copied.getString("copyText"));
        } finally {
            remote.close();
            worker.shutdownNow();
        }
    }

    @Test
    public void androidEightMemoryPipeTransfersLargeDataAndHandlesAbandonment() throws Exception {
        byte[] bytes = new byte[2 * 1024 * 1024];
        new java.util.Random(42).nextBytes(bytes);
        try (MemoryPayload payload = MemoryPayload.pipe(bytes)) {
            Bundle bundle = new Bundle();
            payload.put(bundle, "data");
            assertArrayEquals(bytes, MemoryPayload.take(bundle, "data"));
        }
        try (MemoryPayload abandoned = MemoryPayload.pipe(bytes)) {
            // Closing an unread pipe must release its blocked writer, without waiting for a reader.
        }
    }
}
