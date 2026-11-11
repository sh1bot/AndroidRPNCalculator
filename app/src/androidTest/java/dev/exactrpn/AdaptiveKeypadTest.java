// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class AdaptiveKeypadTest {
    private Button find(View view, String label) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(label))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button result = find(((ViewGroup) view).getChildAt(i), label);
                if (result != null) return result;
            }
        return null;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void size(Context context, View view, int width, int height) {
        int w = dp(context, width), h = dp(context, height);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }

    private Rect bounds(View view, View root) {
        int x = 0, y = 0;
        View current = view;
        while (current != root) {
            x += current.getLeft();
            y += current.getTop();
            current = (View) current.getParent();
        }
        return new Rect(x, y, x + view.getWidth(), y + view.getHeight());
    }

    @Test
    public void modesStayInsideTheStackAndKeypadUsesFullLandscapeHeight() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(
                    () -> {
                        ViewGroup workspace =
                                activity.getWindow()
                                        .getDecorView()
                                        .findViewWithTag("calculator-workspace");
                        ViewGroup stack = workspace.findViewWithTag("stack-panel");
                        ViewGroup modes = stack.findViewWithTag("mode-row");
                        View keypad = workspace.getChildAt(1);
                        assertEquals(stack, modes.getParent());
                        size(context, workspace, 760, 280);
                        assertEquals(0, keypad.getTop());
                        assertEquals(workspace.getHeight(), keypad.getHeight());
                        find(keypad, "f()").performClick();
                        size(context, workspace, 660, 280);
                        assertEquals(0, keypad.getTop());
                        assertEquals(workspace.getHeight(), keypad.getHeight());
                        assertEquals(stack.getWidth(), modes.getWidth());
                        for (int i = 0; i < modes.getChildCount(); i++) {
                            assertTrue(modes.getChildAt(i).getLeft() >= 0);
                            assertTrue(modes.getChildAt(i).getRight() <= modes.getWidth());
                        }
                        size(context, workspace, 392, 700);
                        assertEquals(stack.getBottom(), keypad.getTop());
                        assertEquals(workspace.getWidth(), modes.getWidth());
                    });
        } finally {
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void piTauToggleOnlyChangesTheKeyAndAppliesInBothFunctionSets() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            AtomicReference<Command> command = new AtomicReference<>();
                            AtomicReference<Boolean> preference = new AtomicReference<>(false);
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context, command::set, true, (open, inverse) -> {});
                            keypad.setTauPreference(false, preference::set);
                            find(keypad, "π").performLongClick();
                            assertTrue(preference.get());
                            assertNull(command.get());
                            find(keypad, "τ").performClick();
                            assertEquals("τ", command.get().wire);
                            command.set(null);
                            keypad.setInverse(true);
                            assertNotNull(find(keypad, "τ"));
                            find(keypad, "τ").performLongClick();
                            assertFalse(preference.get());
                            assertNull(command.get());
                            find(keypad, "π").performClick();
                            assertEquals("π", command.get().wire);
                        });
    }

    @Test
    public void toggleSwitchAndCloseWithoutSendingAnArithmeticCommand() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            AtomicReference<Command> command = new AtomicReference<>();
                            AtomicReference<String> state = new AtomicReference<>();
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context,
                                            command::set,
                                            false,
                                            (open, inverse) -> state.set(open + ":" + inverse));
                            assertFalse(keypad.isExpanded());
                            assertNull(find(keypad, "INV"));
                            find(keypad, "f()").performClick();
                            assertTrue(keypad.isExpanded());
                            assertEquals("true:false", state.get());
                            assertNull(command.get());
                            find(keypad, "rem").performClick();
                            assertEquals("rem", command.get().wire);
                            command.set(null);
                            find(keypad, "f⁻¹()").performClick();
                            assertTrue(keypad.isExpanded());
                            assertEquals("true:true", state.get());
                            assertNull(command.get());
                            find(keypad, "mod").performClick();
                            assertEquals("mod", command.get().wire);
                            find(keypad, "⌈x⌉").performClick();
                            assertEquals("ceil", command.get().wire);
                            command.set(null);
                            find(keypad, "f⁻¹()").performClick();
                            assertFalse(keypad.isExpanded());
                            assertEquals("false:true", state.get());
                            assertNull(command.get());
                            find(keypad, "f()").performClick();
                            assertNotNull(find(keypad, "sin"));
                            assertNotNull(find(keypad, "rem"));
                            find(keypad, "f()").performClick();
                            assertFalse(keypad.isExpanded());
                        });
    }

    @Test
    public void reorderedControlsAndHeldDigitsFollowTheirCurrentRoles() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            AtomicReference<Command> command = new AtomicReference<>();
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context, command::set, false, (open, inverse) -> {});
                            CalculatorWorkspace workspace =
                                    new CalculatorWorkspace(context, new View(context), keypad);
                            size(context, workspace, 392, 700);
                            android.text.TextPaint buttonFont = find(keypad, "⎌").getPaint();
                            android.util.Log.i(
                                    "GlyphProbe",
                                    "API="
                                            + android.os.Build.VERSION.SDK_INT
                                            + " trash="
                                            + buttonFont.hasGlyph("🗑")
                                            + " textTrash="
                                            + buttonFont.hasGlyph("🗑︎")
                                            + " emojiTrash="
                                            + buttonFont.hasGlyph("🗑️")
                                            + " swap="
                                            + buttonFont.hasGlyph("⇅"));
                            assertTrue(
                                    "Undo glyph must be available in the button font",
                                    find(keypad, "⎌").getPaint().hasGlyph("⎌"));
                            Rect undo = bounds(find(keypad, "⎌"), workspace),
                                    clear = bounds(find(keypad, "AC"), workspace),
                                    drop =
                                            bounds(
                                                    ((Button) keypad.findViewWithTag("BACK")),
                                                    workspace),
                                    swap =
                                            bounds(
                                                    ((Button) keypad.findViewWithTag("SWAP")),
                                                    workspace);
                            assertTrue(undo.bottom <= clear.top);
                            assertEquals(clear.top, drop.top);
                            assertEquals(drop.top, swap.top);
                            assertTrue(clear.right <= drop.left);
                            assertTrue(drop.right <= swap.left);
                            TestCalculator calculator = new TestCalculator();
                            for (String key : new String[] {"1", "ENTER", "2", "ENTER"})
                                calculator.key(key);
                            find(keypad, "2").performLongClick();
                            assertEquals(Command.Kind.HOLD_DIGIT, command.get().kind);
                            assertEquals("2", command.get().text);
                            calculator.apply(
                                    command.get(), NumberText.COMPUTER, NumberText.COMPUTER);
                            assertEquals(
                                    java.util.Arrays.asList("1", "2", "1"),
                                    CalculatorTestSupport.formattedValues(
                                            calculator,
                                            ValueFormatter.Notation.SCIENTIFIC,
                                            FractionMode.DECIMAL));
                            find(keypad, "0").performLongClick();
                            assertEquals(Command.Kind.HOLD_DIGIT, command.get().kind);
                            assertEquals("0", command.get().text);
                            CalculatorTestSupport.paste(calculator, "4");
                            keypad.setMode(InteractionMode.ENTRY);
                            find(keypad, "1").performLongClick();
                            calculator.apply(
                                    command.get(), NumberText.COMPUTER, NumberText.COMPUTER);
                            assertEquals("41", calculator.entry());
                            assertEquals(3, calculator.values().size());
                            assertNotNull(find(keypad, "CE"));
                            assertNotNull(find(keypad, "⌫"));
                            keypad.setCalculating(true);
                            find(keypad, "Cancel").performLongClick();
                            assertEquals("CANCEL", command.get().wire);
                        });
    }

    @Test
    public void radixDigitsStayInsideTheDecimalFootprintWithoutMovingTheStack() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            View stack = new View(context);
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context, key -> {}, false, (open, inverse) -> {});
                            CalculatorWorkspace workspace =
                                    new CalculatorWorkspace(context, stack, keypad);
                            size(context, workspace, 392, 700);
                            int stackHeight = stack.getHeight(), keypadHeight = keypad.getHeight();
                            Rect digitArea = bounds(find(keypad, "7"), workspace);
                            digitArea.union(bounds(find(keypad, "3"), workspace));
                            Rect multiply = bounds(find(keypad, "×"), workspace);

                            keypad.setNumberFormat(
                                    new LocaleNumbers(
                                            Locale.US, NumberMode.DOT, RadixMode.DOZENAL));
                            size(context, workspace, 392, 700);
                            assertEquals(stackHeight, stack.getHeight());
                            assertEquals(keypadHeight, keypad.getHeight());
                            assertTrue(digitArea.contains(bounds(find(keypad, "↊"), workspace)));
                            assertEquals(multiply, bounds(find(keypad, "×"), workspace));
                            Button dozenal = find(keypad, "↊");
                            android.text.Spanned dozenalLabel =
                                    (android.text.Spanned) dozenal.getText();
                            TurnedDigitSpan[] turned =
                                    dozenalLabel.getSpans(
                                            0, dozenalLabel.length(), TurnedDigitSpan.class);
                            assertEquals(1, turned.length);
                            assertEquals(
                                    (int) Math.ceil(dozenal.getPaint().measureText("0")),
                                    turned[0].getSize(
                                            dozenal.getPaint(), dozenalLabel, 0, 1, null));

                            keypad.setNumberFormat(
                                    new LocaleNumbers(Locale.US, NumberMode.DOT, RadixMode.HEX));
                            size(context, workspace, 392, 700);
                            assertEquals(stackHeight, stack.getHeight());
                            assertEquals(keypadHeight, keypad.getHeight());
                            assertTrue(digitArea.contains(bounds(find(keypad, "F"), workspace)));
                            assertEquals(multiply, bounds(find(keypad, "×"), workspace));
                            assertEquals(
                                    find(keypad, "0").getPaint().measureText("0"),
                                    find(keypad, "F").getPaint().measureText("F"),
                                    .01f);

                            find(keypad, "f()").performClick();
                            size(context, workspace, 392, 700);
                            assertTrue(
                                    bounds(find(keypad, "×"), workspace).height()
                                            > bounds(find(keypad, "sin"), workspace).height());
                        });
    }

    @Test
    public void landscapeCollapseGivesWidthBackAndResizeKeepsPanelState() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            View stack = new View(context);
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context, key -> {}, false, (open, inverse) -> {});
                            CalculatorWorkspace workspace =
                                    new CalculatorWorkspace(context, stack, keypad);
                            size(context, workspace, 760, 280);
                            int closedWidth = stack.getWidth();
                            find(keypad, "f()").performClick();
                            size(context, workspace, 760, 280);
                            assertTrue(stack.getWidth() < closedWidth);
                            Rect sin = bounds(find(keypad, "sin"), workspace),
                                    clear = bounds(find(keypad, "AC"), workspace);
                            assertTrue("Functions must open to the left", sin.right <= clear.left);
                            Rect undo = bounds(find(keypad, "⎌"), workspace),
                                    functions = bounds(find(keypad, "f()"), workspace);
                            assertTrue(functions.width() > undo.width() * 1.6);
                            assertTrue(undo.bottom <= clear.top);
                            assertTrue(
                                    "Controls stay above digits (allow pixel rounding)",
                                    Math.abs(undo.left - clear.left) <= 1);
                            find(keypad, "f()").performClick();
                            size(context, workspace, 760, 280);
                            assertEquals(closedWidth, stack.getWidth());
                            find(keypad, "f⁻¹()").performClick();
                            size(context, workspace, 392, 700);
                            assertTrue(keypad.isExpanded());
                            assertNotNull(find(keypad, "asin"));
                            assertTrue(
                                    bounds(find(keypad, "asin"), workspace).bottom
                                            <= bounds(find(keypad, "AC"), workspace).top);
                            size(context, workspace, 600, 300);
                            assertTrue(
                                    "Narrow landscape stacks the function rows",
                                    bounds(find(keypad, "asin"), workspace).bottom
                                            <= bounds(find(keypad, "AC"), workspace).top);
                            assertTrue(stack.getWidth() >= dp(context, 200));
                        });
    }
}
