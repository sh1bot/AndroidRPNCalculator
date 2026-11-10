// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Returns unused function-panel space to the stack on every measure, including resizing. */
final class CalculatorWorkspace extends ViewGroup {
    private final View stack;
    private final CalculatorKeypad keypad;
    private KeypadLayout plan;

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

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        // Font scaling increases the room reserved for both the stack and key labels.
        float fontScale = getResources().getConfiguration().fontScale;
        int keyMinimum = dp(Math.round(48 * Math.max(1, fontScale * .85f)));
        plan =
                KeypadLayout.choose(
                        width,
                        height,
                        keypad.isExpanded(),
                        dp(12),
                        keyMinimum,
                        dp(48),
                        dp(Math.round(200 * Math.max(1, fontScale))),
                        // The mode row now lives inside the stack; keep the previous
                        // 112dp calculation area plus its 48dp header in vertical layouts.
                        dp(112 + 48),
                        keypad.primaryColumns(),
                        keypad.primaryRowCount());
        keypad.setGeometry(plan.functionsBesideDigits, plan.primaryWidth);
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
        stack.layout(0, 0, plan.stackWidth, plan.stackHeight);
        int x = plan.besideStack ? getWidth() - plan.keypadWidth : 0;
        int y = plan.besideStack ? 0 : getHeight() - plan.keypadHeight;
        keypad.layout(x, y, x + plan.keypadWidth, y + plan.keypadHeight);
    }
}
