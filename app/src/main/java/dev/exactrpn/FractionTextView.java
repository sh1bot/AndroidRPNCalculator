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
    private LocaleNumbers.FractionParts fraction;
    private StaticLayout wholeLayout, numeratorLayout, denominatorLayout;
    private int contentWidth, contentOffset, leftWidth, rightWidth, fractionGap;

    FractionTextView(Context context) {
        super(context);
    }

    FractionTextView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    void stackedFraction(LocaleNumbers.FractionParts parts, CharSequence accessibleText) {
        fraction = parts;
        setText(accessibleText);
        requestLayout();
        invalidate();
    }

    void inlineText(CharSequence text) {
        fraction = null;
        wholeLayout = numeratorLayout = denominatorLayout = null;
        setText(text);
        requestLayout();
        invalidate();
    }

    boolean isStackedFraction() {
        return fraction != null;
    }

    int stackedHeight(LocaleNumbers.FractionParts parts, int width) {
        LocaleNumbers.FractionParts previous = fraction;
        fraction = parts;
        buildLayouts(Math.max(1, width));
        int height = desiredContentHeight();
        fraction = previous;
        return height + getPaddingTop() + getPaddingBottom();
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
        if (fraction.mixed()) {
            int shared = Math.max(2, contentWidth - fractionGap);
            int half = shared / 2;
            int wholeWanted = (int) Math.ceil(Layout.getDesiredWidth(fraction.whole, paint));
            int fractionWanted =
                    (int)
                            Math.ceil(
                                    Math.max(
                                            Layout.getDesiredWidth(fraction.numerator, paint),
                                            Layout.getDesiredWidth(fraction.denominator, paint)));
            if (wholeWanted < half && fractionWanted < half) {
                leftWidth = Math.max(1, wholeWanted);
                rightWidth = Math.max(1, fractionWanted);
                contentOffset = Math.max(0, shared - leftWidth - rightWidth);
            } else {
                if (wholeWanted < half) leftWidth = Math.max(1, wholeWanted);
                else if (fractionWanted < half)
                    leftWidth = Math.max(1, shared - Math.max(1, fractionWanted));
                else leftWidth = half;
                rightWidth = Math.max(1, shared - leftWidth);
                contentOffset = 0;
            }
            wholeLayout = layout(fraction.whole, leftWidth, paint);
        } else {
            contentOffset = 0;
            leftWidth = 0;
            rightWidth = contentWidth;
            wholeLayout = null;
        }
        numeratorLayout = layout(fraction.numerator, rightWidth, paint);
        denominatorLayout = layout(fraction.denominator, rightWidth, paint);
    }

    private int fractionHeight() {
        return numeratorLayout.getHeight() + denominatorLayout.getHeight() + dp(6);
    }

    private int desiredContentHeight() {
        int height = fractionHeight();
        return wholeLayout == null ? height : Math.max(height, wholeLayout.getHeight());
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
        if (wholeLayout != null) {
            canvas.save();
            canvas.translate(contentOffset, (totalHeight - wholeLayout.getHeight()) / 2f);
            wholeLayout.draw(canvas);
            canvas.restore();
        }
        float fractionLeft =
                wholeLayout == null ? 0 : contentOffset + leftWidth + fractionGap;
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
