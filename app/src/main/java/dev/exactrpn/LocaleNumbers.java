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

import java.util.Locale;

/** Use Android's own ICU formatter so grouping widths and suppression rules follow the OS. */
public final class LocaleNumbers {
    private final NumberFormat integers;
    private final DecimalFormatSymbols symbols;
    private final int primary, secondary;
    private final RadixMode radixMode;
    private final NumberText.Profile inputProfile;

    public LocaleNumbers(Locale locale) {
        this(locale, NumberMode.LOCALE, RadixMode.DECIMAL);
    }

    public LocaleNumbers(Locale locale, NumberMode mode) {
        this(locale, mode, RadixMode.DECIMAL);
    }

    public LocaleNumbers(Locale locale, NumberMode mode, RadixMode radixMode) {
        boolean computer = mode == NumberMode.DOT;
        boolean grouping = mode != NumberMode.UNGROUPED;
        this.radixMode = radixMode;
        integers = NumberFormat.getIntegerInstance(computer ? Locale.US : locale);
        integers.setMaximumIntegerDigits(Integer.MAX_VALUE);
        DecimalFormat format = (DecimalFormat) integers;
        symbols = format.getDecimalFormatSymbols();
        if (computer) {
            symbols.setGroupingSeparator('\u2009');
            format.setDecimalFormatSymbols(symbols);
        }
        format.setGroupingUsed(grouping && format.isGroupingUsed());
        primary =
                format.isGroupingUsed()
                        ? radixMode == RadixMode.DECIMAL
                                ? format.getGroupingSize()
                                : radixMode.grouping
                        : 0;
        secondary =
                primary == 0
                        ? 0
                        : radixMode == RadixMode.DECIMAL
                                ? format.getSecondaryGroupingSize()
                                : radixMode.grouping;
        inputProfile =
                new NumberText.Profile(
                        decimalSeparator(),
                        symbols.getGroupingSeparator(),
                        primary,
                        secondary,
                        radixMode.radix);
    }

    public char decimalSeparator() {
        return symbols.getDecimalSeparator();
    }

    /** Entry localizes digits and decimal separator, but never inserts grouping. */
    public String entry(String canonical) {
        canonical = radixMode.digits(canonical);
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
        int boundMarker = exponent(number, bound.length());
        if (number.startsWith(bound) && boundMarker == bound.length() && number.endsWith(")")) {
            text.append(entry(bound));
            appendExponent(text, number.substring(boundMarker + 1, number.length() - 1));
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
        int e = exponent(number, 0);
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
                new RelativeSizeSpan(ExponentMarkerSpan.MARKER_SCALE),
                start,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        start = text.length();
        text.append(entry(exponent.replace('-', '−')));
        text.setSpan(
                new RelativeSizeSpan(ExponentMarkerSpan.INDEX_SCALE),
                start,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new SuperscriptSpan(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static final class EntryIndexSize extends RelativeSizeSpan {
        EntryIndexSize() {
            super(ExponentMarkerSpan.INDEX_SCALE);
        }
    }

    private static final class EntryIndexRaise extends SuperscriptSpan {}

    /** The exponent marker stays one editable character, so offsets and copy stay literal. */
    public void styleEntry(Spannable text) {
        styleDecimal(text);
        for (Object span : text.getSpans(0, text.length(), Object.class))
            if (span instanceof ExponentMarkerSpan
                    || span instanceof EntryIndexSize
                    || span instanceof EntryIndexRaise) text.removeSpan(span);
        int marker = -1;
        for (int i = 0; i < text.length(); i++)
            if (isExponent(text.charAt(i))) {
                marker = i;
                break;
            }
        if (marker < 0
                || !NumberText.analyze(text.subSequence(0, marker).toString(), profile()).valid())
            return;
        for (int i = marker + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i == marker + 1 && (c == '+' || c == '-' || c == '−')) continue;
            if (Character.digit(c, radixMode.radix) < 0) return;
        }
        text.setSpan(
                new ExponentMarkerSpan(entry("10")),
                marker,
                marker + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (marker + 1 < text.length()) {
            text.setSpan(
                    new EntryIndexSize(),
                    marker + 1,
                    text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(
                    new EntryIndexRaise(),
                    marker + 1,
                    text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** Styling only: preserve characters, caret offsets, locale and validation colors. */
    public void styleDecimal(Spannable text) {
        for (DecimalSeparatorSpan span :
                text.getSpans(0, text.length(), DecimalSeparatorSpan.class)) text.removeSpan(span);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isExponent(c)) break;
            if (c == decimalSeparator())
                text.setSpan(
                        new DecimalSeparatorSpan(symbols.getZeroDigit()),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void appendInteger(SpannableStringBuilder text, String whole, int groupingColor) {
        boolean negative = whole.startsWith("-");
        if (negative || whole.startsWith("+")) whole = whole.substring(1);
        StringBuilder grouped = new StringBuilder();
        int first = whole.length(), width = primary;
        while (width > 0 && first > width) {
            int next = first - width;
            grouped.insert(0, symbols.getGroupingSeparator() + whole.substring(next, first));
            first = next;
            width = secondary > 0 ? secondary : primary;
        }
        grouped.insert(0, whole.substring(0, first));
        String localized = entry(grouped.toString());
        if (negative) {
            DecimalFormat format = (DecimalFormat) integers;
            localized = format.getNegativePrefix() + localized + format.getNegativeSuffix();
        }
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
        return inputProfile;
    }

    public String copy(String canonical) {
        return entry(canonical);
    }

    public String canonicalEntry(String visible) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < visible.length(); i++) {
            char c = visible.charAt(i);
            int digit = Character.digit(c, radixMode.radix);
            text.append(
                    digit >= 0
                            ? Character.toUpperCase(Character.forDigit(digit, radixMode.radix))
                            : c == '\u218a'
                                    ? 'A'
                                    : c == '\u218b'
                                            ? 'B'
                                            : c == decimalSeparator() ? '.' : c == '−' ? '-' : c);
        }
        return text.toString();
    }

    public RadixMode radixMode() {
        return radixMode;
    }

    private boolean isExponent(char c) {
        return c == 'p' || c == 'P' || radixMode == RadixMode.DECIMAL && (c == 'e' || c == 'E');
    }

    private int exponent(String text, int from) {
        for (int i = from; i < text.length(); i++) if (isExponent(text.charAt(i))) return i;
        return -1;
    }
}
