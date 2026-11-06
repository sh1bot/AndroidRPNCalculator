// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.SortedSet;

/** Builds stack rows; calculation, selection policy and clipboard remain with the controller. */
final class StackRowRenderer {
    interface Actions {
        boolean toggle(int index, Display display);

        void tap(int index, Display display);

        void menu(View anchor, int index, Display display);
    }

    private final Context context;
    private final ThemeColors colors;
    private final Actions actions;

    StackRowRenderer(Context context, ThemeColors colors, Actions actions) {
        this.context = context;
        this.colors = colors;
        this.actions = actions;
    }

    private int dp(int n) {
        return Math.round(n * context.getResources().getDisplayMetrics().density);
    }

    private TextView text(int size) {
        TextView value = new TextView(context);
        value.setTextSize(size);
        value.setTextColor(colors.text);
        value.setPadding(dp(4), dp(6), dp(4), dp(6));
        return value;
    }

    private void digits(TextView value) {
        value.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        value.setFontFeatureSettings("tnum");
    }

    private static String withoutApproximation(String number) {
        return number.startsWith("≈ ") ? number.substring(2) : number;
    }

    private void updateStackLabel(TextView label, int depth, String number) {
        boolean approximate = number.startsWith("≈ ");
        String name = depth == 1 ? "x" : depth == 2 ? "y" : Integer.toString(depth);
        label.setText(name + (approximate ? "≈" : ":"));
        label.setContentDescription(
                context.getString(
                        approximate ? R.string.stack_label_approximate : R.string.stack_label,
                        name));
    }

    private String rowDescription(
            boolean copy, int depth, boolean approximate, CharSequence value) {
        int id =
                copy
                        ? (approximate ? R.string.copy_level_approximate : R.string.copy_level)
                        : (approximate
                                ? R.string.select_level_approximate
                                : R.string.select_level_value);
        return context.getString(id, depth, value);
    }

    void render(
            LinearLayout stackRows,
            Display display,
            LocaleNumbers numbers,
            SortedSet<Integer> selectedRows) {
        selectedRows.removeIf(index -> index >= display.rows.size());
        stackRows.removeAllViews();
        if (display.rows.isEmpty()) {
            TextView empty = text(16);
            empty.setText(context.getString(R.string.stack_empty));
            empty.setTextColor(colors.muted);
            stackRows.addView(empty);
        }
        for (int i = 0; i < display.rows.size(); i++) {
            final int index = i;
            int depth = display.rows.size() - i;
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag("stack-row:" + i);
            if (!selectedRows.isEmpty()) {
                CheckBox selected = new CheckBox(context);
                selected.setTag("stack-checkbox:" + i);
                selected.setMinWidth(0);
                selected.setMinimumWidth(0);
                selected.setPadding(0, 0, 0, 0);
                selected.setButtonTintList(
                        new android.content.res.ColorStateList(
                                new int[][] {
                                    new int[] {android.R.attr.state_checked}, new int[] {}
                                },
                                new int[] {colors.accent, colors.muted}));
                selected.setChecked(selectedRows.contains(i));
                selected.setContentDescription(context.getString(R.string.select_level, depth));
                selected.setOnClickListener(
                        v -> {
                            if (!actions.toggle(index, display))
                                selected.setChecked(selectedRows.contains(index));
                        });
                row.addView(selected, new LinearLayout.LayoutParams(dp(40), dp(40)));
            }
            TextView label = text(12);
            digits(label);
            label.setSingleLine(true);
            updateStackLabel(label, depth, display.rows.get(i));
            label.setTag("stack-label:" + i);
            label.setOnLongClickListener(
                    v -> {
                        actions.toggle(index, display);
                        return true;
                    });
            label.setOnClickListener(v -> actions.tap(index, display));
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setTextColor(colors.muted);
            row.addView(label, new LinearLayout.LayoutParams(dp(46), -2));
            boolean result = !display.editing && depth == 1;
            TextView value = text(result ? 32 : 21);
            digits(value);
            value.setGravity(Gravity.END);
            value.setText(numbers.stack(withoutApproximation(display.rows.get(i)), colors.muted));
            if (!result) {
                value.setSingleLine(true);
                value.setHorizontallyScrolling(false);
                value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                value.setAutoSizeTextTypeUniformWithConfiguration(
                        12, 21, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            }
            if (result
                    && display.fractionMode == FractionMode.DECIMAL
                    && display.rows.get(i).contains(".")
                    && !display.rows.get(i).startsWith("≈")) {
                final String fullNumber = display.rows.get(i),
                        compactNumber = display.compactRows.get(i);
                final CharSequence full =
                        numbers.stack(withoutApproximation(fullNumber), colors.muted);
                final CharSequence compact =
                        numbers.stack(withoutApproximation(compactNumber), colors.muted);
                value.setSingleLine(true);
                value.setHorizontallyScrolling(false);
                value.addOnLayoutChangeListener(
                        (v, l, t, r, b, ol, ot, or, ob) -> {
                            int available =
                                    r - l - value.getPaddingLeft() - value.getPaddingRight();
                            if (available <= 0) return;
                            android.text.TextPaint measure =
                                    new android.text.TextPaint(value.getPaint());
                            float scale = context.getResources().getDisplayMetrics().scaledDensity;
                            int size = 32;
                            for (; size > 21; size--) {
                                measure.setTextSize(size * scale);
                                if (android.text.Layout.getDesiredWidth(full, measure) <= available)
                                    break;
                            }
                            measure.setTextSize(size * scale);
                            boolean fits =
                                    android.text.Layout.getDesiredWidth(full, measure) <= available;
                            CharSequence chosen = fits ? full : compact;
                            updateStackLabel(label, depth, fits ? fullNumber : compactNumber);
                            row.setContentDescription(
                                    rowDescription(
                                            selectedRows.isEmpty(),
                                            depth,
                                            !fits && compactNumber.startsWith("≈ "),
                                            chosen));
                            if (!value.getText().toString().equals(chosen.toString()))
                                value.setText(chosen);
                            if (value.getTextSize() != size * scale) value.setTextSize(size);
                        });
            }
            value.setTextIsSelectable(false);
            row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
            value.setTag("stack-value:" + i);
            value.setOnLongClickListener(
                    v -> {
                        actions.menu(value, index, display);
                        return true;
                    });
            value.setOnClickListener(v -> actions.tap(index, display));
            row.setOnLongClickListener(
                    v -> {
                        actions.menu(value, index, display);
                        return true;
                    });
            row.setOnClickListener(v -> actions.tap(index, display));
            row.setContentDescription(
                    rowDescription(
                            selectedRows.isEmpty(),
                            depth,
                            display.rows.get(i).startsWith("≈ "),
                            value.getText()));
            stackRows.addView(row);
        }
        showSelection(stackRows, selectedRows, null);
    }

    /** Menu focus changes only presentation; the real selection keeps its row identities. */
    void showSelection(
            LinearLayout stackRows, SortedSet<Integer> selectedRows, Integer menuTarget) {
        for (int i = 0; i < stackRows.getChildCount(); i++) {
            View row = stackRows.getChildAt(i);
            if (!("stack-row:" + i).equals(row.getTag())) continue;
            View checkbox = row.findViewWithTag("stack-checkbox:" + i);
            if (checkbox != null)
                checkbox.setVisibility(menuTarget == null ? View.VISIBLE : View.INVISIBLE);
            boolean highlighted = menuTarget == null ? selectedRows.contains(i) : menuTarget == i;
            row.setSelected(highlighted);
            if (highlighted) {
                android.graphics.drawable.GradientDrawable highlight =
                        new android.graphics.drawable.GradientDrawable();
                highlight.setColor(colors.numberKey);
                highlight.setCornerRadius(dp(8));
                row.setBackground(highlight);
            } else row.setBackground(null);
        }
    }
}
