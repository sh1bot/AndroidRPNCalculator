// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Applies the {@link KeypadLayout} plan to the stack and keypad on every measure. */
final class CalculatorWorkspace extends ViewGroup {
    private final View stack;
    private final CalculatorKeypad keypad;
    private KeypadLayout plan;
    private int headerBasisWidth;

    CalculatorWorkspace(Context context, View stack, CalculatorKeypad keypad) {
        super(context);
        this.stack = stack;
        this.keypad = keypad;
        addView(stack);
        addView(keypad);
        setTag("calculator-workspace");
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    /** The stack width the mode header sizes its buttons from; see {@link #onMeasure}. */
    int headerBasisWidth() {
        return headerBasisWidth;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        // The stack's minimum rectangle, derived from the nominal 22sp stack font and scaled
        // by the user's font size: the width fits a 20-digit value (0.6 × 22sp per digit cell)
        // plus a 36dp label allowance; the height fits four lines at a 40dp pitch below the
        // 48dp mode header.
        float fontScale = Math.max(1, getResources().getConfiguration().fontScale);
        int minStackWidth = dp(Math.round(300 * fontScale));
        int minStackHeight = dp(Math.round(208 * fontScale));
        int gap = dp(12);
        plan =
                KeypadLayout.choose(
                        width,
                        height,
                        keypad.isExpanded(),
                        // Mirroring is only effective beside the stack, so the keypad's
                        // left-handed preference applies only when the window is wider
                        // than tall; portrait layouts are never mirrored.
                        keypad.isMirrored() && width > height,
                        gap,
                        minStackWidth,
                        minStackHeight,
                        keypad.primaryColumns(),
                        keypad.primaryRowCount());
        // The mode header sizes its buttons from the plan's expanded stack width, not
        // from the panel's current width: in R1 the panel narrows when the function
        // column opens, and a header sized from its instantaneous width would resize
        // its buttons on every toggle.
        headerBasisWidth = plan.expandedStackWidth;
        keypad.setGeometry(plan);
        stack.measure(
                MeasureSpec.makeMeasureSpec(plan.stackWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(plan.stackHeight, MeasureSpec.EXACTLY));
        keypad.measure(
                MeasureSpec.makeMeasureSpec(plan.keypadWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(plan.keypadHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        stack.layout(plan.stackX, 0, plan.stackX + plan.stackWidth, plan.stackHeight);
        int y = plan.besideStack ? 0 : getHeight() - plan.keypadHeight;
        keypad.layout(plan.keypadX, y, plan.keypadX + plan.keypadWidth, y + plan.keypadHeight);
    }
}
