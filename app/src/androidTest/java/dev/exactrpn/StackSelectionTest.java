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
    public void largeMixedAndImproperFractionsStayReadableOnEveryStackRow() throws Exception {
        MainActivity a = open();
        CalculatorSession state = session(a);
        try {
            for (Command command :
                    new Command[] {
                        Command.paste("1e999"),
                        Command.key("ENTER"),
                        Command.paste("7"),
                        Command.key("÷"),
                        Command.key("ENTER")
                    }) {
                instrumentation.runOnMainSync(() -> state.enqueue(command));
                await(() -> !state.busy);
            }
            for (int mode = 0; mode < 2; mode++) {
                String expectedMode = mode == 0 ? "FRAC" : "IMPR";
                instrumentation.runOnMainSync(() -> state.enqueue(Command.key("FRACTION")));
                await(() -> !state.busy);
                await(
                        () -> {
                            android.widget.TextView modeButton =
                                    (android.widget.TextView) tagged(a, "FRACTION");
                            if (!expectedMode.contentEquals(modeButton.getText())) return false;
                            for (int row = 0; row < 2; row++) {
                                android.widget.TextView value =
                                        (android.widget.TextView) tagged(a, "stack-value:" + row);
                                if (value == null
                                        || value.getText().length() >= 100
                                        || value.getLayout() == null) return false;
                                for (int line = 0; line < value.getLayout().getLineCount(); line++)
                                    if (value.getLayout().getEllipsisCount(line) != 0) return false;
                            }
                            return true;
                        });
            }
        } finally {
            finish(a);
        }
    }

    @Test
    public void fractionWhichWouldWrapUsesAStackedBar() throws Exception {
        MainActivity activity = open();
        CalculatorSession state = session(activity);
        try {
            for (Command command :
                    new Command[] {
                        Command.paste("123456789012345678901234567890"),
                        Command.key("ENTER"),
                        Command.paste("7000000000000000003"),
                        Command.key("÷"),
                        Command.key("FRACTION")
                    }) {
                instrumentation.runOnMainSync(() -> state.enqueue(command));
                await(() -> !state.busy);
            }
            await(
                    () -> {
                        View value = tagged(activity, "stack-value:0");
                        return value instanceof FractionTextView
                                && ((FractionTextView) value).isStackedFraction();
                    });
        } finally {
            finish(activity);
        }
    }

    @Test
    public void fittingFractionRemainsExactAboveTheBottomRow() throws Exception {
        MainActivity activity = open();
        CalculatorSession state = session(activity);
        try {
            for (Command command :
                    new Command[] {
                        Command.paste("1"),
                        Command.key("ENTER"),
                        Command.paste("3"),
                        Command.key("÷"),
                        Command.paste("2"),
                        Command.key("ENTER"),
                        Command.key("FRACTION")
                    }) {
                instrumentation.runOnMainSync(() -> state.enqueue(command));
                await(() -> !state.busy);
            }
            await(
                    () -> {
                        View upper = tagged(activity, "stack-value:0");
                        return upper instanceof FractionTextView
                                && "1/3".contentEquals(((android.widget.TextView) upper).getText())
                                && ((FractionTextView) upper).isStackedFraction();
                    });
        } finally {
            finish(activity);
        }
    }

    @Test
    public void fittingLargeIntegerStaysPlainAboveTheBottomRow() throws Exception {
        MainActivity activity = open();
        CalculatorSession state = session(activity);
        try {
            for (Command command :
                    new Command[] {
                        Command.paste("1234567890123"),
                        Command.key("ENTER"),
                        Command.paste("2"),
                        Command.key("ENTER")
                    }) {
                instrumentation.runOnMainSync(() -> state.enqueue(command));
                await(() -> !state.busy);
            }
            await(
                    () -> {
                        android.widget.TextView upper =
                                (android.widget.TextView) tagged(activity, "stack-value:0");
                        return upper != null
                                && upper.getText().length() >= 13
                                && upper.getText().toString().indexOf('×') < 0;
                    });
        } finally {
            finish(activity);
        }
    }

    @Test
    public void copyingAndCyclingHugeFractionModesKeepsEachModeStable() throws Exception {
        MainActivity activity = open();
        CalculatorSession state = session(activity);
        try {
            for (Command command :
                    new Command[] {
                        Command.paste("1e999"),
                        Command.key("ENTER"),
                        Command.paste("7"),
                        Command.key("÷")
                    }) {
                instrumentation.runOnMainSync(() -> state.enqueue(command));
                await(() -> !state.busy);
            }
            while (state.display.fractionMode != FractionMode.DECIMAL) {
                instrumentation.runOnMainSync(() -> state.enqueue(Command.key("FRACTION")));
                await(() -> !state.busy);
            }
            await(() -> tagged(activity, "stack-value:0") != null);
            String[] firstText = new String[3];
            float[] firstSize = new float[3];
            int[] firstHeight = new int[3], firstLines = new int[3];
            ClipboardManager clipboard =
                    (ClipboardManager)
                            instrumentation
                                    .getTargetContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);
            for (int mode = 0; mode < 7; mode++) {
                int slot = mode % 3;
                String expectedMode = new String[] {"DEC", "FRAC", "IMPR"}[slot];
                await(
                        () -> {
                            android.widget.TextView modeButton =
                                    (android.widget.TextView) tagged(activity, "FRACTION");
                            android.widget.TextView value =
                                    (android.widget.TextView) tagged(activity, "stack-value:0");
                            android.widget.TextView label =
                                    (android.widget.TextView) tagged(activity, "stack-label:0");
                            return expectedMode.contentEquals(modeButton.getText())
                                    && label != null
                                    && "x≈".contentEquals(label.getText())
                                    && value != null
                                    && value.getText().length() < 100
                                    && value.getLayout() != null;
                        });
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(
                        () -> {
                            android.widget.TextView value =
                                    (android.widget.TextView) tagged(activity, "stack-value:0");
                            if (firstText[slot] == null) {
                                firstText[slot] = value.getText().toString();
                                firstSize[slot] = value.getTextSize();
                                firstHeight[slot] = value.getHeight();
                                firstLines[slot] = value.getLayout().getLineCount();
                            } else {
                                assertEquals(firstText[slot], value.getText().toString());
                                assertEquals(firstSize[slot], value.getTextSize(), .01f);
                                assertEquals(firstHeight[slot], value.getHeight());
                                assertEquals(firstLines[slot], value.getLayout().getLineCount());
                            }
                            for (int line = 0; line < value.getLayout().getLineCount(); line++)
                                assertEquals(0, value.getLayout().getEllipsisCount(line));
                            clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("test", "pending"));
                            value.performLongClick();
                        });
                await(
                        () ->
                                !state.busy
                                        && clipboard.hasPrimaryClip()
                                        && !"pending"
                                                .contentEquals(
                                                        clipboard
                                                                .getPrimaryClip()
                                                                .getItemAt(0)
                                                                .getText()));
                instrumentation.runOnMainSync(() -> state.enqueue(Command.key("FRACTION")));
                await(() -> !state.busy);
            }
        } finally {
            finish(activity);
        }
    }

    @Test
    public void bottomIntegerFitsTheStackViewportBeforeAndAfterExpandingFunctions()
            throws Exception {
        MainActivity a = open();
        CalculatorSession state = session(a);
        try {
            char[] digits = new char[600];
            java.util.Arrays.fill(digits, '7');
            instrumentation.runOnMainSync(() -> state.enqueue(Command.paste(new String(digits))));
            await(() -> !state.busy && state.display.editing);
            key(a, "ENTER");
            try {
                await(
                        () -> {
                            android.widget.TextView value =
                                    (android.widget.TextView) tagged(a, "stack-value:0");
                            if (value == null) return false;
                            View row = tagged(a, "stack-row:0");
                            android.widget.ScrollView viewport =
                                    (android.widget.ScrollView) row.getParent().getParent();
                            return value.getText().length() < 100
                                    && row.getHeight() <= viewport.getHeight()
                                    && row.getBottom()
                                            <= viewport.getScrollY() + viewport.getHeight();
                        });
            } catch (AssertionError failed) {
                java.util.concurrent.atomic.AtomicReference<String> detail =
                        new java.util.concurrent.atomic.AtomicReference<>();
                instrumentation.runOnMainSync(
                        () -> {
                            android.widget.TextView value =
                                    (android.widget.TextView) tagged(a, "stack-value:0");
                            View row = tagged(a, "stack-row:0");
                            View viewport = (View) row.getParent().getParent();
                            detail.set(
                                    "textLength="
                                            + value.getText().length()
                                            + " valueHeight="
                                            + value.getHeight()
                                            + " rowHeight="
                                            + row.getHeight()
                                            + " viewportHeight="
                                            + viewport.getHeight()
                                            + " width="
                                            + value.getWidth()
                                            + " maxLines="
                                            + value.getMaxLines());
                        });
                throw new AssertionError(detail.get(), failed);
            }
            key(a, "FUNCTIONS");
            await(
                    () -> {
                        View row = tagged(a, "stack-row:0");
                        android.widget.ScrollView viewport =
                                (android.widget.ScrollView) row.getParent().getParent();
                        return row.getHeight() <= viewport.getHeight()
                                && row.getBottom() <= viewport.getScrollY() + viewport.getHeight();
                    });
            TestCalculator saved = TestCalculator.load(state.checkpoint.bytes);
            assertEquals(new String(digits), saved.values().get(0).bigIntegerValue().toString());
        } finally {
            finish(a);
        }
    }

    @Test
    public void cancelOverridesUnselectDuringASelectedUnaryOperation() throws Exception {
        MainActivity a = open();
        CalculatorSession state = session(a);
        try {
            for (String key : new String[] {"π", "e", "+", "e", "π", "+", "−"}) key(a, key);
            await(() -> tagged(a, "stack-row:0") != null && tagged(a, "stack-row:1") == null);
            instrumentation.runOnMainSync(() -> tagged(a, "stack-row:0").performClick());
            assertEquals(1, state.selectedRows.size());
            instrumentation.runOnMainSync(() -> tagged(a, "1/x").performClick());
            SystemClock.sleep(300);
            assertTrue(state.busy);
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals("Cancel", ((Button) tagged(a, "UNDO")).getText().toString());
                        assertEquals(
                                "Cancel calculation",
                                tagged(a, "UNDO").getContentDescription().toString());
                        tagged(a, "UNDO").performClick();
                    });
            long started = SystemClock.elapsedRealtime();
            await(() -> !state.busy);
            assertTrue(SystemClock.elapsedRealtime() - started < 2000);
            assertEquals(1, state.selectedRows.size());
            assertEquals(1, state.display.rows.size());
        } finally {
            finish(a);
        }
    }

    @Test
    public void hugeIntegerResultUsesCompactTextWithoutBlockingTheActivity() throws Exception {
        MainActivity a = open();
        CalculatorSession state = session(a);
        try {
            for (String key : new String[] {"6", "5", "5", "3", "6"}) key(a, key);
            instrumentation.runOnMainSync(() -> state.enqueue(Command.key("10ˣ")));
            await(() -> !state.busy && !state.display.editing);
            assertEquals(java.util.Arrays.asList("1e+65536"), state.display.rows);
            assertTrue(state.error.isEmpty());
            key(a, "ENTER");
            key(a, "÷");
            assertEquals(java.util.Arrays.asList("1"), state.display.rows);
        } finally {
            finish(a);
        }
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
