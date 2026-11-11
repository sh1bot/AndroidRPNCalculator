// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;

/** Caller-side presentation of already evaluated numerical data. */
public final class ValueFormatter {
    public enum Notation {
        SCIENTIFIC,
        ENGINEERING
    }

    private static final int DIGITS = 12, MAX_WIDE_DIGITS = 40, MAX_INLINE_DIGITS = 1000;

    private ValueFormatter() {}

    public static String format(NumericValue value, boolean newest, Notation notation) {
        return format(value, newest, notation, FractionMode.DECIMAL);
    }

    public static String format(
            NumericValue value, boolean newest, Notation notation, FractionMode mode) {
        RationalValue rational = value.rational;
        if (rational != null) {
            boolean integer = rational.denominator.equals(BigInteger.ONE);
            if (integer && newest && fits(rational.numerator, value.base))
                return digits(rational.numerator, value.base);
            if (!integer
                    && mode != FractionMode.DECIMAL
                    && fits(rational.numerator, value.base)
                    && fits(rational.denominator, value.base)) {
                String text = fraction(rational, mode == FractionMode.MIXED, value.base);
                if (newest || text.length() <= DIGITS + 4) return text;
            }
            if (newest && mode == FractionMode.DECIMAL) {
                String text = terminating(rational, value.base);
                if (text != null && text.length() <= MAX_INLINE_DIGITS + 2) return text;
            }
        }
        return approximation(value, notation, DIGITS, true, newest);
    }

    public static String clipboard(NumericValue value, Notation notation, FractionMode mode) {
        if (value.rational != null) {
            if (mode != FractionMode.DECIMAL)
                return fraction(value.rational, mode == FractionMode.MIXED, value.base);
            String text = terminating(value.rational, value.base);
            if (text != null) return text;
        }
        return approximation(value, notation, 40, false, false).replace('\u2212', '-');
    }

    /** Exact fraction text for a caller which can measure it, or null when none is available. */
    public static String exactFraction(NumericValue value, FractionMode mode) {
        RationalValue rational = value.rational;
        if (rational == null
                || rational.denominator.equals(BigInteger.ONE)
                || mode == FractionMode.DECIMAL
                || !fits(rational.numerator, value.base)
                || !fits(rational.denominator, value.base)) return null;
        if (mode != FractionMode.MIXED) return fraction(rational, false, value.base);
        BigInteger[] parts = rational.numerator.abs().divideAndRemainder(rational.denominator);
        if (parts[0].signum() == 0) return fraction(rational, false, value.base);
        return (rational.numerator.signum() < 0 ? "-" : "")
                + digits(parts[0], value.base)
                + (rational.numerator.signum() < 0 ? "-" : "+")
                + digits(parts[1], value.base)
                + "/"
                + digits(rational.denominator, value.base);
    }

    /** Forced exponential fallback, rounded to at most the requested significant digits. */
    public static String exponential(NumericValue value, Notation notation, int significantDigits) {
        if (significantDigits < 1) throw new IllegalArgumentException("No significant digits");
        if (value.zeroBound != 0)
            return approximation(value, notation, significantDigits, true, false);
        if (value.num.signum() == 0) return "0";
        BigInteger radix = BigInteger.valueOf(value.base);
        BigInteger coefficient = value.num.abs();
        int scale = value.scale;
        boolean exact = value.exact;
        int length = coefficient.toString(value.base).length();
        if (length > significantDigits) {
            int removed = length - significantDigits;
            BigInteger divisor = radix.pow(removed);
            BigInteger[] parts = coefficient.divideAndRemainder(divisor);
            int halfway = parts[1].shiftLeft(1).compareTo(divisor);
            coefficient =
                    halfway > 0 || halfway == 0 && parts[0].testBit(0)
                            ? parts[0].add(BigInteger.ONE)
                            : parts[0];
            scale -= removed;
            exact = false;
            if (coefficient.toString(value.base).length() > significantDigits) {
                coefficient = coefficient.divide(radix);
                scale--;
            }
        }
        String number = digits(coefficient, value.base);
        int exponent = number.length() - scale - 1;
        int group = RadixMode.of(value.base).grouping;
        int power =
                notation == Notation.ENGINEERING
                        ? Math.floorDiv(exponent, group) * group
                        : exponent;
        String mantissa = pointAfter(number, exponent - power + 1);
        return (exact ? "" : "≈ ")
                + (value.num.signum() < 0 ? "-" : "")
                + mantissa
                + exponentMarker(value.base)
                + (power >= 0 ? "+" : "−")
                + radixDigits(Math.abs(power), value.base);
    }

    public static String clipboard(NumericValue value, Notation notation) {
        return clipboard(value, notation, FractionMode.DECIMAL);
    }

    private static boolean fits(BigInteger value, int base) {
        return value.abs().toString(base).length() <= MAX_INLINE_DIGITS;
    }

    private static String digits(BigInteger value, int base) {
        return value.toString(base).toUpperCase(java.util.Locale.ROOT);
    }

    private static String fraction(RationalValue value, boolean mixed, int base) {
        BigInteger numerator = value.numerator, denominator = value.denominator;
        if (denominator.equals(BigInteger.ONE)) return digits(numerator, base);
        if (!mixed) return digits(numerator, base) + "/" + digits(denominator, base);
        BigInteger[] parts = numerator.abs().divideAndRemainder(denominator);
        if (parts[0].signum() == 0)
            return digits(numerator, base) + "/" + digits(denominator, base);
        return (numerator.signum() < 0 ? "-" : "")
                + digits(parts[0], base)
                + " "
                + digits(parts[1], base)
                + "/"
                + digits(denominator, base);
    }

    private static String terminating(RationalValue value, int base) {
        BigInteger radix = BigInteger.valueOf(base), rest = value.denominator;
        int places = 0;
        while (!rest.equals(BigInteger.ONE) && places <= MAX_INLINE_DIGITS) {
            BigInteger factor = rest.gcd(radix);
            if (factor.equals(BigInteger.ONE)) return null;
            rest = rest.divide(factor);
            places++;
        }
        if (!rest.equals(BigInteger.ONE)) return null;
        BigInteger scaled = value.numerator.multiply(radix.pow(places)).divide(value.denominator);
        boolean negative = scaled.signum() < 0;
        String text = scaled.abs().toString(base).toUpperCase(java.util.Locale.ROOT);
        if (places == 0) return (negative ? "-" : "") + text;
        text = "0".repeat(Math.max(0, places + 1 - text.length())) + text;
        int point = text.length() - places;
        text = text.substring(0, point) + "." + text.substring(point);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return (negative ? "-" : "") + text;
    }

    private static String approximation(
            NumericValue value,
            Notation notation,
            int significantDigits,
            boolean mark,
            boolean preferPlain) {
        if (value.zeroBound != 0)
            return mark
                    ? "\u2248 0 (|x| < 1"
                            + exponentMarker(value.base)
                            + "\u2212"
                            + radixDigits(value.zeroBound, value.base)
                            + ")"
                    : "0";
        String prefix = mark && !value.exact ? "\u2248 " : "";
        if (value.num.signum() == 0) return prefix + "0";
        boolean negative = value.num.signum() < 0;
        String number = digits(value.num.abs(), value.base);
        int exponent = number.length() - value.scale - 1;
        if (exponent >= -6 && exponent < significantDigits
                || preferPlain && Math.abs((long) exponent) <= MAX_WIDE_DIGITS)
            return prefix + (negative ? "-" : "") + plain(number, value.scale);
        int group = RadixMode.of(value.base).grouping;
        int power =
                notation == Notation.ENGINEERING
                        ? Math.floorDiv(exponent, group) * group
                        : exponent;
        int wholeDigits = exponent - power + 1;
        String mantissa = pointAfter(number, wholeDigits);
        return prefix
                + (negative ? "-" : "")
                + mantissa
                + exponentMarker(value.base)
                + (power >= 0 ? "+" : "\u2212")
                + radixDigits(Math.abs(power), value.base);
    }

    private static String exponentMarker(int base) {
        return base == 10 ? "e" : "p";
    }

    private static String radixDigits(int value, int base) {
        return Integer.toString(value, base).toUpperCase(java.util.Locale.ROOT);
    }

    private static String plain(String number, int scale) {
        int point = number.length() - scale;
        if (point <= 0) return "0." + "0".repeat(-point) + number;
        if (point >= number.length()) return number + "0".repeat(point - number.length());
        return number.substring(0, point) + "." + number.substring(point);
    }

    private static String pointAfter(String number, int wholeDigits) {
        if (wholeDigits <= 0) return "0." + "0".repeat(-wholeDigits) + number;
        if (wholeDigits >= number.length())
            return number + "0".repeat(wholeDigits - number.length());
        return number.substring(0, wholeDigits) + "." + number.substring(wholeDigits);
    }
}
