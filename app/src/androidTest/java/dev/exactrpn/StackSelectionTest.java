// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class StackSelectionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private interface Condition {
        boolean ready();
    }

    private void await(Condition condition) {
        long until = SystemClock.elapsedRealtime() + 15000;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> ready.set(condition.ready()));
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < until);
        fail("Timed out waiting for stack UI at " + new Throwable().getStackTrace()[1]);
    }

    static CalculatorSession session(MainActivity activity) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("session");
            field.setAccessible(true);
            return (CalculatorSession) field.get(activity);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    static void finish(MainActivity activity) throws InterruptedException {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        CalculatorSession session = session(activity);
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
        assertTrue(
                "Activity worker must finish before the next test",
                session.awaitClosed(10, TimeUnit.SECONDS));
    }

    private View tagged(MainActivity activity, String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    private MainActivity open() {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> !session(activity).busy);
        return activity;
    }

    private void key(MainActivity activity, String key) {
        instrumentation.runOnMainSync(() -> tagged(activity, key).performClick());
        await(() -> !session(activity).busy);
    }

    private void seed(MainActivity activity) {
        for (String key : new String[] {"1", "ENTER", "2", "ENTER", "3", "ENTER"})
            key(activity, key);
        await(() -> tagged(activity, "stack-label:2") != null);
    }

    private void selectEnds(MainActivity activity) {
        instrumentation.runOnMainSync(() -> tagged(activity, "stack-label:0").performClick());
        instrumentation.runOnMainSync(() -> tagged(activity, "stack-row:2").performClick());
    }

    @Test
    public void labelsEnterSelectionAndUndoTogglesAllAndNone() throws Exception {
        MainActivity activity = open();
        CalculatorSession session = session(activity);
        try {
            seed(activity);
            instrumentation.runOnMainSync(
                    () -> {
                        tagged(activity, "stack-label:1").performClick();
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(1)),
                                session.selectedRows);
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:1")).isChecked());
                        tagged(activity, "stack-value:0").performClick();
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:0")).isChecked());
                        tagged(activity, "stack-row:1").performClick();
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0)),
                                session.selectedRows);
                        assertFalse(tagged(activity, "stack-label:2").isLongClickable());
                        tagged(activity, "stack-label:2").performLongClick();
                        assertEquals(1, session.selectedRows.size());
                        assertTrue(
                                ((TextView) tagged(activity, "calculation-status"))
                                        .getText()
                                        .toString()
                                        .contains("SELECT"));
                        assertNotNull(tagged(activity, "UNDO").getForeground());
                        assertEquals(
                                "Select all stack rows",
                                tagged(activity, "UNDO").getContentDescription().toString());
                        tagged(activity, "UNDO").performClick();
                        assertEquals(3, session.selectedRows.size());
                        assertNotNull(tagged(activity, "UNDO").getForeground());
                        assertEquals(
                                "Clear selection",
                                tagged(activity, "UNDO").getContentDescription().toString());
                        tagged(activity, "UNDO").performClick();
                        assertTrue(session.selectedRows.isEmpty());
                        assertNull(tagged(activity, "stack-checkbox:0"));
                        assertEquals("+", ((Button) tagged(activity, "+")).getText().toString());
                        assertEquals("⎌", ((Button) tagged(activity, "UNDO")).getText().toString());
                        assertNull(tagged(activity, "CLEAR").getForeground());
                        tagged(activity, "stack-label:1").performClick();
                        assertEquals(1, session.selectedRows.size());
                        activity.onBackPressed();
                        assertTrue(session.selectedRows.isEmpty());
                        assertFalse(activity.isFinishing());
                    });
        } finally {
            finish(activity);
        }
    }

    @Test
    public void selectionKeysCopyPickSumProductAndDropWithoutConsumingOriginals() throws Exception {
        MainActivity activity = open();
        CalculatorSession session = session(activity);
        try {
            seed(activity);
            selectEnds(activity);
            instrumentation.runOnMainSync(
                    () -> {
                        Button copy = (Button) tagged(activity, "CLEAR");
                        assertEquals("", copy.getText().toString());
                        assertNotNull(copy.getForeground());
                        assertEquals(android.view.Gravity.CENTER, copy.getForegroundGravity());
                        assertEquals(
                                "Pick", ((Button) tagged(activity, "SWAP")).getText().toString());
                        assertEquals("Σ", ((Button) tagged(activity, "+")).getText().toString());
                        assertEquals("Π", ((Button) tagged(activity, "×")).getText().toString());
                    });
            key(activity, "CLEAR");
            ClipboardManager clipboard =
                    (ClipboardManager)
                            instrumentation
                                    .getTargetContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "1\n3"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            key(activity, "SWAP");
            await(() -> tagged(activity, "stack-value:4") != null);
            assertEquals(java.util.Arrays.asList("1", "2", "3", "1", "3"), session.display.rows);
            key(activity, "+");
            key(activity, "×");
            await(() -> tagged(activity, "stack-value:6") != null);
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                java.util.Arrays.asList("1", "2", "3", "1", "3", "4", "3"),
                                session.display.rows);
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0, 2)),
                                session.selectedRows);
                        assertFalse(((CheckBox) tagged(activity, "stack-checkbox:6")).isChecked());
                    });
            RpnCalculator restored = RpnCalculator.load(session.checkpoint.bytes);
            restored.key("UNDO");
            assertEquals(6, restored.values().size());
            key(activity, "BACK");
            await(
                    () ->
                            tagged(activity, "stack-value:6") == null
                                    && tagged(activity, "stack-checkbox:0") == null);
            assertEquals(java.util.Arrays.asList("2", "1", "3", "4", "3"), session.display.rows);
            assertTrue(session.selectedRows.isEmpty());
            key(activity, "UNDO");
            assertEquals(7, session.display.rows.size());
        } finally {
            finish(activity);
        }
    }

    @Test
    public void entryModeCannotEnterSelectionAndOldLongPressActionsStayDisabledWhileSelecting()
            throws Exception {
        MainActivity activity = open();
        CalculatorSession session = session(activity);
        try {
            seed(activity);
            key(activity, "7");
            await(
                    () ->
                            session.display.editing
                                    && !tagged(activity, "stack-label:0").isClickable());
            instrumentation.runOnMainSync(
                    () -> {
                        tagged(activity, "stack-label:0").performClick();
                        tagged(activity, "stack-label:0").performLongClick();
                        assertTrue(session.selectedRows.isEmpty());
                        assertNull(tagged(activity, "stack-checkbox:0"));
                        assertEquals(
                                "CE", ((Button) tagged(activity, "CLEAR")).getText().toString());
                        assertNull(tagged(activity, "CLEAR").getForeground());
                        assertEquals("⌫", ((Button) tagged(activity, "BACK")).getText().toString());
                    });
            key(activity, "ENTER");
            await(() -> tagged(activity, "stack-label:0").isClickable());
            selectEnds(activity);
            instrumentation.runOnMainSync(
                    () -> {
                        assertFalse(tagged(activity, "BACK").isLongClickable());
                        assertFalse(tagged(activity, "SWAP").isLongClickable());
                        assertFalse(tagged(activity, "UNDO").isLongClickable());
                        tagged(activity, "BACK").performLongClick();
                        tagged(activity, "SWAP").performLongClick();
                        tagged(activity, "UNDO").performLongClick();
                        assertEquals(2, session.selectedRows.size());
                    });
            assertEquals(java.util.Arrays.asList("1", "2", "3", "7"), session.display.rows);
            key(activity, "8");
            await(
                    () ->
                            tagged(activity, "stack-checkbox:0") == null
                                    && !tagged(activity, "stack-label:0").isClickable());
            assertTrue(session.selectedRows.isEmpty());
            assertEquals("8", session.display.entry);
            instrumentation.runOnMainSync(
                    () ->
                            assertEquals(
                                    "⎌", ((Button) tagged(activity, "UNDO")).getText().toString()));
            key(activity, "UNDO");
            assertFalse(session.display.editing);
            assertEquals(java.util.Arrays.asList("1", "2", "3", "7"), session.display.rows);
            key(activity, "UNDO");
            assertTrue(session.display.editing);
            assertEquals("7", session.display.entry);
            assertEquals(java.util.Arrays.asList("1", "2", "3"), session.display.rows);
        } finally {
            finish(activity);
        }
    }
}
