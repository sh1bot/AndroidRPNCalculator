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

    private <T extends TextView> T text(T value, int size) {
        value.setTextSize(size);
        value.setTextColor(colors.text);
        value.setPadding(dp(4), dp(6), dp(4), dp(6));
        return value;
    }

    private TextView text(int size) {
        return text(new TextView(context), size);
    }

    private float sp(int size) {
        return android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                size,
                context.getResources().getDisplayMetrics());
    }

    private static void fitWhenSized(View view, java.util.function.BooleanSupplier fitter) {
        final class Watcher
                implements View.OnAttachStateChangeListener,
                        View.OnLayoutChangeListener,
                        android.view.ViewTreeObserver.OnPreDrawListener {
            private android.view.ViewTreeObserver observer;
            private boolean pending;

            private void schedule() {
                if (!view.isAttachedToWindow() || pending) return;
                observer = view.getViewTreeObserver();
                observer.addOnPreDrawListener(this);
                pending = true;
            }

            private void remove() {
                if (pending && observer != null && observer.isAlive())
                    observer.removeOnPreDrawListener(this);
                pending = false;
            }

            @Override
            public void onViewAttachedToWindow(View ignored) {
                schedule();
            }

            @Override
            public void onViewDetachedFromWindow(View ignored) {
                remove();
            }

            @Override
            public void onLayoutChange(
                    View ignored,
                    int left,
                    int top,
                    int right,
                    int bottom,
                    int oldLeft,
                    int oldTop,
                    int oldRight,
                    int oldBottom) {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                    schedule();
            }

            @Override
            public boolean onPreDraw() {
                if (fitter.getAsBoolean()) remove();
                return !pending;
            }
        }
        Watcher watcher = new Watcher();
        view.addOnAttachStateChangeListener(watcher);
        view.addOnLayoutChangeListener(watcher);
        if (view.isAttachedToWindow()) watcher.schedule();
    }

    private void digits(TextView value) {
        value.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        value.setFontFeatureSettings("tnum");
    }

    private void updateStackLabel(TextView label, int depth, boolean approximate) {
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
        return context.getString(
                id, depth, value.toString().replace("\u200b", "").replace("\u2060", ""));
    }

    private int minimumSize(int normal) {
        return (int) Math.ceil(normal * .7);
    }

    private android.text.StaticLayout layout(
            CharSequence text, TextView view, int size, int width) {
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        paint.setTextSize(sp(size));
        return android.text.StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setIncludePad(view.getIncludeFontPadding())
                .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                .setLineSpacing(view.getLineSpacingExtra(), view.getLineSpacingMultiplier())
                .build();
    }

    private boolean fits(android.text.StaticLayout layout, int width, int height) {
        if (layout.getHeight() > height) return false;
        for (int line = 0; line < layout.getLineCount(); line++)
            if (layout.getLineWidth(line) > width) return false;
        return true;
    }

    private int naturalSize(
            FractionTextView value,
            CharSequence text,
            LocaleNumbers.FractionParts fraction,
            int normal,
            int width,
            int height) {
        for (int size = normal; size >= minimumSize(normal); size--) {
            value.setTextSize(size);
            if (fraction == null) {
                if (android.text.Layout.getDesiredWidth(
                                text, new android.text.TextPaint(value.getPaint()))
                        <= width) return size;
            } else {
                FractionTextView.StackedMeasure measure =
                        value.measureFraction(fraction, width, false);
                if (measure.componentsFit()
                        && (height == 0 || value.measuredHeight(measure) <= height)) return size;
            }
        }
        return 0;
    }

    private static final class RowFit {
        final CharSequence text;
        final int size;
        final boolean approximate;

        RowFit(CharSequence text, int size, boolean approximate) {
            this.text = text;
            this.size = size;
            this.approximate = approximate;
        }
    }

    private RowFit exponential(
            Display display,
            LocaleNumbers numbers,
            int index,
            TextView view,
            int normalSize,
            int width) {
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        FormattedValue[] formatted = new FormattedValue[12];
        CharSequence[] text = new CharSequence[12];
        for (int digits = 1; digits <= 12; digits++) {
            formatted[digits - 1] =
                    ValueFormatter.exponentialPresentation(
                            display.values.get(index), display.notation, digits);
            text[digits - 1] = numbers.stack(formatted[digits - 1], colors.muted);
        }
        for (int size = normalSize; size >= minimumSize(normalSize); size--) {
            paint.setTextSize(sp(size));
            for (int digits = 12; digits >= 1; digits--) {
                if (android.text.Layout.getDesiredWidth(text[digits - 1], paint) <= width)
                    return new RowFit(
                            text[digits - 1], size, formatted[digits - 1].approximate);
            }
        }
        return new RowFit(text[0], minimumSize(normalSize), formatted[0].approximate);
    }

    private void fitBottomRow(
            FractionTextView value,
            TextView label,
            LinearLayout row,
            LinearLayout stackRows,
            Display display,
            LocaleNumbers numbers,
            int index,
            int depth,
            boolean followBottom) {
        View viewport = (View) stackRows.getParent();
        FormattedValue fullValue = display.fullPresentations.get(index);
        CharSequence full = numbers.stack(fullValue, colors.muted);
        CharSequence breakable = numbers.breakable(full);
        LocaleNumbers.FractionParts fraction =
                numbers.fractionParts(fullValue, colors.muted);
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
                    int usableHeight = height - value.getPaddingTop() - value.getPaddingBottom();
                    int size = naturalSize(value, full, fraction, 32, width, height);
                    boolean fullChosen = size != 0, stacked = fraction != null, split = false;
                    CharSequence multiline = null;
                    if (!fullChosen) {
                        for (size = 32; size >= minimumSize(32) && !fullChosen; size--) {
                            value.setTextSize(size);
                            if (fraction != null) {
                                split = fraction.mixed();
                                FractionTextView.StackedMeasure measure =
                                        value.measureFraction(fraction, width, split);
                                if (value.measuredHeight(measure) <= height)
                                    fullChosen = stacked = true;
                            } else {
                                android.text.SpannableStringBuilder pointFirst =
                                        new android.text.SpannableStringBuilder(full);
                                int point = pointFirst.toString().indexOf(numbers.decimalSeparator());
                                if (point >= 0) {
                                    pointFirst.insert(point + 1, "\n");
                                    android.text.StaticLayout pointLayout =
                                            layout(pointFirst, value, size, width);
                                    if (fits(pointLayout, width, usableHeight)) {
                                        multiline = pointFirst;
                                        fullChosen = true;
                                    }
                                }
                                if (!fullChosen) {
                                    android.text.StaticLayout grouped =
                                            layout(breakable, value, size, width);
                                    if (grouped.getLineCount() > 1
                                            && fits(grouped, width, usableHeight)) {
                                        multiline = breakable;
                                        fullChosen = true;
                                    }
                                }
                            }
                            if (fullChosen) break;
                        }
                    }
                    CharSequence chosen;
                    boolean approximate;
                    if (fullChosen) {
                        chosen = multiline == null ? full : multiline;
                        approximate = fullValue.approximate;
                        value.setTextSize(size);
                        value.setMaxLines(Integer.MAX_VALUE);
                        if (stacked) value.stackedFraction(fraction, full, split);
                        else value.inlineText(chosen);
                    } else {
                        RowFit compact = exponential(display, numbers, index, value, 32, width);
                        chosen = compact.text;
                        approximate = compact.approximate;
                        value.setTextSize(compact.size);
                        value.setMaxLines(1);
                        value.inlineText(chosen);
                    }
                    value.setEllipsize(null);
                    updateStackLabel(label, depth, approximate);
                    value.requestLayout();
                    row.setContentDescription(
                            rowDescription(
                                    false,
                                    depth,
                                    approximate,
                                    chosen));
                };
        // Fit before drawing, and let a requested relayout finish before showing the row.
        // This avoids painting either the unbounded value or a stale font size for a frame.
        boolean[] pendingFollow = {followBottom};
        java.util.function.BooleanSupplier fitted =
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
        fitWhenSized(value, fitted);
    }

    private void fitUpperRow(
            FractionTextView value,
            TextView label,
            LinearLayout row,
            Display display,
            LocaleNumbers numbers,
            int index,
            int depth) {
        FormattedValue fullValue = display.fullPresentations.get(index);
        CharSequence full = numbers.stack(fullValue, colors.muted);
        LocaleNumbers.FractionParts fraction =
                numbers.fractionParts(fullValue, colors.muted);
        int[] previousWidth = {-1};
        Runnable fit =
                () -> {
                    int width = value.getWidth() - value.getPaddingLeft() - value.getPaddingRight();
                    if (width <= 0 || width == previousWidth[0]) return;
                    previousWidth[0] = width;
                    int fullSize = naturalSize(value, full, fraction, 22, width, 0);
                    boolean fullChosen = fullSize != 0;
                    CharSequence chosen;
                    boolean approximate;
                    if (fullChosen) {
                        chosen = full;
                        approximate = fullValue.approximate;
                        value.setTextSize(fullSize);
                        value.setMaxLines(fraction == null ? 1 : Integer.MAX_VALUE);
                        if (fraction != null) value.stackedFraction(fraction, full);
                        else value.inlineText(full);
                    } else {
                        RowFit compact = exponential(display, numbers, index, value, 22, width);
                        chosen = compact.text;
                        approximate = compact.approximate;
                        value.setTextSize(compact.size);
                        value.setMaxLines(1);
                        value.inlineText(chosen);
                    }
                    value.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
                    value.setEllipsize(null);
                    updateStackLabel(label, depth, approximate);
                    row.setContentDescription(
                            rowDescription(
                                    false,
                                    depth,
                                    approximate,
                                    chosen));
                };
        java.util.function.BooleanSupplier fitted =
                () -> {
                    fit.run();
                    return !value.isLayoutRequested();
                };
        fitWhenSized(value, fitted);
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
            digits(label);
            label.setSingleLine(true);
            updateStackLabel(label, depth, display.presentations.get(i).approximate);
            label.setTag("stack-label:" + i);
            if (!display.editing) label.setOnClickListener(v -> actions.toggle(index, display));
            label.setOnLongClickListener(
                    v -> {
                        actions.copy(index, display);
                        return true;
                    });
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setTextColor(colors.muted);
            boolean result = !display.editing && depth == 1;
            FractionTextView value = text(new FractionTextView(context), result ? 32 : 22);
            digits(value);
            label.setPadding(label.getPaddingLeft(), label.getPaddingTop(), 0, label.getPaddingBottom());
            int labelWidth =
                    label.getPaddingLeft()
                            + (int) Math.ceil(label.getPaint().measureText(label.getText().toString()));
            row.addView(label, new LinearLayout.LayoutParams(labelWidth, -2));
            int labelGap = (int) Math.ceil(label.getPaint().measureText("\u2002"));
            value.setPadding(
                    labelGap,
                    value.getPaddingTop(),
                    value.getPaddingRight(),
                    value.getPaddingBottom());
            value.setGravity(Gravity.END);
            value.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
            // Fit from a bounded first layout. A huge exact fraction must never briefly
            // expand the stack before the viewport-aware fitter chooses its fallback.
            FormattedValue initial =
                    result ? display.compactPresentations.get(i) : display.presentations.get(i);
            CharSequence initialText = numbers.stack(initial, colors.muted);
            value.inlineText(initialText);
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (!result) {
                value.setPadding(labelGap, dp(5), dp(4), dp(5));
                value.setHorizontallyScrolling(false);
                fitUpperRow(value, label, row, display, numbers, i, depth);
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
                            display.presentations.get(i).approximate,
                            value.getText()));
            if (display.editing)
                label.setContentDescription(
                        rowDescription(
                                true,
                                depth,
                                display.presentations.get(i).approximate,
                                value.getText()));
            stackRows.addView(row);
        }
        updateSelection(stackRows, selectedRows);
    }

    void updateSelection(LinearLayout stackRows, SortedSet<Integer> selectedRows) {
        for (int i = 0; i < stackRows.getChildCount(); i++) {
            View row = stackRows.getChildAt(i);
            if (!("stack-row:" + i).equals(row.getTag())) continue;
            boolean highlighted = selectedRows.contains(i);
            View checkbox = row.findViewWithTag("stack-checkbox:" + i);
            if (checkbox instanceof CheckBox) ((CheckBox) checkbox).setChecked(highlighted);
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
