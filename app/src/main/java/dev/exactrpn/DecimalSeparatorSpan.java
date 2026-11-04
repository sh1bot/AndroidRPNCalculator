// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** A tabular decimal mark: the normal glyph centered in one localized digit cell. */
final class DecimalSeparatorSpan extends ReplacementSpan {
    private final String zero;
    DecimalSeparatorSpan(char zeroDigit) { zero=Character.toString(zeroDigit); }
    private int width(Paint paint,String separator) {
        return (int)Math.ceil(Math.max(paint.measureText(zero),paint.measureText(separator)));
    }
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics) {
        if(metrics!=null) paint.getFontMetricsInt(metrics);
        return width(paint,text.subSequence(start,end).toString());
    }
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {
        String separator=text.subSequence(start,end).toString();
        float offset=(width(paint,separator)-paint.measureText(separator))/2;
        canvas.drawText(separator,x+offset,y,paint);
    }
}
