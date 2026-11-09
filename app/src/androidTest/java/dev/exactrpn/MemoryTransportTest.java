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

    private CalculationRequest request(String key, Bundle accepted) {
        CalculationRequest request = new CalculationRequest(key);
        request.angle = RpnCalculator.AngleUnit.RAD;
        request.notation = ValueFormatter.Notation.SCIENTIFIC;
        request.locale = "en-US";
        if (accepted != null) {
            request.input = accepted.getByteArray("state");
            request.inputId = accepted.getString("checkpoint");
        }
        return request;
    }

    @Test
    public void displayPacketSharesRowsAndBudgetsEntryHistoryWithoutTruncatingValues()
            throws Exception {
        Bundle result = new Bundle();
        String row = String.join("", java.util.Collections.nCopies(9, "1234567890")) + "/7";
        result.putStringArrayList(
                "rows", new java.util.ArrayList<>(java.util.Collections.nCopies(2000, row)));
        result.putString("compactTop", "1.76e+88");
        result.putString("stackId", "stack");
        result.putString("checkpoint", "checkpoint");
        result.putString("angle", "RAD");
        result.putString("notation", "SCIENTIFIC");
        result.putString("entry", "");
        result.putString("problem", "");
        result.putByteArray("state", new RpnCalculator().save());
        EntryEditor editor = new EntryEditor();
        for (int i = 0; i < 1000; i++) editor.key("9");
        byte[] packet = DisplayResult.encode(result, editor);
        assertTrue(packet.length <= CalculationLimits.TRANSFER_BYTES);
        Bundle decoded = DisplayResult.decode(packet);
        assertFalse(decoded.containsKey("compactRows"));
        assertEquals(result.getStringArrayList("rows"), decoded.getStringArrayList("rows"));
        EntryEditor restored = EntryEditor.load(decoded.getByteArray("entryState"));
        assertEquals(String.join("", java.util.Collections.nCopies(1000, "9")), restored.entry());
        assertTrue(restored.undoCount() > 0 && restored.undoCount() < 100);
        Display full = new Display(decoded);
        Display editing = new Display(full, restored);
        assertEquals(row, full.rows.get(1999));
        assertEquals(row, editing.rows.get(1998));
        assertEquals("1.76e+88", editing.rows.get(1999));
        assertThrows(UnsupportedOperationException.class, () -> editing.rows.set(0, "changed"));
        assertThrows(
                java.io.IOException.class,
                () -> DisplayResult.decode(java.util.Arrays.copyOf(packet, packet.length - 1)));
        Bundle copy = new Bundle();
        copy.putString("copyText", "7/3");
        assertEquals(
                "7/3",
                DisplayResult.decode(DisplayResult.encode(copy, null)).getString("copyText"));
    }

    @Test
    public void oversizedCopiesAndRequestsPreserveTheAcceptedCalculation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Bundle accepted = null;
            for (String key :
                    new String[] {"RESTORE", "PASTE_RAW:65536", "10ˣ", "ENTER", "ENTER", "ENTER"}) {
                CalculationRequest next = request(key, accepted);
                accepted = worker.submit(() -> remote.evaluate(next)).get(30, TimeUnit.SECONDS);
            }
            CalculationRequest copy = request("COPY_ROWS:0,1,2,3", accepted);
            expectTooLarge(worker, remote, copy);
            CalculationRequest oversized = request("RESTORE", accepted);
            oversized.entryDraft = new byte[CalculationLimits.TRANSFER_BYTES];
            expectTooLarge(worker, remote, oversized);
            CalculationRequest single = request("COPY_ROWS:0", accepted);
            single.input = null;
            Bundle copied = worker.submit(() -> remote.evaluate(single)).get(30, TimeUnit.SECONDS);
            assertEquals(65537, copied.getString("copyText").length());
            assertTrue(copied.getString("copyText").startsWith("1"));
            remote.cancel();
            CalculationRequest restore = request("RESTORE", accepted);
            Bundle restored =
                    worker.submit(() -> remote.evaluate(restore)).get(30, TimeUnit.SECONDS);
            assertEquals(accepted.getStringArrayList("rows"), restored.getStringArrayList("rows"));
        } finally {
            remote.close();
            worker.shutdownNow();
        }
    }

    private void expectTooLarge(
            ExecutorService worker, RemoteCalculator remote, CalculationRequest request)
            throws Exception {
        try {
            worker.submit(() -> remote.evaluate(request)).get(30, TimeUnit.SECONDS);
            fail("Oversized transfer succeeded");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause() instanceof CalculationError);
            assertEquals(
                    CalculationError.Code.TRANSFER_TOO_LARGE,
                    ((CalculationError) expected.getCause()).code());
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
            CalculationRequest invalid = request("RESTORE", accepted);
            invalid.entryDraft = new byte[] {0};
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
                CalculationRequest next = request("COPY_ROWS:0", accepted);
                next.input = null;
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
    public void requestCodecRoundTripsModesAndRejectsMalformedData() throws Exception {
        CalculationRequest request = new CalculationRequest("PASTE_RAW:1,234\n5.6");
        request.locale = "de-DE";
        request.angle = RpnCalculator.AngleUnit.GRAD;
        request.notation = ValueFormatter.Notation.ENGINEERING;
        request.numberMode = NumberMode.UNGROUPED;
        request.fractionMode = FractionMode.IMPROPER;
        request.entryBranch = true;
        request.entryDraft = new byte[] {1, 2, 3};
        byte[] encoded = request.encode("session", "checkpoint", true);
        CalculationRequest decoded = CalculationRequest.decode(encoded);
        assertEquals(request.key, decoded.key);
        assertEquals(request.locale, decoded.locale);
        assertEquals(request.angle, decoded.angle);
        assertEquals(request.notation, decoded.notation);
        assertEquals(request.numberMode, decoded.numberMode);
        assertEquals(request.fractionMode, decoded.fractionMode);
        assertArrayEquals(request.entryDraft, decoded.entryDraft);
        assertTrue(decoded.entryBranch);
        assertEquals("session", decoded.sessionId);
        assertEquals("checkpoint", decoded.inputId);
        for (byte[] invalid :
                new byte[][] {
                    java.util.Arrays.copyOf(encoded, encoded.length - 1),
                    java.util.Arrays.copyOf(encoded, encoded.length + 1)
                }) {
            try {
                CalculationRequest.decode(invalid);
                fail("Malformed request accepted");
            } catch (java.io.IOException expected) {
            }
        }
        encoded[encoded.length - 1] = (byte) 255;
        try {
            CalculationRequest.decode(encoded);
            fail("Invalid mode accepted");
        } catch (java.io.IOException expected) {
        }
        assertEquals(
                CalculationLimits.TRANSFER_BYTES,
                Transfer.require(new byte[CalculationLimits.TRANSFER_BYTES]).length);
        try {
            Transfer.require(new byte[CalculationLimits.TRANSFER_BYTES + 1]);
            fail("Oversized payload accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    CalculationError.Code.TRANSFER_TOO_LARGE, ((CalculationError) expected).code());
        }
    }
}
