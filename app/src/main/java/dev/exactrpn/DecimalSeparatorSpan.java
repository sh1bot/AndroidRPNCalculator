// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Give the decimal mark weight and breathing room without enlarging the whole line. */
final class DecimalSeparatorSpan extends ReplacementSpan {
    private Paint markPaint(Paint original) {
        Paint mark=new Paint(original); mark.setFakeBoldText(true); mark.setTextSize(original.getTextSize()*1.3f);
        return mark;
    }
    private int width(Paint original,Paint mark,String text) {
        return (int)Math.ceil(Math.max(original.getTextSize()*.45f,mark.measureText(text)));
    }
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics) {
        if(metrics!=null) paint.getFontMetricsInt(metrics);
        String separator=text.subSequence(start,end).toString();
        return width(paint,markPaint(paint),separator);
    }
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {
        String separator=text.subSequence(start,end).toString(); Paint mark=markPaint(paint);
        float offset=(width(paint,mark,separator)-mark.measureText(separator))/2;
        canvas.drawText(separator,x+offset,y,mark);
    }
}
