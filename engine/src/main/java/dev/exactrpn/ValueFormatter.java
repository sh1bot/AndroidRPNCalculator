// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;

import com.hp.creals.StringFloatRep;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Display-only approximations. Never replace the exact stack values with these strings. */
public final class ValueFormatter {
    public enum Notation {
        SCIENTIFIC,
        ENGINEERING
    }

    private static final int DIGITS = 12;
    private static final int MAX_INLINE_DIGITS = 1000;
    private static final BigInteger INLINE_LIMIT = BigInteger.TEN.pow(MAX_INLINE_DIGITS);
    private static final int COPY_DIGITS = 40;

    private ValueFormatter() {}

    public static String format(UnifiedReal value, boolean newest, Notation notation) {
        BigInteger integer = value.bigIntegerValue();
        if (integer != null) {
            if (newest && integer.abs().compareTo(INLINE_LIMIT) < 0) return integer.toString();
            BigDecimal rounded =
                    new BigDecimal(integer).round(new MathContext(DIGITS, RoundingMode.HALF_EVEN));
            return (rounded.compareTo(new BigDecimal(integer)) == 0 ? "" : "≈ ")
                    + decimal(rounded, notation);
        }
        if (newest) {
            String exact = terminatingDecimal(value);
            if (exact != null && exact.length() <= MAX_INLINE_DIGITS) return exact;
        }
        // Bounded zero detection avoids an undecidable comparison with exact zero.
        StringFloatRep rep = value.crValue().toStringFloatRep(DIGITS + 8, 10, -1000);
        if (rep.sign == 0) return "≈ 0 (|x| < 1e−1000)";
        BigDecimal approximation = rounded(rep, DIGITS);
        boolean exact =
                Math.abs((long) approximation.scale()) <= 1000
                        && RpnCalculator.parse(approximation.toString()).definitelyEquals(value);
        return (exact ? "" : "≈ ") + decimal(approximation, notation);
    }

    public static String format(
            UnifiedReal value, boolean newest, Notation notation, FractionMode fractionMode) {
        if (fractionMode != FractionMode.DECIMAL) {
            BoundedRational rational = value.boundedRationalValue();
            if (rational != null && value.bigIntegerValue() == null) {
                BigInteger[] parts = ExactFunctions.parts(value);
                if (parts[0].abs().compareTo(INLINE_LIMIT) < 0
                        && parts[1].abs().compareTo(INLINE_LIMIT) < 0) {
                    String text = fraction(rational, fractionMode == FractionMode.MIXED);
                    // Older rows have one line: use a decimal approximation instead of
                    // letting the view clip a huge numerator or whole-number part.
                    if (newest || text.length() <= DIGITS + 4) return text;
                }
            }
        }
        return format(value, newest, notation);
    }

    public static String clipboard(
            UnifiedReal value, Notation notation, FractionMode fractionMode) {
        if (fractionMode != FractionMode.DECIMAL) {
            BoundedRational rational = value.boundedRationalValue();
            if (rational != null) return fraction(rational, fractionMode == FractionMode.MIXED);
        }
        return clipboard(value, notation);
    }

    private static String fraction(BoundedRational rational, boolean mixed) {
        String text = rational.toNiceString();
        if (!mixed || !text.contains("/")) return text;
        BigInteger[] parts = ExactFunctions.parts(new UnifiedReal(rational));
        BigInteger numerator = parts[0], denominator = parts[1];
        BigInteger[] divided = numerator.abs().divideAndRemainder(denominator);
        if (divided[0].signum() == 0) return text;
        return (numerator.signum() < 0 ? "-" : "")
                + divided[0]
                + " "
                + divided[1]
                + "/"
                + denominator;
    }

    public static String clipboard(UnifiedReal value, Notation notation) {
        BigInteger integer = value.bigIntegerValue();
        if (integer != null) return integer.toString();
        String exact = terminatingDecimal(value);
        if (exact != null) return exact;
        StringFloatRep rep = value.crValue().toStringFloatRep(COPY_DIGITS + 10, 10, -1000);
        if (rep.sign == 0) return "0";
        return decimal(rounded(rep, COPY_DIGITS), notation, COPY_DIGITS).replace('−', '-');
    }

    private static String terminatingDecimal(UnifiedReal value) {
        BoundedRational rational = value.boundedRationalValue();
        int places = BoundedRational.digitsRequired(rational);
        if (places > 1000) return null;
        String text =
                new BigDecimal(rational.toStringTruncated(places))
                        .stripTrailingZeros()
                        .toPlainString();
        return text;
    }

    private static BigDecimal rounded(StringFloatRep rep, int digits) {
        BigDecimal number =
                new BigDecimal(new BigInteger(rep.mantissa))
                        .scaleByPowerOfTen(rep.exponent - rep.mantissa.length());
        if (rep.sign < 0) number = number.negate();
        // Guard digits protect against error in the constructive real's final digit.
        return number.round(new MathContext(digits, RoundingMode.HALF_EVEN));
    }

    private static String decimal(BigDecimal number, Notation notation) {
        return decimal(number, notation, DIGITS);
    }

    private static String decimal(BigDecimal number, Notation notation, int digits) {
        BigDecimal d = number.stripTrailingZeros();
        int exponent = d.precision() - d.scale() - 1;
        if (d.signum() == 0 || (exponent >= -6 && exponent < digits)) return d.toPlainString();
        int power = notation == Notation.ENGINEERING ? Math.floorDiv(exponent, 3) * 3 : exponent;
        return d.scaleByPowerOfTen(-power).toPlainString()
                + "e"
                + (power >= 0 ? "+" : "−")
                + Math.abs(power);
    }
}
