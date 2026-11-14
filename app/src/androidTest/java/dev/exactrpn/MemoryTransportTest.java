// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import android.content.Context;
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

    private CalculationRequest request(String key, DisplayResult accepted) {
        return request(Command.key(key), accepted);
    }

    private CalculationRequest request(Command key, DisplayResult accepted) {
        CalculationRequest request = new CalculationRequest(key);
        request.angle = RpnCalculator.AngleUnit.RAD;
        if (key.kind == Command.Kind.READ_VALUES) request.numbers = NumericRequest.PRECISE;
        if (accepted != null) {
            request.input = accepted.state;
            request.inputId = accepted.checkpoint;
        }
        return request;
    }

    @Test
    public void displayPacketSharesRowsAndBudgetsEntryHistoryWithoutTruncatingValues()
            throws Exception {
        DisplayResult result = new DisplayResult();
        NumericValue number =
                NumericEvaluator.read(
                        RpnCalculator.parse("123456789012345678901234567890"),
                        NumericRequest.DEFAULT);
        result.values = new java.util.ArrayList<>(java.util.Collections.nCopies(3800, number));
        result.stackId = "stack";
        result.checkpoint = "checkpoint";
        result.state = new TestCalculator().save();
        EntryEditor editor = new EntryEditor();
        for (int i = 0; i < 1000; i++) editor.key("9");
        byte[] packet = DisplayResult.encode(result, editor);
        assertTrue(packet.length <= CalculationLimits.TRANSFER_BYTES);
        DisplayResult decoded = DisplayResult.decode(packet);
        assertEquals(result.values.size(), decoded.values.size());
        assertEquals(number.rational.numerator, decoded.values.get(3799).rational.numerator);
        EntryEditor restored = EntryEditor.load(decoded.entryState);
        assertEquals(String.join("", java.util.Collections.nCopies(1000, "9")), restored.entry());
        assertTrue(restored.undoCount() > 0 && restored.undoCount() < 100);
        Display full =
                new Display(
                        decoded,
                        new EntryEditor(),
                        new Display(
                                ValueFormatter.Notation.ENGINEERING,
                                RpnCalculator.AngleUnit.RAD,
                                NumberMode.UNGROUPED,
                                FractionMode.IMPROPER,
                                RadixMode.DECIMAL));
        Display editing = new Display(full, restored);
        assertEquals("123456789012345678901234567890", full.rows.get(3799));
        assertEquals("123.456789012e+27", editing.rows.get(3798));
        assertEquals("123.456789012e+27", editing.rows.get(3799));
        assertThrows(UnsupportedOperationException.class, () -> editing.rows.set(0, "changed"));
        assertThrows(
                java.io.IOException.class,
                () -> DisplayResult.decode(java.util.Arrays.copyOf(packet, packet.length - 1)));
        DisplayResult copy = new DisplayResult();
        copy.readOnly = true;
        copy.values.add(
                NumericEvaluator.read(
                        RpnCalculator.parse("7").divide(RpnCalculator.parse("3")),
                        NumericRequest.PRECISE));
        assertEquals(
                "7/3",
                ValueFormatter.clipboard(
                        DisplayResult.decode(DisplayResult.encode(copy, null)).values.get(0),
                        ValueFormatter.Notation.SCIENTIFIC,
                        FractionMode.IMPROPER));
    }

    @Test
    public void optionalExactDataCannotCrowdOutCalculationState() throws Exception {
        DisplayResult result = new DisplayResult();
        result.state = new byte[230000];
        java.util.Arrays.fill(result.state, (byte) 7);
        NumericValue value =
                NumericEvaluator.read(
                        RpnCalculator.parse("1e399").divide(RpnCalculator.parse("7")),
                        NumericRequest.DEFAULT);
        result.values = new java.util.ArrayList<>(java.util.Collections.nCopies(400, value));
        byte[] packet = DisplayResult.encode(result, new EntryEditor());
        assertTrue(packet.length <= CalculationLimits.TRANSFER_BYTES);
        DisplayResult decoded = DisplayResult.decode(packet);
        assertArrayEquals(result.state, decoded.state);
        assertEquals(400, decoded.values.size());
        assertNull(decoded.values.get(0).rational);
        assertNotNull(decoded.values.get(399).rational);
        assertEquals(value.num, decoded.values.get(0).num);
        assertEquals(value.scale, decoded.values.get(0).scale);
        result.exactRequired = true;
        assertThrows(
                IllegalArgumentException.class,
                () -> DisplayResult.encode(result, new EntryEditor()));
        assertNotNull(
                "Packing must never change cached numerical data", result.values.get(0).rational);
    }

    @Test
    public void oversizedCopiesAndRequestsPreserveTheAcceptedCalculation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            DisplayResult accepted = null;
            for (String key : new String[] {"RESTORE", "10ˣ", "ENTER", "ENTER", "ENTER"}) {
                CalculationRequest next = request(key, accepted);
                if (key.equals("10ˣ")) {
                    EntryEditor draft = new EntryEditor();
                    draft.pasteText("65536", NumberText.COMPUTER);
                    next.entryDraft = draft.entryForCommit().save();
                }
                accepted = worker.submit(() -> remote.evaluate(next)).get(30, TimeUnit.SECONDS);
            }
            for (int i = 0; i < 12; i++) {
                CalculationRequest duplicate = request("ENTER", accepted);
                accepted =
                        worker.submit(() -> remote.evaluate(duplicate)).get(15, TimeUnit.SECONDS);
            }
            CalculationRequest copy =
                    request(
                            Command.rows(
                                    Command.Kind.READ_VALUES,
                                    java.util.stream.IntStream.range(0, 16).toArray()),
                            accepted);
            expectTooLarge(worker, remote, copy);
            CalculationRequest oversized = request("RESTORE", accepted);
            oversized.entryDraft = new byte[CalculationLimits.TRANSFER_BYTES];
            expectTooLarge(worker, remote, oversized);
            CalculationRequest single =
                    request(Command.rows(Command.Kind.READ_VALUES, 0), accepted);
            single.input = null;
            DisplayResult copied =
                    worker.submit(() -> remote.evaluate(single)).get(30, TimeUnit.SECONDS);
            assertEquals(65537, ResultTestSupport.copy(copied).length());
            assertTrue(ResultTestSupport.copy(copied).startsWith("1"));
            remote.cancel();
            CalculationRequest restore = request("RESTORE", accepted);
            DisplayResult restored =
                    worker.submit(() -> remote.evaluate(restore)).get(30, TimeUnit.SECONDS);
            assertEquals(ResultTestSupport.rows(accepted), ResultTestSupport.rows(restored));
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
            TestCalculator calculator = new TestCalculator();
            CalculatorTestSupport.paste(calculator, "42");
            calculator.key("ENTER");
            DisplayResult seed = new DisplayResult();
            seed.state = calculator.save();
            DisplayResult accepted =
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
            DisplayResult copied =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            Command.rows(
                                                                    Command.Kind.READ_VALUES, 0),
                                                            accepted)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals("42", ResultTestSupport.copy(copied));
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
            TestCalculator calculator = new TestCalculator();
            CalculatorTestSupport.paste(calculator, "45");
            calculator.key("ENTER");
            DisplayResult seed = new DisplayResult();
            seed.state = calculator.save();
            DisplayResult accepted =
                    worker.submit(() -> remote.evaluate(request("RESTORE", seed)))
                            .get(20, TimeUnit.SECONDS);
            for (Command badCopy :
                    new Command[] {
                        Command.rows(Command.Kind.READ_VALUES, 999),
                        Command.rows(Command.Kind.READ_VALUES, -1)
                    }) {
                try {
                    worker.submit(() -> remote.evaluate(request(badCopy, accepted)))
                            .get(15, TimeUnit.SECONDS);
                    fail("Missing row unexpectedly copied");
                } catch (java.util.concurrent.ExecutionException expected) {
                    assertTrue(expected.getCause() instanceof RemoteCalculator.UserFailure);
                }
                var field = RemoteCalculator.class.getDeclaredField("remoteSnapshotId");
                field.setAccessible(true);
                assertEquals(accepted.checkpoint, field.get(remote));
                CalculationRequest next =
                        request(Command.rows(Command.Kind.READ_VALUES, 0), accepted);
                next.input = null;
                DisplayResult copied =
                        worker.submit(() -> remote.evaluate(next)).get(15, TimeUnit.SECONDS);
                assertEquals("45", ResultTestSupport.copy(copied));
            }
        } finally {
            remote.close();
            worker.shutdownNow();
        }
    }

    @Test
    public void workerExplicitlyPreservesNoOpDraftsAndRestoresHistoryReplies() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            DisplayResult accepted =
                    worker.submit(() -> remote.evaluate(request("RESTORE", null)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(DisplayResult.EditorDisposition.RESTORE, accepted.editorDisposition);
            remote.cancel(); // A fresh worker must recognize a no-op without relying on stack IDs.
            CalculationRequest noop = request("DROP", accepted);
            EntryEditor local = new EntryEditor();
            local.key("1");
            local.key("UNDO");
            noop.entryDraft = local.entryForCommit().save();
            accepted = worker.submit(() -> remote.evaluate(noop)).get(15, TimeUnit.SECONDS);
            assertEquals(DisplayResult.EditorDisposition.KEEP, accepted.editorDisposition);
            local.pasteClipboard("1,234", NumberText.COMPUTER);
            assertThrows(IllegalArgumentException.class, local::prepared);
            CalculationRequest commit = request("+", accepted);
            local.pasteText("7", NumberText.COMPUTER);
            commit.entryDraft = local.entryForCommit().save();
            accepted = worker.submit(() -> remote.evaluate(commit)).get(15, TimeUnit.SECONDS);
            assertEquals(DisplayResult.EditorDisposition.RESTORE, accepted.editorDisposition);
            assertEquals(java.util.Arrays.asList("7"), ResultTestSupport.rows(accepted));
            assertNotNull(accepted.operationError);
            CalculationRequest undo = request("UNDO", accepted);
            accepted = worker.submit(() -> remote.evaluate(undo)).get(15, TimeUnit.SECONDS);
            assertEquals(DisplayResult.EditorDisposition.RESTORE, accepted.editorDisposition);
            assertEquals("7", EntryEditor.load(accepted.entryState).entry());
        } finally {
            remote.close();
            worker.shutdownNow();
        }
    }

    @Test
    public void everyCommandPayloadRoundTripsWithoutParsingText() throws Exception {
        for (Command command :
                new Command[] {
                    Command.key("SELECT_HOME"),
                    Command.pushValues(
                            new RationalValue[] {
                                new RationalValue(
                                        java.math.BigInteger.valueOf(1234),
                                        java.math.BigInteger.ONE)
                            }),
                    Command.holdDigit("0"),
                    Command.rows(Command.Kind.DROP_ROWS, 3, 1, 3),
                    Command.rows(Command.Kind.PICK_ROWS, 0),
                    Command.rows(Command.Kind.SUM_ROWS, 0, 2),
                    Command.rows(Command.Kind.PRODUCT_ROWS, 0, 2),
                    Command.rows(Command.Kind.READ_VALUES, 1),
                    Command.unaryRows(Operation.SQRT, 0, 2)
                }) {
            CalculationRequest request = new CalculationRequest(command);
            Command decoded =
                    CalculationRequest.decode(request.encode("session", null, false)).command;
            assertEquals(command.kind, decoded.kind);
            assertEquals(command.wire, decoded.wire);
            assertEquals(command.text, decoded.text);
            assertEquals(command.start, decoded.start);
            assertEquals(command.end, decoded.end);
            assertEquals(command.raw, decoded.raw);
            assertEquals(command.explicitDecimal, decoded.explicitDecimal);
            assertEquals(command.operation, decoded.operation);
            assertArrayEquals(command.rows(), decoded.rows());
        }
        byte[] encoded =
                new CalculationRequest(Command.key("ENTER")).encode("session", null, false);
        encoded[4] = (byte) 255;
        assertThrows(java.io.IOException.class, () -> CalculationRequest.decode(encoded));
    }

    @Test
    public void requestCodecRoundTripsNumericalBoundsAndRejectsMalformedData() throws Exception {
        CalculationRequest request =
                new CalculationRequest(Command.rows(Command.Kind.READ_VALUES, 0));

        request.angle = RpnCalculator.AngleUnit.GRAD;

        request.numbers = new NumericRequest(java.math.BigInteger.valueOf(2000), 12, 8192, true);
        request.entryBranch = true;
        request.entryDraft = new byte[] {1, 2, 3};
        byte[] encoded = request.encode("session", "checkpoint", true);
        CalculationRequest decoded = CalculationRequest.decode(encoded);
        assertEquals(request.command.kind, decoded.command.kind);
        assertEquals(request.command.text, decoded.command.text);
        assertEquals(request.angle, decoded.angle);
        assertEquals(request.numbers.limit, decoded.numbers.limit);
        assertEquals(request.numbers.base, decoded.numbers.base);
        assertEquals(request.numbers.exactBytes, decoded.numbers.exactBytes);
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
        java.nio.ByteBuffer.wrap(encoded).putInt(encoded.length - 9, 1);
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
