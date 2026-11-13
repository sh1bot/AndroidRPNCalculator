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

    private float cellWidth(Paint paint) {
        return paint.measureText("0");
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) paint.getFontMetricsInt(metrics);
        return (int) Math.ceil(cellWidth(paint));
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
            String rendered = Character.toString(glyph);
            int baseline = GroupingSeparatorSpan.baseline(paint, y);
            canvas.drawText(
                    rendered,
                    x + (cellWidth(paint) - paint.measureText(rendered)) / 2,
                    baseline,
                    paint);
            return;
        }
        int baseline = GroupingSeparatorSpan.baseline(paint, y);
        float width = cellWidth(paint), glyphWidth = paint.measureText(fallback);
        float offset = (width - glyphWidth) / 2;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float centerY = baseline + (metrics.ascent + metrics.descent) / 2;
        canvas.save();
        canvas.rotate(180, x + width / 2, centerY);
        canvas.drawText(fallback, x + offset, baseline, paint);
        canvas.restore();
    }
}
