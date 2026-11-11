// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;

/** Keeps grouping marks narrow while the surrounding numerical text uses a monospace grid. */
final class GroupingSeparatorSpan extends ReplacementSpan {
    private Paint proportional(Paint source) {
        Paint paint = new Paint(source);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return paint;
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) paint.getFontMetricsInt(metrics);
        return (int)
                Math.ceil(proportional(paint).measureText(text.subSequence(start, end).toString()));
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
        canvas.drawText(text, start, end, x, y, proportional(paint));
    }
}
