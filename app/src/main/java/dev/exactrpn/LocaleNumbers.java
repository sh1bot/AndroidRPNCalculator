// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.icu.text.DecimalFormat;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.NumberFormat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;

import java.math.BigInteger;
import java.util.Locale;

/** Use Android's own ICU formatter so grouping widths and suppression rules follow the OS. */
public final class LocaleNumbers {
    private final NumberFormat integers;
    private final DecimalFormatSymbols symbols;
    private final int primary, secondary;
    private final boolean computer;

    public LocaleNumbers(Locale locale) {
        this(locale, NumberMode.LOCALE);
    }

    public LocaleNumbers(Locale locale, NumberMode mode) {
        boolean computer = mode == NumberMode.DOT;
        boolean grouping = mode != NumberMode.UNGROUPED;
        this.computer = computer;
        integers = NumberFormat.getIntegerInstance(computer ? Locale.US : locale);
        integers.setMaximumIntegerDigits(Integer.MAX_VALUE);
        DecimalFormat format = (DecimalFormat) integers;
        symbols = format.getDecimalFormatSymbols();
        if (computer) {
            symbols.setGroupingSeparator('\u2009');
            format.setDecimalFormatSymbols(symbols);
        }
        format.setGroupingUsed(grouping && format.isGroupingUsed());
        primary = format.isGroupingUsed() ? format.getGroupingSize() : 0;
        secondary = format.getSecondaryGroupingSize();
    }

    public char decimalSeparator() {
        return symbols.getDecimalSeparator();
    }

    /** Entry localizes digits and decimal separator, but never inserts grouping. */
    public String entry(String canonical) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < canonical.length(); i++) {
            char c = canonical.charAt(i);
            text.append(
                    c >= '0' && c <= '9'
                            ? (char) (symbols.getZeroDigit() + c - '0')
                            : c == '.' ? decimalSeparator() : c);
        }
        return text.toString();
    }

    public CharSequence stack(String canonical, int groupingColor) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        String number = canonical;
        if (number.startsWith("≈ ")) {
            text.append("≈ ");
            number = number.substring(2);
        }
        // The near-zero bound is an annotation, but its power of ten uses the same styling.
        String bound = "0 (|x| < 1";
        if (number.startsWith(bound + "e") && number.endsWith(")")) {
            text.append(entry(bound));
            appendExponent(text, number.substring(bound.length() + 1, number.length() - 1));
            return text.append(")");
        }
        if (number.contains("(")) return text.append(entry(number));
        int slash = number.indexOf('/');
        if (slash >= 0) {
            int whole = number.indexOf(' ');
            String numerator = number.substring(0, slash);
            if (whole >= 0 && whole < slash) {
                appendInteger(text, number.substring(0, whole), groupingColor);
                text.append("\u00a0");
                numerator = number.substring(whole + 1, slash);
            } else if (numerator.startsWith("-")) {
                text.append(((DecimalFormat) integers).getNegativePrefix());
                numerator = numerator.substring(1);
            }
            int fractionStart = text.length();
            int start = text.length();
            appendInteger(text, numerator, groupingColor);
            text.setSpan(
                    new RelativeSizeSpan(.72f),
                    start,
                    text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(
                    new SuperscriptSpan(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // Ordinary slash; styling is explicit and covers every digit, rather than
            // font-dependent.
            text.append("/");
            start = text.length();
            appendInteger(text, number.substring(slash + 1), groupingColor);
            text.setSpan(
                    new RelativeSizeSpan(.72f),
                    start,
                    text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(
                    new SubscriptSpan(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // Apply before digit scaling, so numerator, slash and denominator all rise equally.
            text.setSpan(
                    new FractionRaiseSpan(),
                    fractionStart,
                    text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | (1 << Spanned.SPAN_PRIORITY_SHIFT));
            return text;
        }
        int e = number.indexOf('e');
        if (e < 0) e = number.indexOf('E');
        String mantissa = e < 0 ? number : number.substring(0, e);
        int dot = mantissa.indexOf('.');
        String whole = dot < 0 ? mantissa : mantissa.substring(0, dot);
        appendInteger(text, whole, groupingColor);
        if (dot >= 0) {
            int start = text.length();
            text.append(Character.toString(decimalSeparator()));
            text.setSpan(
                    new DecimalSeparatorSpan(symbols.getZeroDigit()),
                    start,
                    start + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(entry(mantissa.substring(dot + 1)));
        }
        if (e >= 0) appendExponent(text, number.substring(e + 1));
        return text;
    }

    private void appendExponent(SpannableStringBuilder text, String exponent) {
        int start = text.length();
        text.append("\u2009×").append(entry("10"));
        text.setSpan(
                new RelativeSizeSpan(.75f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        start = text.length();
        text.append(entry(exponent.replace('-', '−')));
        text.setSpan(
                new RelativeSizeSpan(.6f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new SuperscriptSpan(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Styling only: preserve characters, caret offsets, locale and validation colors. */
    public void styleDecimal(Spannable text) {
        for (DecimalSeparatorSpan span :
                text.getSpans(0, text.length(), DecimalSeparatorSpan.class)) text.removeSpan(span);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'e' || c == 'E') break;
            if (c == decimalSeparator())
                text.setSpan(
                        new DecimalSeparatorSpan(symbols.getZeroDigit()),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void appendInteger(SpannableStringBuilder text, String whole, int groupingColor) {
        BigInteger integer = new BigInteger(whole);
        String localized = integers.format(integer);
        if (whole.startsWith("-") && integer.signum() == 0)
            localized =
                    ((DecimalFormat) integers).getNegativePrefix()
                            + localized; // No double conversion or digit loss.
        int start = text.length();
        text.append(localized);
        for (int i = 0; i < localized.length(); i++) {
            if (localized.charAt(i) == symbols.getGroupingSeparator())
                text.setSpan(
                        new ForegroundColorSpan(groupingColor),
                        start + i,
                        start + i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public NumberText.Profile profile() {
        return new NumberText.Profile(
                decimalSeparator(), symbols.getGroupingSeparator(), primary, secondary);
    }

    public String copy(String canonical) {
        return computer ? canonical : entry(canonical);
    }

    public String canonicalEntry(String visible) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < visible.length(); i++) {
            char c = visible.charAt(i);
            int digit = Character.digit(c, 10);
            text.append(
                    digit >= 0
                            ? (char) ('0' + digit)
                            : c == decimalSeparator() ? '.' : c == '−' ? '-' : c);
        }
        return text.toString();
    }
}
