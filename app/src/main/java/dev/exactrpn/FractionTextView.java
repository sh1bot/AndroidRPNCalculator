// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;

/** A TextView which can draw a wrapped fraction with a real horizontal fraction bar. */
final class FractionTextView extends android.widget.TextView {
    private static final float FRACTION_SCALE = .72f;
    private LocaleNumbers.FractionParts fraction;
    private StaticLayout wholeLayout, numeratorLayout, denominatorLayout;
    private int contentWidth, leftWidth, rightWidth, fractionGap;
    private boolean splitWhole;

    FractionTextView(Context context) {
        super(context);
    }

    FractionTextView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    void stackedFraction(LocaleNumbers.FractionParts parts, CharSequence accessibleText) {
        stackedFraction(parts, accessibleText, false);
    }

    void stackedFraction(
            LocaleNumbers.FractionParts parts, CharSequence accessibleText, boolean splitWhole) {
        fraction = parts;
        this.splitWhole = splitWhole;
        setText(accessibleText);
        requestLayout();
        invalidate();
    }

    void inlineText(CharSequence text) {
        fraction = null;
        splitWhole = false;
        wholeLayout = numeratorLayout = denominatorLayout = null;
        setText(text);
        requestLayout();
        invalidate();
    }

    boolean isStackedFraction() {
        return fraction != null;
    }

    int stackedHeight(LocaleNumbers.FractionParts parts, int width) {
        return stackedHeight(parts, width, false);
    }

    int stackedHeight(LocaleNumbers.FractionParts parts, int width, boolean splitWhole) {
        LocaleNumbers.FractionParts previous = fraction;
        boolean previousSplit = this.splitWhole;
        fraction = parts;
        this.splitWhole = splitWhole;
        buildLayouts(Math.max(1, width));
        int height = desiredContentHeight();
        fraction = previous;
        this.splitWhole = previousSplit;
        return height + getPaddingTop() + getPaddingBottom();
    }

    boolean stackedComponentsFit(LocaleNumbers.FractionParts parts, int width) {
        LocaleNumbers.FractionParts previous = fraction;
        boolean previousSplit = splitWhole;
        fraction = parts;
        splitWhole = false;
        buildLayouts(Math.max(1, width));
        boolean fits =
                (wholeLayout == null || oneLineFits(wholeLayout, leftWidth))
                        && oneLineFits(numeratorLayout, rightWidth)
                        && oneLineFits(denominatorLayout, rightWidth);
        fraction = previous;
        splitWhole = previousSplit;
        return fits;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private StaticLayout layout(CharSequence text, int width, TextPaint paint) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setIncludePad(getIncludeFontPadding())
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setLineSpacing(getLineSpacingExtra(), getLineSpacingMultiplier())
                .build();
    }

    private void buildLayouts(int width) {
        if (fraction == null) return;
        contentWidth = Math.max(1, width);
        fractionGap = dp(8);
        TextPaint paint = new TextPaint(getPaint());
        // TextView normally resolves its ColorStateList immediately before its own draw pass.
        // These independent layouts bypass that pass, so carry the resolved theme colour in
        // their base paint explicitly.
        paint.setColor(getCurrentTextColor());
        TextPaint fractionPaint = new TextPaint(paint);
        fractionPaint.setTextSize(paint.getTextSize() * FRACTION_SCALE);
        if (fraction.mixed()) {
            int wholeWanted = (int) Math.ceil(Layout.getDesiredWidth(fraction.whole, paint));
            int fractionWanted =
                    (int)
                            Math.ceil(
                                    Math.max(
                                            Layout.getDesiredWidth(
                                                    fraction.numerator, fractionPaint),
                                            Layout.getDesiredWidth(
                                                    fraction.denominator, fractionPaint)));
            if (splitWhole) {
                leftWidth = rightWidth = contentWidth;
            } else {
                int shared = Math.max(2, contentWidth - fractionGap);
                leftWidth = Math.max(1, Math.min(shared - 1, wholeWanted));
                rightWidth = Math.max(1, shared - leftWidth);
                if (fractionWanted < rightWidth) rightWidth = fractionWanted;
            }
            wholeLayout = layout(fraction.whole, leftWidth, paint);
        } else {
            leftWidth = 0;
            rightWidth = contentWidth;
            wholeLayout = null;
        }
        numeratorLayout = layout(fraction.numerator, rightWidth, fractionPaint);
        denominatorLayout = layout(fraction.denominator, rightWidth, fractionPaint);
    }

    private int fractionHeight() {
        return numeratorLayout.getHeight() + denominatorLayout.getHeight() + dp(6);
    }

    private int desiredContentHeight() {
        int height = fractionHeight();
        if (splitWhole && wholeLayout != null) return wholeLayout.getHeight() + dp(4) + height;
        return wholeLayout == null ? height : Math.max(height, wholeLayout.getHeight());
    }

    private boolean oneLineFits(StaticLayout layout, int width) {
        return layout.getLineCount() == 1 && widest(layout) <= width;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (fraction == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        buildLayouts(available);
        int desired = desiredContentHeight() + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec), resolveSize(desired, heightMeasureSpec));
    }

    private float widest(StaticLayout layout) {
        float width = 0;
        for (int line = 0; line < layout.getLineCount(); line++)
            width = Math.max(width, layout.getLineWidth(line));
        return width;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (fraction == null) {
            super.onDraw(canvas);
            return;
        }
        int available = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        if (available != contentWidth) buildLayouts(available);
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        int totalHeight = desiredContentHeight();
        float wholeWidth = wholeLayout == null ? 0 : widest(wholeLayout);
        float fractionWidth = Math.max(widest(numeratorLayout), widest(denominatorLayout));
        if (splitWhole && wholeLayout != null) {
            wholeLayout.draw(canvas);
            canvas.translate(0, wholeLayout.getHeight() + dp(4));
            totalHeight -= wholeLayout.getHeight() + dp(4);
        }
        float blockLeft =
                wholeLayout == null || splitWhole
                        ? 0
                        : Math.max(0, contentWidth - wholeWidth - fractionGap - fractionWidth);
        if (wholeLayout != null && !splitWhole) {
            canvas.save();
            canvas.translate(
                    blockLeft + wholeWidth - leftWidth,
                    (totalHeight - wholeLayout.getHeight()) / 2f);
            wholeLayout.draw(canvas);
            canvas.restore();
        }
        float fractionLeft =
                wholeLayout == null || splitWhole
                        ? 0
                        : blockLeft + wholeWidth + fractionGap + fractionWidth - rightWidth;
        float fractionTop = (totalHeight - fractionHeight()) / 2f;
        canvas.save();
        canvas.translate(fractionLeft, fractionTop);
        numeratorLayout.draw(canvas);
        float lineY = numeratorLayout.getHeight() + dp(3);
        float lineWidth = Math.max(widest(numeratorLayout), widest(denominatorLayout));
        Paint line = new Paint(getPaint());
        line.setColor(getCurrentTextColor());
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1, dp(1)));
        canvas.drawLine(rightWidth - lineWidth, lineY, rightWidth, lineY, line);
        canvas.translate(0, numeratorLayout.getHeight() + dp(6));
        denominatorLayout.draw(canvas);
        canvas.restore();
        canvas.restore();
    }
}
