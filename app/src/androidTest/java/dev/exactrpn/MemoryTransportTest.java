// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Parcel;

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
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 27);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicInteger accesses = new AtomicInteger();
        FileObserver saved = watch(context.getNoBackupFilesDir(), accesses);
        FileObserver cache = watch(context.getCacheDir(), accesses);
        try {
            RpnCalculator calculator = new RpnCalculator();
            List<String> literals = new ArrayList<>();
            int depth = 1100;
            char[] digits = new char[990];
            java.util.Arrays.fill(digits, '9');
            String prefix = new String(digits);
            for (int i = 0; i < depth; i++) {
                String literal = prefix + String.format(java.util.Locale.ROOT, "%04d", i);
                literals.add(literal);
                CalculatorTestSupport.paste(calculator, literal);
                calculator.key("ENTER");
            }
            // Canonical entry fixtures use computer mode; match this request's locale before
            // saving.
            calculator.setInputProfile(new LocaleNumbers(java.util.Locale.US).profile());
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
            CalculatorTestSupport.paste(calculator, "42");
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
    public void failedCopyPreservesBothCachesWithoutResendingTheSnapshot() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            RpnCalculator calculator = new RpnCalculator();
            CalculatorTestSupport.paste(calculator, "45");
            calculator.key("ENTER");
            Bundle seed = new Bundle();
            seed.putByteArray("state", calculator.save());
            Bundle accepted =
                    worker.submit(() -> remote.evaluate(request("RESTORE", seed)))
                            .get(20, TimeUnit.SECONDS);
            for (String badCopy : new String[] {"COPY_ROWS:999", "COPY_ROWS:bad"}) {
                try {
                    worker.submit(() -> remote.evaluate(request(badCopy, accepted)))
                            .get(15, TimeUnit.SECONDS);
                    fail("Missing row unexpectedly copied");
                } catch (java.util.concurrent.ExecutionException expected) {
                    assertTrue(expected.getCause() instanceof RemoteCalculator.UserFailure);
                }
                var field = RemoteCalculator.class.getDeclaredField("remoteSnapshotId");
                field.setAccessible(true);
                assertEquals(accepted.getString("checkpoint"), field.get(remote));
                Bundle next = request("COPY_ROWS:0", accepted);
                next.remove("input");
                Bundle copied =
                        worker.submit(() -> remote.evaluate(next)).get(15, TimeUnit.SECONDS);
                assertEquals("45", copied.getString("copyText"));
            }
        } finally {
            remote.close();
            worker.shutdownNow();
        }
    }

    @Test
    public void smallPayloadsUseBinderAndLargeOnesRequireSharedMemory() throws Exception {
        for (int length :
                new int[] {0, MemoryPayload.INLINE_LIMIT - 1, MemoryPayload.INLINE_LIMIT}) {
            byte[] bytes = new byte[length];
            new java.util.Random(42).nextBytes(bytes);
            if (length >= MemoryPayload.INLINE_LIMIT && Build.VERSION.SDK_INT < 27) {
                try {
                    MemoryPayload.create(bytes);
                    fail("Android 8.0 must reject transfers requiring shared memory");
                } catch (java.io.IOException expected) {
                    assertTrue(expected.getMessage().contains("Android 8.1"));
                }
                continue;
            }
            try (MemoryPayload payload = MemoryPayload.create(bytes)) {
                Bundle sent = new Bundle();
                payload.put(sent, "data");
                assertEquals(
                        length < MemoryPayload.INLINE_LIMIT, sent.get("data") instanceof byte[]);
                Parcel parcel = Parcel.obtain();
                try {
                    parcel.writeBundle(sent);
                    parcel.setDataPosition(0);
                    Bundle received = parcel.readBundle(MemoryPayload.class.getClassLoader());
                    assertArrayEquals(bytes, MemoryPayload.take(received, "data"));
                    assertFalse(received.containsKey("data"));
                } finally {
                    parcel.recycle();
                }
                MemoryPayload.discard(sent, "data");
                assertTrue(sent.isEmpty());
            }
        }
    }
}
