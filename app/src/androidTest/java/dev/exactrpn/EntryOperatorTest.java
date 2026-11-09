// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class EntryOperatorTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private void key(CalculatorSession session, String key) {
        instrumentation.runOnMainSync(() -> session.enqueue(Command.parse(key)));
        long until = SystemClock.elapsedRealtime() + 15000;
        while (session.busy && SystemClock.elapsedRealtime() < until) SystemClock.sleep(25);
        assertFalse("Calculation timed out", session.busy);
    }

    private void awaitUi(BooleanSupplier condition) {
        long until = SystemClock.elapsedRealtime() + 10000;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < until);
        fail("Timed out waiting for error notice");
    }

    @Test
    public void digitsAndCursorEditsStayLocalUntilCommit() throws Exception {
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL,
                        true);
        session.attach(instrumentation.getTargetContext());
        try {
            key(session, "RESTORE");
            CalculationSnapshot accepted = session.checkpoint;
            java.lang.reflect.Field remoteField =
                    CalculatorSession.class.getDeclaredField("remote");
            remoteField.setAccessible(true);
            RemoteCalculator remote = (RemoteCalculator) remoteField.get(session);
            instrumentation.runOnMainSync(remote::cancel);
            instrumentation.runOnMainSync(
                    () -> {
                        for (String input :
                                new String[] {
                                    "1", "2", ".", "3", "LEFT", "RIGHT", "BACK", "UNDO", "REDO",
                                    "UNDO"
                                }) {
                            session.enqueue(Command.parse(input));
                            assertFalse("Typing must not start worker activity", session.busy);
                            assertSame(
                                    "Typing must not serialize a new stack snapshot",
                                    accepted,
                                    session.checkpoint);
                        }
                    });
            assertEquals("12.3", session.display.entry);
            java.lang.reflect.Field connection =
                    RemoteCalculator.class.getDeclaredField("connection");
            connection.setAccessible(true);
            assertNull(
                    "Typing must not reconnect to the arithmetic service", connection.get(remote));
            key(session, "FORMAT");
            assertEquals("12.3", session.display.entry);
            assertEquals(
                    "Mode changes must not submit the draft",
                    "",
                    TestCalculator.load(session.checkpoint.bytes).entry());
            key(session, "UNDO");
            assertEquals("12.", session.display.entry);
            key(session, "REDO");
            assertEquals("12.3", session.display.entry);
            key(session, "ENTER");
            assertFalse(session.display.editing);
            assertEquals(Arrays.asList("12.3"), session.display.rows);
            key(session, "UNDO");
            assertEquals("12.3", session.display.entry);
            assertTrue(session.display.rows.isEmpty());
            key(session, "REDO");
            assertFalse(session.display.editing);
            assertEquals(Arrays.asList("12.3"), session.display.rows);
            key(session, "UNDO");
            key(session, "UNDO");
            assertFalse(session.display.editing);
            assertTrue(session.display.rows.isEmpty());
        } finally {
            session.close();
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
        }
    }

    @Test
    public void multilinePasteIsAtomicAcrossTheWorkerAndUndoRemovesTheBatch() throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL,
                        true);
        session.attach(context);
        try {
            key(session, "RESTORE");
            key(session, "PASTE_RAW:1\n2.5\n3");
            assertEquals(Arrays.asList("1", "2.5", "3"), session.display.rows);
            assertFalse(session.display.editing);
            byte[] before = session.checkpoint.bytes;
            key(session, "PASTE_RAW:4\nbad\n6");
            assertTrue(session.error.contains("line 2"));
            assertArrayEquals(before, session.checkpoint.bytes);
            key(session, "UNDO");
            assertTrue(session.display.rows.isEmpty());
        } finally {
            session.close();
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
        }
    }

    @Test
    public void onlyLifecycleCheckpointsAndClosePersistForegroundWork() throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        store.clear();
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL);
        session.attach(context);
        boolean closed = false;
        try {
            key(session, "RESTORE");
            key(session, "2");
            key(session, "ENTER");
            key(session, "√");
            SystemClock.sleep(1100); // Longer than the former foreground save delay.
            assertNull(
                    "Foreground arithmetic must not persist the calculation",
                    StoreTestSupport.checkpoint(store));
            key(session, "CLEAR");
            key(session, "1");
            key(session, "2");
            SystemClock.sleep(1100);
            assertNull(
                    "Foreground entry must not persist the calculation",
                    StoreTestSupport.checkpoint(store));
            session.background = true;
            session.saveForLifecycle();
            TestCalculator stopped =
                    TestCalculator.load(
                            Files.readAllBytes(
                                    new File(StoreTestSupport.checkpoint(store)).toPath()));
            assertEquals("12", stopped.entry());
            session.background = false;
            key(session, "3");
            session.close();
            closed = true;
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
            TestCalculator restored =
                    TestCalculator.load(
                            Files.readAllBytes(
                                    new File(StoreTestSupport.checkpoint(store)).toPath()));
            assertEquals("123", restored.entry());
            restored.key("UNDO");
            assertEquals("12", restored.entry());
            CalculatorSession resumed =
                    new CalculatorSession(
                            ValueFormatter.Notation.SCIENTIFIC,
                            RpnCalculator.AngleUnit.RAD,
                            NumberMode.DOT,
                            FractionMode.DECIMAL);
            resumed.attach(context);
            try {
                key(resumed, "RESTORE");
                assertEquals("123", resumed.display.entry);
                CalculationSnapshot arithmetic = resumed.checkpoint;
                key(resumed, "UNDO");
                assertEquals("12", resumed.display.entry);
                assertSame(arithmetic, resumed.checkpoint);
                key(resumed, "REDO");
                assertEquals("123", resumed.display.entry);
                assertSame(arithmetic, resumed.checkpoint);
            } finally {
                resumed.close();
                assertTrue(resumed.awaitClosed(15, TimeUnit.SECONDS));
            }
        } finally {
            if (!closed) {
                session.close();
                session.awaitClosed(15, TimeUnit.SECONDS);
            }
            store.clear();
        }
    }

    @Test
    public void stoppingSavesAcceptedMemoryWithoutWaitingForOrCancellingArithmetic()
            throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        store.clear();
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL);
        session.attach(context);
        try {
            key(session, "RESTORE");
            for (String key : new String[] {"π", "e", "+", "e", "π", "+", "−"}) key(session, key);
            CalculationSnapshot accepted = session.checkpoint;
            instrumentation.runOnMainSync(() -> session.enqueue(Command.parse("1/x")));
            SystemClock.sleep(300);
            assertTrue("Reciprocal of unknown zero should still be calculating", session.busy);
            long started = SystemClock.elapsedRealtime();
            instrumentation.runOnMainSync(
                    () -> {
                        session.background = true;
                        session.saveForLifecycle();
                    });
            long saveTime = SystemClock.elapsedRealtime() - started;
            System.out.println(
                    "Lifecycle saved "
                            + accepted.bytes.length
                            + " bytes in "
                            + saveTime
                            + " ms during blocked arithmetic");
            assertTrue("Stop must not wait for arithmetic", saveTime < 2000);
            assertTrue(session.busy);
            assertFalse(session.cancelRequested);
            assertSame(accepted, session.checkpoint);
            assertArrayEquals(accepted.bytes, store.load());
            session.cancel();
            awaitUi(() -> !session.busy);
            session.background = false;
            key(session, "CLEAR");
            key(session, "UNDO");
            assertEquals(1, session.display.rows.size());
        } finally {
            session.close();
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
            store.clear();
        }
    }

    @Test
    public void outstandingWorkUpdatesTheSaveAfterFinishingInBackground() throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        store.clear();
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL);
        session.attach(context);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            key(session, "RESTORE");
            for (String key : new String[] {"1", "ENTER", "2", "ENTER"}) key(session, key);
            java.lang.reflect.Field field = CalculatorSession.class.getDeclaredField("worker");
            field.setAccessible(true);
            ExecutorService calculations = (ExecutorService) field.get(session);
            calculations.submit(
                    () -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(
                    () -> {
                        session.enqueue(Command.parse("+"));
                        // An intermediate empty result must retain history for queued Undo.
                        session.enqueue(Command.parse("CLEAR"));
                        session.enqueue(Command.parse("UNDO"));
                        session.background = true;
                        session.saveForLifecycle();
                    });
            assertTrue(session.busy);
            assertEquals(2, TestCalculator.load(store.load()).values().size());
            release.countDown();
            calculations.submit(() -> {}).get(15, TimeUnit.SECONDS);
            assertEquals(Arrays.asList("3"), session.display.rows);
            TestCalculator saved = TestCalculator.load(store.load());
            assertEquals(1, saved.values().size());
            assertTrue(
                    saved.values()
                            .get(0)
                            .definitelyEquals(new com.android.calculator2.UnifiedReal(3)));
        } finally {
            release.countDown();
            session.close();
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
            store.clear();
        }
    }

    @Test
    public void styledExponentRemainsEditableAtOriginalCursorOffsets() throws Exception {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        View root = activity.getWindow().getDecorView();
        android.widget.EditText entry = root.findViewWithTag("number-entry");
        try {
            key(session, "PASTE_RAW:1.23");
            awaitUi(() -> entry.getText().toString().equals("1.23"));
            instrumentation.runOnMainSync(() -> root.findViewWithTag(".").performLongClick());
            awaitUi(() -> !session.busy && entry.getText().toString().equals("1.23e"));
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                1,
                                entry.getText()
                                        .getSpans(0, entry.length(), ExponentMarkerSpan.class)
                                        .length);
                        root.findViewWithTag("4").performClick();
                    });
            awaitUi(() -> !session.busy && entry.getText().toString().equals("1.23e4"));
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                1,
                                entry.getText()
                                        .getSpans(
                                                0,
                                                entry.length(),
                                                android.text.style.SuperscriptSpan.class)
                                        .length);
                        entry.setSelection(
                                5); // Immediately after the single underlying e character.
                    });
            awaitUi(() -> !session.busy && session.display.start == 5);
            instrumentation.runOnMainSync(() -> root.findViewWithTag("BACK").performClick());
            awaitUi(() -> !session.busy && entry.getText().toString().equals("1.234"));
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                0,
                                entry.getText()
                                        .getSpans(0, entry.length(), ExponentMarkerSpan.class)
                                        .length);
                        assertEquals(
                                0,
                                entry.getText()
                                        .getSpans(
                                                0,
                                                entry.length(),
                                                android.text.style.SuperscriptSpan.class)
                                        .length);
                        root.findViewWithTag("ENTER").performClick();
                    });
            awaitUi(() -> !session.busy && !session.display.editing);
            assertEquals(Arrays.asList("1.234"), session.display.rows);
        } finally {
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void olderFailureCannotRestoreItsNoticeOrDiscardTheNextKeypress() throws Exception {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        CountDownLatch release = new CountDownLatch(1);
        try {
            awaitUi(() -> !session.busy);
            java.lang.reflect.Field workerField =
                    CalculatorSession.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            ExecutorService worker = (ExecutorService) workerField.get(session);
            CountDownLatch paused = new CountDownLatch(1);
            worker.submit(
                    () -> {
                        paused.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    });
            assertTrue(paused.await(10, TimeUnit.SECONDS));
            View root = activity.getWindow().getDecorView();
            instrumentation.runOnMainSync(
                    () -> {
                        root.findViewWithTag("+").performClick();
                        root.findViewWithTag("1").performClick();
                    });
            // Complete the earlier failing operator only after the next key has dismissed it.
            release.countDown();
            awaitUi(() -> !session.busy && session.display.entry.equals("1"));
            assertEquals("", session.error);
            instrumentation.runOnMainSync(
                    () ->
                            assertEquals(
                                    View.GONE,
                                    root.findViewWithTag("calculation-notice").getVisibility()));
        } finally {
            release.countDown();
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void keysDismissErrorsImmediatelyAndNewFailuresStillAppear() throws Exception {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        View root = activity.getWindow().getDecorView();
        TextView notice = root.findViewWithTag("calculation-notice");
        try {
            key(session, "+");
            awaitUi(() -> notice.getVisibility() == View.VISIBLE);
            instrumentation.runOnMainSync(
                    () -> {
                        root.findViewWithTag("FUNCTIONS").performClick();
                        assertEquals(View.GONE, notice.getVisibility());
                        assertEquals("", session.error);
                    });
            key(session, "+");
            awaitUi(() -> notice.getVisibility() == View.VISIBLE);
            instrumentation.runOnMainSync(
                    () -> {
                        root.findViewWithTag("1").performClick();
                        assertEquals(View.GONE, notice.getVisibility());
                    });
            awaitUi(() -> !session.busy && session.display.entry.equals("1"));
            assertEquals("", session.error);
            instrumentation.runOnMainSync(() -> root.findViewWithTag("+").performClick());
            awaitUi(() -> !session.busy && notice.getVisibility() == View.VISIBLE);
            assertFalse(session.error.isEmpty());
            assertFalse(session.display.editing);
        } finally {
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void modeButtonsStayEnabledAndQueueChangesWithDigitEntry() throws Exception {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        View root = activity.getWindow().getDecorView();
        try {
            awaitUi(() -> !session.busy);
            instrumentation.runOnMainSync(
                    () -> {
                        root.findViewWithTag("1").performClick();
                        LinearLayout modes = root.findViewWithTag("mode-row");
                        for (int i = 0; i < 4; i++) {
                            assertTrue(
                                    "Mode button must stay enabled",
                                    modes.getChildAt(i).isEnabled());
                            modes.getChildAt(i).performClick();
                        }
                    });
            awaitUi(() -> !session.busy);
            assertEquals("1", session.display.entry);
            assertEquals(RpnCalculator.AngleUnit.DEG, session.display.angleUnit);
            assertEquals(ValueFormatter.Notation.ENGINEERING, session.display.notation);
            assertEquals(NumberMode.DOT, session.display.numberMode);
            assertEquals(FractionMode.MIXED, session.display.fractionMode);
        } finally {
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void repeatedUnderflowShowsTheNoticeAgainAfterItExpires() throws Exception {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        try {
            TextView notice =
                    activity.getWindow().getDecorView().findViewWithTag("calculation-notice");
            key(session, "1");
            key(session, "+");
            awaitUi(() -> notice.getVisibility() == View.VISIBLE);
            String message = session.error;
            long firstError = session.errorRevision;
            awaitUi(() -> notice.getVisibility() == View.GONE);
            // No intervening edit: this failure starts with the same value already on the stack.
            key(session, "+");
            assertEquals(message, session.error);
            assertTrue(session.errorRevision > firstError);
            awaitUi(() -> notice.getVisibility() == View.VISIBLE);
            instrumentation.runOnMainSync(() -> assertEquals(message, notice.getText().toString()));
            assertEquals(Arrays.asList("1"), session.display.rows);
            assertFalse(session.display.editing);
        } finally {
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void failedOperatorsPublishAndCheckpointCommittedEntryWithTheirError() throws Exception {
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL,
                        true);
        session.attach(instrumentation.getTargetContext());
        try {
            key(session, "1");
            key(session, "+");
            assertFalse(session.display.editing);
            assertEquals(Arrays.asList("1"), session.display.rows);
            assertEquals("Error: Enter another value first, then the operator", session.error);
            TestCalculator saved = TestCalculator.load(session.checkpoint.bytes);
            assertFalse(saved.isEditing());
            assertEquals("1", saved.values().get(0).bigIntegerValue().toString());
            key(session, "0");
            key(session, "÷");
            assertFalse(session.display.editing);
            assertEquals(Arrays.asList("1", "0"), session.display.rows);
            assertFalse(session.error.isEmpty());
            key(session, "UNDO");
            assertTrue(session.display.editing);
            assertEquals("0", session.display.entry);
            assertEquals(Arrays.asList("1"), session.display.rows);
            key(session, "CE");
            key(session, "PASTE_RAW:1,234");
            key(session, "+");
            assertTrue(session.display.editing);
            assertEquals("1,234", session.display.entry);
            assertEquals(Arrays.asList("1"), session.display.rows);
            assertFalse(session.error.isEmpty());
        } finally {
            session.close();
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
        }
    }
}
