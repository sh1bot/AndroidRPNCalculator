// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.icu.text.DecimalFormat;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.NumberFormat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import java.math.BigInteger;
import java.util.Locale;

/** Use Android's own ICU formatter so grouping widths and suppression rules follow the OS. */
public final class LocaleNumbers {
    private final NumberFormat integers;
    private final DecimalFormatSymbols symbols;
    private final int primary, secondary;
    private final boolean computer;
    public LocaleNumbers(Locale locale) { this(locale,false,true); }
    public LocaleNumbers(Locale locale,boolean computer) { this(locale,computer,true); }
    public LocaleNumbers(Locale locale,boolean computer,boolean grouping) {
        this.computer=computer;
        integers = NumberFormat.getIntegerInstance(computer ? Locale.US : locale);
        integers.setMaximumIntegerDigits(Integer.MAX_VALUE);
        DecimalFormat format = (DecimalFormat)integers;
        symbols = format.getDecimalFormatSymbols();
        if (computer) { symbols.setGroupingSeparator('\u2009'); format.setDecimalFormatSymbols(symbols); }
        format.setGroupingUsed(grouping && format.isGroupingUsed());
        primary = format.isGroupingUsed() ? format.getGroupingSize() : 0;
        secondary = format.getSecondaryGroupingSize();
    }
    public char decimalSeparator() { return symbols.getDecimalSeparator(); }
    /** Entry localizes digits and decimal separator, but never inserts grouping. */
    public String entry(String canonical) {
        StringBuilder text = new StringBuilder();
        for (int i=0;i<canonical.length();i++) {
            char c = canonical.charAt(i);
            text.append(c>='0' && c<='9' ? (char)(symbols.getZeroDigit()+c-'0') : c=='.' ? decimalSeparator() : c);
        }
        return text.toString();
    }
    public CharSequence stack(String canonical, int groupingColor) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        String number = canonical;
        if (number.startsWith("≈ ")) { text.append("≈ "); number = number.substring(2); }
        // The display's explicit near-zero annotation is not a numeric literal.
        if (number.contains("(")) return text.append(entry(number));
        int slash=number.indexOf('/');
        if(slash>=0) {
            appendInteger(text,number.substring(0,slash),groupingColor);
            text.append("⁄");
            appendInteger(text,number.substring(slash+1),groupingColor);
            return text;
        }
        int e = number.indexOf('e');
        String mantissa = e<0 ? number : number.substring(0,e);
        int dot = mantissa.indexOf('.');
        String whole = dot<0 ? mantissa : mantissa.substring(0,dot);
        appendInteger(text,whole,groupingColor);
        if (dot>=0) {
            int start=text.length(); text.append(Character.toString(decimalSeparator()));
            text.setSpan(new DecimalSeparatorSpan(),start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(entry(mantissa.substring(dot+1)));
        }
        if (e>=0) text.append("e").append(entry(number.substring(e+1)));
        return text;
    }
    /** Styling only: preserve characters, caret offsets, locale and validation colors. */
    public void styleDecimal(Spannable text) {
        for(DecimalSeparatorSpan span:text.getSpans(0,text.length(),DecimalSeparatorSpan.class)) text.removeSpan(span);
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='e' || c=='E') break;
            if(c==decimalSeparator()) text.setSpan(new DecimalSeparatorSpan(),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    private void appendInteger(SpannableStringBuilder text,String whole,int groupingColor) {
        BigInteger integer = new BigInteger(whole);
        String localized = integers.format(integer);
        if (whole.startsWith("-") && integer.signum()==0) localized = ((DecimalFormat)integers).getNegativePrefix()+localized; // No double conversion or digit loss.
        int start = text.length(); text.append(localized);
        for (int i=0;i<localized.length();i++) {
            if (localized.charAt(i)==symbols.getGroupingSeparator())
                text.setSpan(new ForegroundColorSpan(groupingColor),start+i,start+i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    public NumberText.Profile profile() { return new NumberText.Profile(decimalSeparator(),symbols.getGroupingSeparator(),primary,secondary); }
    public String copy(String canonical) { return computer ? canonical : entry(canonical); }
    public String canonicalEntry(String visible) {
        StringBuilder text=new StringBuilder();
        for(int i=0;i<visible.length();i++) {
            char c=visible.charAt(i); int digit=Character.digit(c,10);
            text.append(digit>=0 ? (char)('0'+digit) : c==decimalSeparator() ? '.' : c=='−' ? '-' : c);
        }
        return text.toString();
    }
}


