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

    private void digits(TextView value, RadixMode radix) {
        value.setTypeface(
                Typeface.create(
                        radix == RadixMode.DECIMAL ? "sans-serif" : "monospace", Typeface.NORMAL));
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
        return context.getString(id, depth, value.toString().replace("\u200b", ""));
    }

    private int fittedSize(CharSequence text, android.text.TextPaint paint, int width) {
        int size = 32;
        for (; size > 22; size--) {
            paint.setTextSize(sp(size));
            if (android.text.Layout.getDesiredWidth(text, paint) <= width) break;
        }
        return size;
    }

    private void fitBottomRow(
            TextView value,
            TextView label,
            LinearLayout row,
            LinearLayout stackRows,
            Display display,
            LocaleNumbers numbers,
            int index,
            int depth,
            boolean followBottom) {
        View viewport = (View) stackRows.getParent();
        String fullNumber = display.rows.get(index), compactNumber = display.compactRows.get(index);
        CharSequence full =
                numbers.breakable(numbers.stack(withoutApproximation(fullNumber), colors.muted));
        CharSequence compact =
                numbers.breakable(numbers.stack(withoutApproximation(compactNumber), colors.muted));
        int[] previousSize = {-1, -1};
        Runnable fit =
                () -> {
                    if (!value.isAttachedToWindow()) return;
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
                    int fullSize =
                            fullNumber.startsWith("≈") ? fittedSize(full, measure, width) : 32;
                    measure.setTextSize(sp(fullSize));
                    android.text.StaticLayout layout =
                            android.text.StaticLayout.Builder.obtain(
                                            full, 0, full.length(), measure, width)
                                    .setIncludePad(value.getIncludeFontPadding())
                                    .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED)
                                    .setHyphenationFrequency(value.getHyphenationFrequency())
                                    .setLineSpacing(
                                            value.getLineSpacingExtra(),
                                            value.getLineSpacingMultiplier())
                                    .build();
                    boolean fits =
                            layout.getHeight() + value.getPaddingTop() + value.getPaddingBottom()
                                    <= height;
                    for (int line = 0; fits && line < layout.getLineCount(); line++)
                        if (layout.getLineWidth(line) > width) fits = false;
                    CharSequence chosen = fits ? full : compact;
                    int size = fits ? fullSize : fittedSize(compact, measure, width);
                    if (value.getTextSize() != sp(size)) value.setTextSize(size);
                    value.setEllipsize(fits ? null : android.text.TextUtils.TruncateAt.END);
                    int lines = fits ? layout.getLineCount() : 1;
                    if (value.getMaxLines() != lines) value.setMaxLines(lines);
                    if (!value.getText().toString().equals(chosen.toString()))
                        value.setText(chosen);
                    updateStackLabel(label, depth, fits ? fullNumber : compactNumber);
                    value.requestLayout();
                    row.setContentDescription(
                            rowDescription(
                                    false,
                                    depth,
                                    (fits ? fullNumber : compactNumber).startsWith("≈ "),
                                    chosen));
                };
        // Fit before drawing, and let a requested relayout finish before showing the row.
        // This avoids painting either the unbounded value or a stale font size for a frame.
        boolean[] pendingFollow = {followBottom};
        android.view.ViewTreeObserver.OnPreDrawListener fitted =
                () -> {
                    fit.run();
                    boolean ready = !value.isLayoutRequested();
                    if (ready && pendingFollow[0]) {
                        pendingFollow[0] = false;
                        viewport.post(
                                () ->
                                        ((android.widget.ScrollView) viewport)
                                                .fullScroll(View.FOCUS_DOWN));
                    }
                    return ready;
                };
        value.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    private android.view.ViewTreeObserver observer;

                    public void onViewAttachedToWindow(View v) {
                        observer = value.getViewTreeObserver();
                        observer.addOnPreDrawListener(fitted);
                    }

                    public void onViewDetachedFromWindow(View v) {
                        if (observer.isAlive()) observer.removeOnPreDrawListener(fitted);
                    }
                });
    }

    void render(
            LinearLayout stackRows,
            Display display,
            LocaleNumbers numbers,
            SortedSet<Integer> selectedRows,
            boolean followBottom) {
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
            digits(label, RadixMode.DECIMAL);
            label.setSingleLine(true);
            updateStackLabel(label, depth, display.rows.get(i));
            label.setTag("stack-label:" + i);
            if (!display.editing) label.setOnClickListener(v -> actions.toggle(index, display));
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
            digits(value, numbers.radixMode());
            value.setGravity(Gravity.END);
            value.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
            // Fit from a bounded first layout. A huge exact fraction must never briefly
            // expand the stack before the viewport-aware fitter chooses its fallback.
            String initial = result ? display.compactRows.get(i) : display.rows.get(i);
            value.setText(numbers.stack(withoutApproximation(initial), colors.muted));
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (!result) {
                value.setPadding(dp(4), dp(5), dp(4), dp(5));
                value.setSingleLine(true);
                value.setHorizontallyScrolling(false);
                value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                value.setAutoSizeTextTypeUniformWithConfiguration(
                        12, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            }
            if (result)
                fitBottomRow(
                        value, label, row, stackRows, display, numbers, i, depth, followBottom);
            value.setTextIsSelectable(false);
            row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
            value.setTag("stack-value:" + i);
            value.setOnClickListener(v -> actions.toggle(index, display));
            row.setOnClickListener(v -> actions.toggle(index, display));
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
