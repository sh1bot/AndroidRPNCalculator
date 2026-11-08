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

        void copy(int index, Display display);
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

    private float sp(int size) {
        return android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                size,
                context.getResources().getDisplayMetrics());
    }

    private void keepStackRowHeight(TextView value) {
        android.text.TextPaint previous = new android.text.TextPaint(value.getPaint());
        previous.setTextSize(sp(21));
        android.graphics.Paint.FontMetricsInt oldMetrics = previous.getFontMetricsInt();
        android.graphics.Paint.FontMetricsInt newMetrics = value.getPaint().getFontMetricsInt();
        int oldTextHeight = oldMetrics.bottom - oldMetrics.top;
        int newTextHeight = newMetrics.bottom - newMetrics.top;
        int padding = Math.max(0, 2 * dp(6) - (newTextHeight - oldTextHeight));
        value.setPadding(dp(4), padding / 2, dp(4), padding - padding / 2);
        value.setMinimumHeight(oldTextHeight + 2 * dp(6));
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

    private void fitBottomRow(
            TextView value,
            TextView label,
            LinearLayout row,
            LinearLayout stackRows,
            Display display,
            LocaleNumbers numbers,
            int index,
            int depth) {
        View viewport = (View) stackRows.getParent();
        String fullNumber = display.rows.get(index), compactNumber = display.compactRows.get(index);
        CharSequence full = numbers.stack(withoutApproximation(fullNumber), colors.muted);
        CharSequence compact = numbers.stack(withoutApproximation(compactNumber), colors.muted);
        int[] previousSize = {-1, -1};
        Runnable fit =
                () -> {
                    int width = value.getWidth() - value.getPaddingLeft() - value.getPaddingRight();
                    int height =
                            viewport.getHeight()
                                    - viewport.getPaddingTop()
                                    - viewport.getPaddingBottom();
                    if (width <= 0
                            || height <= 0
                            || width == previousSize[0] && height == previousSize[1]) return;
                    previousSize[0] = width;
                    previousSize[1] = height;
                    android.text.TextPaint measure = new android.text.TextPaint(value.getPaint());
                    measure.setTextSize(sp(32));
                    android.text.StaticLayout layout =
                            android.text.StaticLayout.Builder.obtain(
                                            full, 0, full.length(), measure, width)
                                    .setIncludePad(value.getIncludeFontPadding())
                                    .setBreakStrategy(value.getBreakStrategy())
                                    .setHyphenationFrequency(value.getHyphenationFrequency())
                                    .setLineSpacing(
                                            value.getLineSpacingExtra(),
                                            value.getLineSpacingMultiplier())
                                    .build();
                    boolean fits =
                            layout.getHeight() + value.getPaddingTop() + value.getPaddingBottom()
                                    <= height;
                    CharSequence chosen = fits ? full : compact;
                    int size = 32;
                    if (!fits) {
                        for (; size > 22; size--) {
                            measure.setTextSize(sp(size));
                            if (android.text.Layout.getDesiredWidth(compact, measure) <= width)
                                break;
                        }
                    }
                    if (value.getTextSize() != sp(size)) value.setTextSize(size);
                    value.setEllipsize(fits ? null : android.text.TextUtils.TruncateAt.END);
                    int lines = fits ? layout.getLineCount() : 1;
                    if (value.getMaxLines() != lines) value.setMaxLines(lines);
                    if (!value.getText().toString().equals(chosen.toString()))
                        value.setText(chosen);
                    updateStackLabel(label, depth, fits ? fullNumber : compactNumber);
                    row.setContentDescription(
                            rowDescription(
                                    false,
                                    depth,
                                    (fits ? fullNumber : compactNumber).startsWith("≈ "),
                                    chosen));
                };
        View.OnLayoutChangeListener resized = (v, l, t, r, b, ol, ot, or, ob) -> fit.run();
        value.addOnLayoutChangeListener(resized);
        value.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View v) {
                        viewport.addOnLayoutChangeListener(resized);
                    }

                    public void onViewDetachedFromWindow(View v) {
                        viewport.removeOnLayoutChangeListener(resized);
                    }
                });
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
                selected.setOnLongClickListener(
                        v -> {
                            actions.copy(index, display);
                            return true;
                        });
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
            if (!display.editing) label.setOnClickListener(v -> actions.tap(index, display));
            label.setOnLongClickListener(
                    v -> {
                        actions.copy(index, display);
                        return true;
                    });
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setTextColor(colors.muted);
            row.addView(label, new LinearLayout.LayoutParams(dp(46), -2));
            boolean result = !display.editing && depth == 1;
            TextView value = text(result ? 32 : 22);
            digits(value);
            value.setGravity(Gravity.END);
            value.setText(numbers.stack(withoutApproximation(display.rows.get(i)), colors.muted));
            if (!result) {
                keepStackRowHeight(value);
                value.setSingleLine(true);
                value.setHorizontallyScrolling(false);
                value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                value.setAutoSizeTextTypeUniformWithConfiguration(
                        12, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
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
                            int size = 32;
                            for (; size > 22; size--) {
                                measure.setTextSize(sp(size));
                                if (android.text.Layout.getDesiredWidth(full, measure) <= available)
                                    break;
                            }
                            measure.setTextSize(sp(size));
                            boolean fits =
                                    android.text.Layout.getDesiredWidth(full, measure) <= available;
                            CharSequence chosen = fits ? full : compact;
                            updateStackLabel(label, depth, fits ? fullNumber : compactNumber);
                            row.setContentDescription(
                                    rowDescription(
                                            display.editing,
                                            depth,
                                            !fits && compactNumber.startsWith("≈ "),
                                            chosen));
                            if (!value.getText().toString().equals(chosen.toString()))
                                value.setText(chosen);
                            if (value.getTextSize() != sp(size)) value.setTextSize(size);
                        });
            }
            if (result
                    && !(display.fractionMode == FractionMode.DECIMAL
                            && display.rows.get(i).contains(".")
                            && !display.rows.get(i).startsWith("≈")))
                fitBottomRow(value, label, row, stackRows, display, numbers, i, depth);
            value.setTextIsSelectable(false);
            row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
            value.setTag("stack-value:" + i);
            value.setOnClickListener(v -> actions.tap(index, display));
            row.setOnClickListener(v -> actions.tap(index, display));
            value.setOnLongClickListener(
                    v -> {
                        actions.copy(index, display);
                        return true;
                    });
            row.setOnLongClickListener(
                    v -> {
                        actions.copy(index, display);
                        return true;
                    });
            row.setContentDescription(
                    rowDescription(
                            display.editing,
                            depth,
                            display.rows.get(i).startsWith("≈ "),
                            value.getText()));
            if (display.editing)
                label.setContentDescription(
                        rowDescription(
                                true,
                                depth,
                                display.rows.get(i).startsWith("≈ "),
                                value.getText()));
            stackRows.addView(row);
        }
        showSelection(stackRows, selectedRows);
    }

    private void showSelection(LinearLayout stackRows, SortedSet<Integer> selectedRows) {
        for (int i = 0; i < stackRows.getChildCount(); i++) {
            View row = stackRows.getChildAt(i);
            if (!("stack-row:" + i).equals(row.getTag())) continue;
            boolean highlighted = selectedRows.contains(i);
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
