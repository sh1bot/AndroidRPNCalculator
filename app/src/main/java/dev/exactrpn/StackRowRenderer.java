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

    // Nominal sizes: x is larger than the other rows, but every row shares
    // one minimum -- 80% of the ordinary rows' nominal size. A row may
    // shrink to that floor, but no further: below it the chosen format is
    // abandoned for exponential instead of shrinking on.
    private static final int ROW_SIZE = 22, RESULT_SIZE = 32;
    private static final int MINIMUM_SIZE = (int) Math.ceil(ROW_SIZE * .8);

    private float desiredWidth(CharSequence text, TextView view, int size) {
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        paint.setTextSize(sp(size));
        return android.text.Layout.getDesiredWidth(text, paint);
    }

    // Single-line width of a stacked fraction at the given size: the whole
    // part and the wider of numerator and denominator, plus the fixed gap
    // between them. The fraction parts themselves scale with the size.
    private float fractionWidth(LocaleNumbers.FractionParts fraction, TextView view, int size) {
        float whole = fraction.mixed() ? desiredWidth(fraction.whole, view, size) : 0;
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        paint.setTextSize(sp(size) * FractionTextView.FRACTION_SCALE);
        float part =
                Math.max(
                        android.text.Layout.getDesiredWidth(fraction.numerator, paint),
                        android.text.Layout.getDesiredWidth(fraction.denominator, paint));
        return whole + (fraction.mixed() ? dp(8) : 0) + part;
    }

    // Text widths scale linearly with font size (the font is monospaced), so
    // the largest size that fits is a direct calculation, not a search: only
    // the fixed gap between a mixed fraction's parts does not scale.
    private int maximisedSize(int normal, int width, float widthAtMinimum, float fixed) {
        int minimum = MINIMUM_SIZE;
        float scalable = widthAtMinimum - fixed;
        if (scalable <= 0) return normal;
        int size = (int) (minimum * (width - fixed) / scalable);
        return Math.max(minimum, Math.min(normal, size));
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

    // The most digits the codebase ever treats as meaningful; see the
    // 40-digit clipboard representation in ValueFormatter.
    private static final int MAX_EXPONENTIAL_DIGITS = 40;

    private RowFit exponential(
            Display display,
            LocaleNumbers numbers,
            int index,
            TextView view,
            int normalSize,
            int width) {
        // One probe at full precision supplies the sign and exponent, whose
        // lengths do not depend on how many mantissa digits are kept.
        FormattedValue probe =
                ValueFormatter.exponentialPresentation(
                        display.values.get(index), display.notation, MAX_EXPONENTIAL_DIGITS);
        CharSequence probeText = numbers.stack(probe, colors.muted);
        if (probe.kind != FormattedValue.Kind.EXPONENTIAL)
            return new RowFit(probeText, MINIMUM_SIZE, probe.approximate);
        // The font is monospaced, so the digit budget is arithmetic: count
        // the cells that fit at the nominal size, then subtract the cells
        // the fixed parts occupy. The marker (" ×10") is drawn at 75% size
        // and the exponent at 72%, and one spare cell absorbs a rounding
        // carry growing the exponent (9.9…e99 rounding up to 1.0…e100).
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        paint.setTextSize(sp(normalSize));
        int cells = (int) (width / paint.measureText("0")) - 1;
        int suffixCells = 3 + (int) Math.ceil(probe.exponent.length() * .72f);
        int signCells = probe.whole.startsWith("-") ? 1 : 0;
        int digits = cells - suffixCells - signCells - 1; // one cell for the point
        digits = Math.max(1, Math.min(MAX_EXPONENTIAL_DIGITS, digits));
        FormattedValue compact =
                digits == MAX_EXPONENTIAL_DIGITS
                        ? probe
                        : ValueFormatter.exponentialPresentation(
                                display.values.get(index), display.notation, digits);
        CharSequence text = numbers.stack(compact, colors.muted);
        // Truncated to fit at the nominal size; should the span estimates
        // ever undershoot, the minimum size fits the same text by scaling.
        int size =
                android.text.Layout.getDesiredWidth(text, paint) <= width
                        ? normalSize
                        : MINIMUM_SIZE;
        return new RowFit(text, size, compact.approximate);
    }

    // One fitting policy for every row; rows differ only in nominal size.
    // At the shared minimum size the value is tried in its chosen
    // format -- decimal, mixed fraction or improper fraction, as a single
    // line. If it does not fit there it is replaced by an exponential
    // truncated to fit at the nominal size. A value that does fit is then
    // scaled up to the largest size that fits, up to its nominal size.
    private void fitRow(
            FractionTextView value,
            TextView label,
            LinearLayout row,
            LinearLayout stackRows,
            Display display,
            LocaleNumbers numbers,
            int index,
            int depth,
            int normalSize,
            boolean followBottom) {
        View viewport = (View) stackRows.getParent();
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
                    int minimum = MINIMUM_SIZE;
                    float content =
                            fraction != null
                                    ? fractionWidth(fraction, value, minimum)
                                    : desiredWidth(full, value, minimum);
                    CharSequence chosen;
                    boolean approximate;
                    if (content <= width) {
                        float fixed = fraction != null && fraction.mixed() ? dp(8) : 0;
                        int size = maximisedSize(normalSize, width, content, fixed);
                        chosen = full;
                        approximate = fullValue.approximate;
                        value.setTextSize(size);
                        if (fraction != null) value.stackedFraction(fraction, full);
                        else value.inlineText(chosen);
                    } else {
                        RowFit compact =
                                exponential(display, numbers, index, value, normalSize, width);
                        chosen = compact.text;
                        approximate = compact.approximate;
                        value.setTextSize(compact.size);
                        value.inlineText(chosen);
                    }
                    // Single line, never word-wrapped: a value that cannot
                    // fit on one line has already been replaced above, so a
                    // small measurement error cannot wrap it by surprise.
                    value.setSingleLine(true);
                    value.setMaxLines(1);
                    value.setEllipsize(null);
                    updateStackLabel(label, depth, approximate);
                    row.setContentDescription(
                            rowDescription(false, depth, approximate, chosen));
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
            View.OnClickListener toggle = v -> actions.toggle(index, display);
            View.OnLongClickListener copy =
                    v -> {
                        actions.copy(index, display);
                        return true;
                    };
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
                selected.setOnLongClickListener(copy);
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
            if (!display.editing) label.setOnClickListener(toggle);
            label.setOnLongClickListener(copy);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setTextColor(colors.muted);
            boolean result = !display.editing && depth == 1;
            FractionTextView value = text(new FractionTextView(context), result ? RESULT_SIZE : ROW_SIZE);
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
            value.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            // Fit from a bounded first layout. A huge exact fraction must never briefly
            // expand the stack before the fitter chooses its fallback.
            FormattedValue initial =
                    result ? display.compactPresentations.get(i) : display.presentations.get(i);
            CharSequence initialText = numbers.stack(initial, colors.muted);
            value.inlineText(initialText);
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (!result) value.setPadding(labelGap, dp(5), dp(4), dp(5));
            value.setHorizontallyScrolling(false);
            fitRow(
                    value,
                    label,
                    row,
                    stackRows,
                    display,
                    numbers,
                    i,
                    depth,
                    result ? RESULT_SIZE : ROW_SIZE,
                    result && followBottom);
            value.setTextIsSelectable(false);
            row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
            value.setTag("stack-value:" + i);
            value.setOnClickListener(toggle);
            row.setOnClickListener(toggle);
            value.setOnLongClickListener(copy);
            row.setOnLongClickListener(copy);
            String description =
                    rowDescription(
                            display.editing,
                            depth,
                            display.presentations.get(i).approximate,
                            value.getText());
            row.setContentDescription(description);
            if (display.editing) label.setContentDescription(description);
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
