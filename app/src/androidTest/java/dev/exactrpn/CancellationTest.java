// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class CancellationTest {
    private CalculationRequest request(String key, byte[] input) {
        CalculationRequest request = new CalculationRequest(key);
        request.input = input;
        request.angle = RpnCalculator.AngleUnit.RAD;
        request.notation = ValueFormatter.Notation.SCIENTIFIC;
        request.locale = "en-US";
        return request;
    }

    @Test(timeout = 90000)
    public void idleWorkersCanBeOpenedAndClosedRepeatedly() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < 4; i++) {
                RemoteCalculator remote = new RemoteCalculator(context);
                try {
                    Bundle result =
                            worker.submit(() -> remote.evaluate(request("RESTORE", null)))
                                    .get(20, TimeUnit.SECONDS);
                    assertTrue(result.getStringArrayList("rows").isEmpty());
                    assertTrue(result.getByteArray("state").length > 0);
                } finally {
                    remote.close();
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            }
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void cancelStopsWorkerAndPreservesCheckpoint() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        File seed = File.createTempFile("cancel-test-", ".state", context.getNoBackupFilesDir());
        try {
            // Equal expressions whose equality the constructive-real engine cannot establish.
            RpnCalculator calculator = new RpnCalculator();
            for (String key : new String[] {"π", "e", "+", "e", "π", "+", "−"}) calculator.key(key);
            assertFalse(calculator.values().get(0).definitelyZero());
            Files.write(seed.toPath(), calculator.save());
            store.save(Files.readAllBytes(seed.toPath()));
            // Cold process startup/restoration is separate from the strict cancellation budget.
            long restoring = SystemClock.elapsedRealtime();
            Bundle initial =
                    worker.submit(() -> remote.evaluate(request("RESTORE", store.load())))
                            .get(30, TimeUnit.SECONDS);
            System.out.println(
                    "Initial checkpoint restore took "
                            + (SystemClock.elapsedRealtime() - restoring)
                            + " ms");
            byte[] accepted = initial.getByteArray("state");
            store.save(accepted);
            byte[] committed = Files.readAllBytes(new File(store.checkpoint()).toPath());
            Future<Bundle> stuck = worker.submit(() -> remote.evaluate(request("1/x", accepted)));
            SystemClock.sleep(300);
            assertFalse("Reciprocal of unknown zero should still be calculating", stuck.isDone());
            long started = SystemClock.elapsedRealtime();
            remote.cancel();
            try {
                stuck.get(2, TimeUnit.SECONDS);
                fail("Cancelled request returned a result");
            } catch (ExecutionException | CancellationException expected) {
            }
            assertTrue(
                    "Cancel must release the caller promptly",
                    SystemClock.elapsedRealtime() - started < 2000);
            assertArrayEquals(
                    "Cancelled work cannot replace committed storage",
                    committed,
                    Files.readAllBytes(new File(store.checkpoint()).toPath()));
            Bundle cleared =
                    worker.submit(() -> remote.evaluate(request("CLEAR", accepted)))
                            .get(15, TimeUnit.SECONDS);
            assertTrue(
                    "A fresh worker must accept new operations",
                    cleared.getStringArrayList("rows").isEmpty());
            Bundle undone =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request("UNDO", cleared.getByteArray("state"))))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(
                    "Cancellation preserves the old stack and undo",
                    1,
                    undone.getStringArrayList("rows").size());
        } finally {
            remote.close();
            worker.shutdownNow();
            store.clear();
            seed.delete();
        }
    }
}
