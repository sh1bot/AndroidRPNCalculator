// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Draws a turned 2 or 3 when the active font lacks the Unicode dozenal digit. */
final class TurnedDigitSpan extends ReplacementSpan {
    private final char glyph;
    private final String fallback;

    TurnedDigitSpan(char glyph) {
        this.glyph = glyph;
        fallback = glyph == '\u218a' ? "2" : "3";
    }

    private boolean supported(Paint paint) {
        return paint.hasGlyph(Character.toString(glyph));
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) paint.getFontMetricsInt(metrics);
        String rendered = supported(paint) ? Character.toString(glyph) : fallback;
        return (int) Math.ceil(paint.measureText(rendered));
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
        if (supported(paint)) {
            canvas.drawText(Character.toString(glyph), x, y, paint);
            return;
        }
        float width = paint.measureText(fallback);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float centerY = y + (metrics.ascent + metrics.descent) / 2;
        canvas.save();
        canvas.rotate(180, x + width / 2, centerY);
        canvas.drawText(fallback, x, y, paint);
        canvas.restore();
    }
}
