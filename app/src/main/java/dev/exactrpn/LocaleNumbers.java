// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.icu.text.DecimalFormat;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.NumberFormat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;

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

    public CharSequence stack(FormattedValue value, int groupingColor) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (value.approximate) text.append("≈ ");
        if (value.kind == FormattedValue.Kind.ZERO_BOUND) {
            text.append(entry("0 (|x| < 1"));
            appendExponent(
                    text,
                    "−"
                            + Integer.toString(value.zeroBound, value.base)
                                    .toUpperCase(java.util.Locale.ROOT),
                    "10");
            text.append(")");
            return styleDozenal(text);
        }
        if (value.kind == FormattedValue.Kind.FRACTION) {
            String numerator = value.numerator;
            if (!value.whole.isEmpty()) {
                appendInteger(text, value.whole, groupingColor);
                char symbol = value.fractionOperator;
                text.append(symbol == ' ' ? "\u00a0" : symbol == '-' ? " − " : " + ");
            } else if (value.numerator.startsWith("-")) {
                text.append(((DecimalFormat) integers).getNegativePrefix());
                numerator = numerator.substring(1);
            }
            appendInteger(text, numerator, groupingColor);
            text.append("/");
            appendInteger(text, value.denominator, groupingColor);
            return styleDozenal(text);
        }
        appendInteger(text, value.whole, groupingColor);
        if (!value.fractional.isEmpty()) {
            int start = text.length();
            text.append(Character.toString(decimalSeparator()));
            text.setSpan(
                    new DecimalSeparatorSpan(symbols.getZeroDigit()),
                    start,
                    start + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(entry(value.fractional));
        }
        if (value.kind == FormattedValue.Kind.EXPONENTIAL)
            appendExponent(text, value.exponent, "10");
        return styleDozenal(text);
    }

    /** Add invisible, numerically meaningful opportunities for Android's balanced line breaker. */
    CharSequence breakable(CharSequence formatted) {
        SpannableStringBuilder text = new SpannableStringBuilder(formatted);
        java.util.NavigableSet<Integer> breaks = new java.util.TreeSet<>();
        boolean[] handled = new boolean[text.length()];
        int point = -1;
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == decimalSeparator()) {
                point = i;
                break;
            }
        if (point >= 0) {
            int start = point, end = point + 1;
            while (start > 0 && numeric(text.charAt(start - 1))) start--;
            while (end < text.length() && numeric(text.charAt(end))) end++;
            addIntegerBreaks(text, start, point, breaks);
            addFractionBreaks(text, point + 1, end, breaks);
            java.util.Arrays.fill(handled, start, end, true);
        }
        for (int start = 0; start < text.length(); ) {
            if (handled[start] || !numeric(text.charAt(start))) {
                start++;
                continue;
            }
            int end = start + 1;
            while (end < text.length() && !handled[end] && numeric(text.charAt(end))) end++;
            addIntegerBreaks(text, start, end, breaks);
            start = end;
        }
        java.util.NavigableSet<Integer> joins = new java.util.TreeSet<>();
        for (int position = 1; position < text.length(); position++)
            if (numericBoundary(text, position) && !breaks.contains(position))
                joins.add(position);
        java.util.NavigableSet<Integer> insertions = new java.util.TreeSet<>();
        insertions.addAll(breaks);
        insertions.addAll(joins);
        for (int position : insertions.descendingSet())
            text.insert(position, breaks.contains(position) ? "\u200b" : "\u2060");
        return text;
    }

    private boolean numericBoundary(CharSequence text, int position) {
        char left = text.charAt(position - 1), right = text.charAt(position);
        boolean leftNumeric = numeric(left) || left == decimalSeparator();
        boolean rightNumeric = numeric(right) || right == decimalSeparator();
        return leftNumeric && rightNumeric;
    }

    private boolean numeric(char character) {
        return Character.digit(character, radixMode.radix) >= 0
                || character == '\u218a'
                || character == '\u218b'
                || character == symbols.getGroupingSeparator();
    }

    private boolean digit(char character) {
        return Character.digit(character, radixMode.radix) >= 0
                || character == '\u218a'
                || character == '\u218b';
    }

    private void addIntegerBreaks(
            CharSequence text, int start, int end, java.util.Set<Integer> breaks) {
        boolean grouped = false;
        for (int i = start; i < end; i++)
            if (text.charAt(i) == symbols.getGroupingSeparator()) {
                breaks.add(i + 1);
                grouped = true;
            }
        if (grouped) return;
        java.util.List<Integer> digits = new java.util.ArrayList<>();
        for (int i = start; i < end; i++) if (digit(text.charAt(i))) digits.add(i);
        int group = radixMode.grouping;
        for (int fromRight = group; fromRight < digits.size(); fromRight += group)
            breaks.add(digits.get(digits.size() - fromRight));
    }

    private void addFractionBreaks(
            CharSequence text, int start, int end, java.util.Set<Integer> breaks) {
        int digits = 0, group = radixMode.grouping;
        for (int i = start; i < end; i++)
            if (digit(text.charAt(i)) && ++digits % group == 0 && i + 1 < end) breaks.add(i + 1);
    }

    private void appendExponent(SpannableStringBuilder text, String exponent, String base) {
        int start = text.length();
        text.append("\u2009×");
        text.setSpan(
                new RelativeSizeSpan(ExponentMarkerSpan.MARKER_SCALE),
                start,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new TypefaceSpan("sans-serif"),
                start,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(entry(base));
        text.setSpan(
                new RelativeSizeSpan(ExponentMarkerSpan.MARKER_SCALE),
                start + 2,
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
        styleDozenal(text);
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

    /** Preserve the real dozenal characters for editing and copying, with a drawing fallback. */
    <T extends Spannable> T styleDozenal(T text) {
        for (TurnedDigitSpan span : text.getSpans(0, text.length(), TurnedDigitSpan.class))
            text.removeSpan(span);
        if (radixMode != RadixMode.DOZENAL) return text;
        for (int i = 0; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit == '\u218a' || digit == '\u218b')
                text.setSpan(
                        new TurnedDigitSpan(digit), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
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
            if (localized.charAt(i) == symbols.getGroupingSeparator()) {
                text.setSpan(
                        new GroupingSeparatorSpan(groupingColor),
                        start + i,
                        start + i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    static final class FractionParts {
        final CharSequence whole, numerator, denominator;

        FractionParts(CharSequence whole, CharSequence numerator, CharSequence denominator) {
            this.whole = whole;
            this.numerator = numerator;
            this.denominator = denominator;
        }

        boolean mixed() {
            return whole.length() != 0;
        }
    }

    FractionParts fractionParts(FormattedValue value, int groupingColor) {
        if (value.kind != FormattedValue.Kind.FRACTION) return null;
        SpannableStringBuilder displayedWhole =
                new SpannableStringBuilder(integer(value.whole, groupingColor));
        if (!value.whole.isEmpty())
            displayedWhole.append(value.fractionOperator == '-' ? " −" : " +");
        return new FractionParts(
                breakable(displayedWhole),
                breakable(integer(value.numerator, groupingColor)),
                breakable(integer(value.denominator, groupingColor)));
    }

    private CharSequence integer(String canonical, int groupingColor) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (!canonical.isEmpty()) appendInteger(text, canonical, groupingColor);
        return styleDozenal(text);
    }

    public NumberText.Profile profile() {
        return inputProfile;
    }

    public String copy(String canonical) {
        if (radixMode != RadixMode.DOZENAL) return entry(canonical);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < canonical.length(); i++) {
            char c = canonical.charAt(i);
            text.append(
                    c >= '0' && c <= '9'
                            ? (char) (symbols.getZeroDigit() + c - '0')
                            : c == '.'
                                    ? decimalSeparator()
                                    : c == 'a' || c == 'A' || c == '\u218a'
                                            ? 'A'
                                            : c == 'b' || c == 'B' || c == '\u218b' ? 'B' : c);
        }
        return text.toString();
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

}
