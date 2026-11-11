// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;

/** Render the single editable e/E character as a small multiplication-by-ten marker. */
final class ExponentMarkerSpan extends ReplacementSpan {
    static final float MARKER_SCALE = .75f, INDEX_SCALE = .72f;
    private final String marker;
    private final int operatorLength;

    ExponentMarkerSpan(String localizedTen) {
        marker = "\u2009×" + localizedTen;
        operatorLength = 2;
    }

    private Paint markerPaint(Paint paint, boolean operator) {
        Paint result = new Paint(paint);
        result.setTextSize(paint.getTextSize() * MARKER_SCALE);
        if (operator) result.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return result;
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) paint.getFontMetricsInt(metrics);
        Paint operator = markerPaint(paint, true), digits = markerPaint(paint, false);
        return (int)
                Math.ceil(
                        operator.measureText(marker, 0, operatorLength)
                                + digits.measureText(marker, operatorLength, marker.length()));
    }

    @Override
    public void draw(
            Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            Paint paint) {
        Paint operator = markerPaint(paint, true), digits = markerPaint(paint, false);
        canvas.drawText(marker, 0, operatorLength, x, y, operator);
        canvas.drawText(
                marker,
                operatorLength,
                marker.length(),
                x + operator.measureText(marker, 0, operatorLength),
                y,
                digits);
    }
}
