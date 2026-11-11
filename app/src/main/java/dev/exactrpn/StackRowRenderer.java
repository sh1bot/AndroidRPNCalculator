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

    private static void fitWhileAttached(
            View view, android.view.ViewTreeObserver.OnPreDrawListener fitter) {
        view.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    private android.view.ViewTreeObserver observer;

                    public void onViewAttachedToWindow(View ignored) {
                        observer = view.getViewTreeObserver();
                        observer.addOnPreDrawListener(fitter);
                    }

                    public void onViewDetachedFromWindow(View ignored) {
                        if (observer.isAlive()) observer.removeOnPreDrawListener(fitter);
                    }
                });
    }

    private void digits(TextView value) {
        value.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
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
            if (fraction == null
                    ? android.text.Layout.getDesiredWidth(
                                    text, new android.text.TextPaint(value.getPaint()))
                            <= width
                    : value.stackedComponentsFit(fraction, width)
                            && (height == 0 || value.stackedHeight(fraction, width) <= height))
                return size;
        }
        return 0;
    }

    private static final class RowFit {
        final String canonical;
        final CharSequence text;
        final int size;

        RowFit(String canonical, CharSequence text, int size) {
            this.canonical = canonical;
            this.text = text;
            this.size = size;
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
        for (int size = normalSize; size >= minimumSize(normalSize); size--) {
            paint.setTextSize(sp(size));
            for (int digits = 12; digits >= 1; digits--) {
                String canonical =
                        ValueFormatter.exponential(
                                display.values.get(index), display.notation, digits);
                CharSequence text =
                        numbers.stack(withoutApproximation(canonical), colors.muted);
                if (android.text.Layout.getDesiredWidth(text, paint) <= width)
                    return new RowFit(canonical, text, size);
            }
        }
        String canonical =
                ValueFormatter.exponential(display.values.get(index), display.notation, 1);
        return new RowFit(
                canonical,
                numbers.stack(withoutApproximation(canonical), colors.muted),
                minimumSize(normalSize));
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
        String fullNumber = display.fullRows.get(index);
        CharSequence full = numbers.stack(withoutApproximation(fullNumber), colors.muted);
        CharSequence breakable = numbers.breakable(full);
        LocaleNumbers.FractionParts fraction =
                numbers.fractionParts(withoutApproximation(fullNumber), colors.muted);
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
                                if (value.stackedHeight(fraction, width, split) <= height)
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
                    String chosenNumber;
                    CharSequence chosen;
                    if (fullChosen) {
                        chosenNumber = fullNumber;
                        chosen = multiline == null ? full : multiline;
                        value.setTextSize(size);
                        value.setMaxLines(Integer.MAX_VALUE);
                        if (stacked) value.stackedFraction(fraction, full, split);
                        else value.inlineText(chosen);
                    } else {
                        RowFit compact = exponential(display, numbers, index, value, 32, width);
                        chosenNumber = compact.canonical;
                        chosen = compact.text;
                        value.setTextSize(compact.size);
                        value.setMaxLines(1);
                        value.inlineText(chosen);
                    }
                    value.setEllipsize(null);
                    updateStackLabel(label, depth, chosenNumber);
                    value.requestLayout();
                    row.setContentDescription(
                            rowDescription(
                                    false,
                                    depth,
                                    chosenNumber.startsWith("≈ "),
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
        fitWhileAttached(value, fitted);
    }

    private void fitUpperRow(
            FractionTextView value,
            TextView label,
            LinearLayout row,
            Display display,
            LocaleNumbers numbers,
            int index,
            int depth) {
        String fullNumber = display.fullRows.get(index);
        CharSequence full = numbers.stack(withoutApproximation(fullNumber), colors.muted);
        LocaleNumbers.FractionParts fraction =
                numbers.fractionParts(withoutApproximation(fullNumber), colors.muted);
        int[] previousWidth = {-1};
        Runnable fit =
                () -> {
                    int width = value.getWidth() - value.getPaddingLeft() - value.getPaddingRight();
                    if (width <= 0 || width == previousWidth[0]) return;
                    previousWidth[0] = width;
                    int fullSize = naturalSize(value, full, fraction, 22, width, 0);
                    boolean fullChosen = fullSize != 0;
                    String chosenNumber;
                    CharSequence chosen;
                    if (fullChosen) {
                        chosenNumber = fullNumber;
                        chosen = full;
                        value.setTextSize(fullSize);
                        value.setMaxLines(fraction == null ? 1 : Integer.MAX_VALUE);
                        if (fraction != null) value.stackedFraction(fraction, full);
                        else value.inlineText(full);
                    } else {
                        RowFit compact = exponential(display, numbers, index, value, 22, width);
                        chosenNumber = compact.canonical;
                        chosen = compact.text;
                        value.setTextSize(compact.size);
                        value.setMaxLines(1);
                        value.inlineText(chosen);
                    }
                    value.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
                    value.setEllipsize(null);
                    updateStackLabel(label, depth, chosenNumber);
                    row.setContentDescription(
                            rowDescription(
                                    false,
                                    depth,
                                    chosenNumber.startsWith("≈ "),
                                    chosen));
                };
        android.view.ViewTreeObserver.OnPreDrawListener fitted =
                () -> {
                    fit.run();
                    return !value.isLayoutRequested();
                };
        fitWhileAttached(value, fitted);
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
            String initial = result ? display.compactRows.get(i) : display.rows.get(i);
            CharSequence initialText =
                    numbers.stack(withoutApproximation(initial), colors.muted);
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
