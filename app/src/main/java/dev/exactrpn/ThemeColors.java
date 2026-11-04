// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;

/** Resources select the palette from the OS light/dark configuration, including startup. */
final class ThemeColors {
    final int background,text,muted,numberKey,functionKey,enterKey,enterText,accent,error,ripple;
    ThemeColors(Context context) {
        background=context.getColor(R.color.calculator_background); text=context.getColor(R.color.calculator_text);
        muted=context.getColor(R.color.calculator_muted); numberKey=context.getColor(R.color.calculator_number_key);
        functionKey=context.getColor(R.color.calculator_function_key); enterKey=context.getColor(R.color.calculator_enter_key);
        enterText=context.getColor(R.color.calculator_enter_text); accent=context.getColor(R.color.calculator_accent);
        error=context.getColor(R.color.calculator_error); ripple=context.getColor(R.color.calculator_ripple);
    }
}
