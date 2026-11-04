// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;
import com.hp.creals.StringFloatRep;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Display-only approximations. Never replace the exact stack values with these strings. */
public final class ValueFormatter {
    public enum Notation { SCIENTIFIC, ENGINEERING }
    private static final int DIGITS = 12;
    private ValueFormatter() {}
    public static String format(UnifiedReal value, boolean newest, Notation notation) {
        BigInteger integer = value.bigIntegerValue();
        if (integer != null) {
            if (newest) return integer.toString();
            BigDecimal rounded = new BigDecimal(integer).round(new MathContext(DIGITS, RoundingMode.HALF_EVEN));
            return (rounded.compareTo(new BigDecimal(integer)) == 0 ? "" : "≈ ") + decimal(rounded, notation);
        }
        // Bounded zero detection avoids an undecidable comparison with exact zero.
        StringFloatRep rep = value.crValue().toStringFloatRep(DIGITS, 10, -1000);
        if (rep.sign == 0) return "≈ 0 (|x| < 1e−1000)";
        BigDecimal approximation = new BigDecimal(new BigInteger(rep.mantissa))
            .scaleByPowerOfTen(rep.exponent - rep.mantissa.length());
        if (rep.sign < 0) approximation = approximation.negate();
        boolean exact = Math.abs((long)approximation.scale()) <= 1000
            && RpnCalculator.parse(approximation.toString()).definitelyEquals(value);
        return (exact ? "" : "≈ ") + decimal(approximation, notation);
    }
    public static String clipboard(UnifiedReal value, Notation notation) {
        String text = format(value,true,notation);
        if (text.startsWith("≈ ")) text = text.substring(2);
        if (text.startsWith("0 (")) return "0";
        return text.replace('−','-');
    }
    private static String decimal(BigDecimal number, Notation notation) {
        BigDecimal d = number.stripTrailingZeros();
        int exponent = d.precision() - d.scale() - 1;
        if (d.signum() == 0 || (exponent >= -6 && exponent < DIGITS)) return d.toPlainString();
        int power = notation == Notation.ENGINEERING ? Math.floorDiv(exponent, 3) * 3 : exponent;
        return d.scaleByPowerOfTen(-power).toPlainString() + "e" + (power >= 0 ? "+" : "−") + Math.abs(power);
    }
}

