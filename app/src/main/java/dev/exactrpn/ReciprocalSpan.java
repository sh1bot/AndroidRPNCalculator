// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** A special fraction with controlled placement, independent of font fraction shaping. */
final class ReciprocalSpan extends ReplacementSpan {
    private float width(Paint paint) {
        Paint digits = new Paint(paint);
        digits.setTextSize(paint.getTextSize() * .7f);
        return digits.measureText("1") + paint.measureText("⁄") + digits.measureText("x");
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) paint.getFontMetricsInt(metrics);
        return (int) Math.ceil(width(paint));
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
        Paint digits = new Paint(paint);
        float size = paint.getTextSize();
        digits.setTextSize(size * .7f);
        canvas.drawText("1", x, y - size * .28f, digits);
        x += digits.measureText("1");
        canvas.drawText("⁄", x, y, paint);
        x += paint.measureText("⁄");
        canvas.drawText("x", x, y + size * .10f, digits);
    }
}
