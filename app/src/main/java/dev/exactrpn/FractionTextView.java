// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;

/** A TextView which can draw a stacked fraction with a real horizontal fraction bar. */
final class FractionTextView extends android.widget.TextView {
    static final float FRACTION_SCALE = .72f;
    private LocaleNumbers.FractionParts fraction;
    private StackedMeasure stacked;

    static final class StackedMeasure {
        final StaticLayout whole, numerator, denominator;
        final int contentWidth, leftWidth, rightWidth, gap;

        StackedMeasure(
                StaticLayout whole,
                StaticLayout numerator,
                StaticLayout denominator,
                int contentWidth,
                int leftWidth,
                int rightWidth,
                int gap) {
            this.whole = whole;
            this.numerator = numerator;
            this.denominator = denominator;
            this.contentWidth = contentWidth;
            this.leftWidth = leftWidth;
            this.rightWidth = rightWidth;
            this.gap = gap;
        }
    }

    FractionTextView(Context context) {
        super(context);
    }

    FractionTextView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    void stackedFraction(LocaleNumbers.FractionParts parts, CharSequence accessibleText) {
        fraction = parts;
        stacked = null;
        setText(accessibleText);
        requestLayout();
        invalidate();
    }

    void inlineText(CharSequence text) {
        fraction = null;
        stacked = null;
        setText(text);
        requestLayout();
        invalidate();
    }

    boolean isStackedFraction() {
        return fraction != null;
    }

    StackedMeasure measureFraction(LocaleNumbers.FractionParts parts, int width) {
        int contentWidth = Math.max(1, width);
        int gap = dp(8);
        TextPaint paint = new TextPaint(getPaint());
        paint.setColor(getCurrentTextColor());
        TextPaint fractionPaint = new TextPaint(paint);
        fractionPaint.setTextSize(paint.getTextSize() * FRACTION_SCALE);
        StaticLayout whole = null;
        int leftWidth;
        int rightWidth;
        if (parts.mixed()) {
            int wholeWanted = (int) Math.ceil(Layout.getDesiredWidth(parts.whole, paint));
            int fractionWanted =
                    (int)
                            Math.ceil(
                                    Math.max(
                                            Layout.getDesiredWidth(parts.numerator, fractionPaint),
                                            Layout.getDesiredWidth(parts.denominator, fractionPaint)));
            int shared = Math.max(2, contentWidth - gap);
            leftWidth = Math.max(1, Math.min(shared - 1, wholeWanted));
            rightWidth = Math.max(1, shared - leftWidth);
            if (fractionWanted < rightWidth) rightWidth = fractionWanted;
            whole = layout(parts.whole, leftWidth, paint);
        } else {
            leftWidth = 0;
            rightWidth = contentWidth;
        }
        return new StackedMeasure(
                whole,
                layout(parts.numerator, rightWidth, fractionPaint),
                layout(parts.denominator, rightWidth, fractionPaint),
                contentWidth, leftWidth, rightWidth, gap);
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

    private int fractionHeight(StackedMeasure measure) {
        return measure.numerator.getHeight() + measure.denominator.getHeight() + dp(6);
    }

    int measuredHeight(StackedMeasure measure) {
        int height = fractionHeight(measure);
        if (measure.whole != null) height = Math.max(height, measure.whole.getHeight());
        return height + getPaddingTop() + getPaddingBottom();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (fraction == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        stacked = measureFraction(fraction, available);
        int desired = measuredHeight(stacked);
        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec), resolveSize(desired, heightMeasureSpec));
    }

    private static float widest(StaticLayout layout) {
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
        if (stacked == null || available != stacked.contentWidth)
            stacked = measureFraction(fraction, available);
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        int totalHeight = measuredHeight(stacked) - getPaddingTop() - getPaddingBottom();
        float wholeWidth = stacked.whole == null ? 0 : widest(stacked.whole);
        float fractionWidth = Math.max(widest(stacked.numerator), widest(stacked.denominator));
        float blockLeft =
                stacked.whole == null
                        ? 0
                        : Math.max(
                                0,
                                stacked.contentWidth
                                        - wholeWidth
                                        - stacked.gap
                                        - fractionWidth);
        if (stacked.whole != null) {
            canvas.save();
            canvas.translate(
                    blockLeft + wholeWidth - stacked.leftWidth,
                    (totalHeight - stacked.whole.getHeight()) / 2f);
            stacked.whole.draw(canvas);
            canvas.restore();
        }
        float fractionLeft =
                stacked.whole == null
                        ? 0
                        : blockLeft
                                + wholeWidth
                                + stacked.gap
                                + fractionWidth
                                - stacked.rightWidth;
        float fractionTop = (totalHeight - fractionHeight(stacked)) / 2f;
        canvas.save();
        canvas.translate(fractionLeft, fractionTop);
        stacked.numerator.draw(canvas);
        float lineY = stacked.numerator.getHeight() + dp(3);
        float lineWidth = Math.max(widest(stacked.numerator), widest(stacked.denominator));
        Paint line = new Paint(getPaint());
        line.setColor(getCurrentTextColor());
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1, dp(1)));
        canvas.drawLine(
                stacked.rightWidth - lineWidth, lineY, stacked.rightWidth, lineY, line);
        canvas.translate(0, stacked.numerator.getHeight() + dp(6));
        stacked.denominator.draw(canvas);
        canvas.restore();
        canvas.restore();
    }
}
