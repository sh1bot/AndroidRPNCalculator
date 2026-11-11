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

    ExponentMarkerSpan(String localizedTen) {
        marker = "\u2009×" + localizedTen;
    }

    private Paint markerPaint(Paint paint) {
        Paint result = new Paint(paint);
        result.setTextSize(paint.getTextSize() * MARKER_SCALE);
        result.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return result;
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) paint.getFontMetricsInt(metrics);
        return (int) Math.ceil(markerPaint(paint).measureText(marker));
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
        canvas.drawText(marker, x, y, markerPaint(paint));
    }
}
