// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Caller-side presentation of already evaluated numerical data. */
public final class ValueFormatter {
    public enum Notation {
        SCIENTIFIC,
        ENGINEERING
    }

    private static final int DIGITS = 12, MAX_INLINE_DIGITS = 1000;
    private static final BigInteger INLINE_LIMIT = BigInteger.TEN.pow(MAX_INLINE_DIGITS);

    private ValueFormatter() {}

    public static String format(NumericValue value, boolean newest, Notation notation) {
        return format(value, newest, notation, FractionMode.DECIMAL);
    }

    public static String format(
            NumericValue value, boolean newest, Notation notation, FractionMode mode) {
        RationalValue rational = value.rational;
        if (rational != null) {
            boolean integer = rational.denominator.equals(BigInteger.ONE);
            if (integer && newest && rational.numerator.abs().compareTo(INLINE_LIMIT) < 0)
                return rational.numerator.toString();
            if (!integer
                    && mode != FractionMode.DECIMAL
                    && rational.numerator.abs().compareTo(INLINE_LIMIT) < 0
                    && rational.denominator.compareTo(INLINE_LIMIT) < 0) {
                String text = fraction(rational, mode == FractionMode.MIXED);
                if (newest || text.length() <= DIGITS + 4) return text;
            }
            if (newest && mode == FractionMode.DECIMAL) {
                String text = terminating(rational);
                if (text != null && text.length() <= MAX_INLINE_DIGITS) return text;
            }
        }
        return approximation(value, notation, DIGITS, true);
    }

    public static String clipboard(NumericValue value, Notation notation, FractionMode mode) {
        if (value.rational != null) {
            if (mode != FractionMode.DECIMAL)
                return fraction(value.rational, mode == FractionMode.MIXED);
            String text = terminating(value.rational);
            if (text != null) return text;
        }
        return approximation(value, notation, 40, false).replace('−', '-');
    }

    public static String clipboard(NumericValue value, Notation notation) {
        return clipboard(value, notation, FractionMode.DECIMAL);
    }

    private static String fraction(RationalValue value, boolean mixed) {
        BigInteger numerator = value.numerator, denominator = value.denominator;
        if (denominator.equals(BigInteger.ONE)) return numerator.toString();
        if (!mixed) return numerator + "/" + denominator;
        BigInteger[] parts = numerator.abs().divideAndRemainder(denominator);
        if (parts[0].signum() == 0) return numerator + "/" + denominator;
        return (numerator.signum() < 0 ? "-" : "") + parts[0] + " " + parts[1] + "/" + denominator;
    }

    private static String terminating(RationalValue value) {
        BigInteger rest = value.denominator;
        int twos = rest.getLowestSetBit();
        rest = rest.shiftRight(twos);
        int fives = 0;
        while (rest.mod(BigInteger.valueOf(5)).signum() == 0) {
            rest = rest.divide(BigInteger.valueOf(5));
            fives++;
        }
        if (!rest.equals(BigInteger.ONE) || Math.max(twos, fives) > MAX_INLINE_DIGITS) return null;
        return new BigDecimal(value.numerator)
                .divide(new BigDecimal(value.denominator))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String approximation(
            NumericValue value, Notation notation, int digits, boolean mark) {
        if (value.base != 10)
            throw new IllegalArgumentException("Decimal presentation requires radix ten");
        if (value.zeroBound != 0) return mark ? "≈ 0 (|x| < 1e−" + value.zeroBound + ")" : "0";
        BigDecimal number = new BigDecimal(value.num, value.scale).stripTrailingZeros();
        int exponent = number.precision() - number.scale() - 1;
        String prefix = mark && !value.exact ? "≈ " : "";
        if (number.signum() == 0 || exponent >= -6 && exponent < digits)
            return prefix + number.toPlainString();
        int power = notation == Notation.ENGINEERING ? Math.floorDiv(exponent, 3) * 3 : exponent;
        return prefix
                + number.scaleByPowerOfTen(-power).toPlainString()
                + "e"
                + (power >= 0 ? "+" : "−")
                + Math.abs(power);
    }
}
