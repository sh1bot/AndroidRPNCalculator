// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Locale-neutral number parts. Callers style and localize these without reparsing text. */
public final class FormattedValue {
    public enum Kind {
        DECIMAL,
        EXPONENTIAL,
        FRACTION,
        ZERO_BOUND
    }

    public final Kind kind;
    public final boolean approximate;
    public final int base, zeroBound;
    public final String canonical, whole, fractional, exponent, numerator, denominator;
    public final char fractionOperator;

    private FormattedValue(
            Kind kind,
            boolean approximate,
            int base,
            int zeroBound,
            String canonical,
            String whole,
            String fractional,
            String exponent,
            String numerator,
            String denominator,
            char fractionOperator) {
        this.kind = kind;
        this.approximate = approximate;
        this.base = base;
        this.zeroBound = zeroBound;
        this.canonical = canonical;
        this.whole = whole;
        this.fractional = fractional;
        this.exponent = exponent;
        this.numerator = numerator;
        this.denominator = denominator;
        this.fractionOperator = fractionOperator;
    }

    static FormattedValue decimal(String number, boolean approximate, int base) {
        int point = number.indexOf('.');
        return new FormattedValue(
                Kind.DECIMAL,
                approximate,
                base,
                0,
                prefix(approximate) + number,
                point < 0 ? number : number.substring(0, point),
                point < 0 ? "" : number.substring(point + 1),
                "",
                "",
                "",
                '\0');
    }

    static FormattedValue exponential(
            String mantissa, String exponent, boolean approximate, int base) {
        int point = mantissa.indexOf('.');
        String marker = base == 10 ? "e" : "p";
        return new FormattedValue(
                Kind.EXPONENTIAL,
                approximate,
                base,
                0,
                prefix(approximate) + mantissa + marker + exponent,
                point < 0 ? mantissa : mantissa.substring(0, point),
                point < 0 ? "" : mantissa.substring(point + 1),
                exponent,
                "",
                "",
                '\0');
    }

    static FormattedValue fraction(
            String whole,
            char operator,
            String numerator,
            String denominator,
            int base,
            boolean space) {
        String separator = operator == 0 ? "" : space ? " " : Character.toString(operator);
        return new FormattedValue(
                Kind.FRACTION,
                false,
                base,
                0,
                whole + separator + numerator + "/" + denominator,
                whole,
                "",
                "",
                numerator,
                denominator,
                operator);
    }

    static FormattedValue zeroBound(int bound, int base, boolean mark) {
        String marker = base == 10 ? "e" : "p";
        return new FormattedValue(
                Kind.ZERO_BOUND,
                mark,
                base,
                bound,
                mark
                        ? "≈ 0 (|x| < 1"
                                + marker
                                + "−"
                                + Integer.toString(bound, base)
                                        .toUpperCase(java.util.Locale.ROOT)
                                + ")"
                        : "0",
                "0",
                "",
                "",
                "",
                "",
                '\0');
    }

    private static String prefix(boolean approximate) {
        return approximate ? "≈ " : "";
    }

    @Override
    public String toString() {
        return canonical;
    }
}
