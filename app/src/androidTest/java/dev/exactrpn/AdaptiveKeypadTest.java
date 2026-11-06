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
    public void toggleSwitchAndCloseWithoutSendingAnArithmeticCommand() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            AtomicReference<String> command = new AtomicReference<>(),
                                    state = new AtomicReference<>();
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
                            assertEquals("rem", command.get());
                            command.set(null);
                            find(keypad, "f⁻¹()").performClick();
                            assertTrue(keypad.isExpanded());
                            assertEquals("true:true", state.get());
                            assertNull(command.get());
                            find(keypad, "mod").performClick();
                            assertEquals("mod", command.get());
                            find(keypad, "⌈x⌉").performClick();
                            assertEquals("ceil", command.get());
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
                                    undo = bounds(find(keypad, "Undo"), workspace);
                            assertTrue("Functions must open to the left", sin.right <= undo.left);
                            Rect clear = bounds(find(keypad, "Clear"), workspace),
                                    functions = bounds(find(keypad, "f()"), workspace);
                            assertTrue(functions.width() > clear.width() * 1.6);
                            assertTrue(clear.bottom <= undo.top);
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
                                            <= bounds(find(keypad, "Undo"), workspace).top);
                            size(context, workspace, 600, 300);
                            assertTrue(
                                    "Narrow landscape stacks the function rows",
                                    bounds(find(keypad, "asin"), workspace).bottom
                                            <= bounds(find(keypad, "Undo"), workspace).top);
                            assertTrue(stack.getWidth() >= dp(context, 200));
                        });
    }
}
