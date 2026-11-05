// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/** Center the complete fraction beside the whole number's digit height. */
final class FractionRaiseSpan extends MetricAffectingSpan {
    private void raise(TextPaint paint) {
        paint.baselineShift -= Math.round(paint.getTextSize() * .12f);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        raise(paint);
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        raise(paint);
    }
}
