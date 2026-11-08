// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;

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
    public void wholeRowsSelectAndControlsHaveFixedSelectionRoles() throws Exception {
        MainActivity a = open();
        CalculatorSession session = session(a);
        try {
            seed(a);
            instrumentation.runOnMainSync(
                    () -> {
                        tagged(a, "stack-value:1").performClick();
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(1)),
                                session.selectedRows);
                        assertEquals("Pick", ((Button) tagged(a, "ENTER")).getText().toString());
                        assertFalse(tagged(a, "ENTER").isLongClickable());
                        assertFalse(tagged(a, "SWAP").isEnabled());
                        tagged(a, "CLEAR").performClick();
                        assertEquals(3, session.selectedRows.size());
                        tagged(a, "CLEAR").performClick();
                        assertEquals(3, session.selectedRows.size());
                        tagged(a, "UNDO").performClick();
                        assertTrue(session.selectedRows.isEmpty());
                    });
            selectEnds(a);
            key(a, "ENTER");
            assertEquals(java.util.Arrays.asList("1", "2", "3", "1", "3"), session.display.rows);
            key(a, "+");
            key(a, "×");
            assertEquals(
                    java.util.Arrays.asList("1", "2", "3", "1", "3", "4", "3"),
                    session.display.rows);
            key(a, "BACK");
            assertEquals(java.util.Arrays.asList("2", "1", "3", "4", "3"), session.display.rows);
        } finally {
            finish(a);
        }
    }

    @Test
    public void unaryFunctionsReplaceSelectedRowsAndAreUndoableTogether() throws Exception {
        MainActivity a = open();
        CalculatorSession session = session(a);
        try {
            seed(a);
            selectEnds(a);
            key(a, "±");
            assertEquals(java.util.Arrays.asList("-1", "2", "-3"), session.display.rows);
            assertEquals(2, session.selectedRows.size());
            key(a, "FUNCTIONS");
            instrumentation.runOnMainSync(
                    () -> {
                        assertFalse(tagged(a, "Factor").isEnabled());
                        assertFalse(tagged(a, "xʸ").isEnabled());
                        assertFalse(tagged(a, "÷").isEnabled());
                    });
            key(a, "UNDO");
            key(a, "UNDO");
            assertEquals(java.util.Arrays.asList("1", "2", "3"), session.display.rows);
        } finally {
            finish(a);
        }
    }

    @Test
    public void longPressCopiesOnlyItsRowIncludingDuringEntry() throws Exception {
        MainActivity a = open();
        CalculatorSession session = session(a);
        try {
            seed(a);
            selectEnds(a);
            ClipboardManager clipboard =
                    (ClipboardManager)
                            instrumentation
                                    .getTargetContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);
            instrumentation.runOnMainSync(() -> tagged(a, "stack-value:1").performLongClick());
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "2"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            assertEquals(2, session.selectedRows.size());
            key(a, "8");
            instrumentation.runOnMainSync(() -> tagged(a, "stack-row:0").performLongClick());
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "1"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            assertTrue(session.display.editing);
            assertEquals("8", session.display.entry);
            assertTrue(session.selectedRows.isEmpty());
            instrumentation.runOnMainSync(() -> tagged(a, "stack-row:0").performClick());
            assertTrue(session.selectedRows.isEmpty());
        } finally {
            finish(a);
        }
    }
}
