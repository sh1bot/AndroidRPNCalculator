// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;
import com.android.calculator2.BoundedRational;
import com.hp.creals.StringFloatRep;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Display-only approximations. Never replace the exact stack values with these strings. */
public final class ValueFormatter {
    public enum Notation { SCIENTIFIC, ENGINEERING }
    private static final int DIGITS = 12;
    private static final int COPY_DIGITS = 40;
    private ValueFormatter() {}
    public static String format(UnifiedReal value, boolean newest, Notation notation) {
        BigInteger integer = value.bigIntegerValue();
        if (integer != null) {
            if (newest) return integer.toString();
            BigDecimal rounded = new BigDecimal(integer).round(new MathContext(DIGITS, RoundingMode.HALF_EVEN));
            return (rounded.compareTo(new BigDecimal(integer)) == 0 ? "" : "≈ ") + decimal(rounded, notation);
        }
        if(newest) {
            BoundedRational rational=value.boundedRationalValue();
            int places=BoundedRational.digitsRequired(rational);
            if(places<=1000) return new BigDecimal(rational.toStringTruncated(places)).stripTrailingZeros().toPlainString();
        }
        // Bounded zero detection avoids an undecidable comparison with exact zero.
        StringFloatRep rep = value.crValue().toStringFloatRep(DIGITS + 8, 10, -1000);
        if (rep.sign == 0) return "≈ 0 (|x| < 1e−1000)";
        BigDecimal approximation = new BigDecimal(new BigInteger(rep.mantissa))
            .scaleByPowerOfTen(rep.exponent - rep.mantissa.length());
        if (rep.sign < 0) approximation = approximation.negate();
        // CR guarantees an approximation, not correct rounding of its final digit.
        approximation = approximation.round(new MathContext(DIGITS, RoundingMode.HALF_EVEN));
        boolean exact = Math.abs((long)approximation.scale()) <= 1000
            && RpnCalculator.parse(approximation.toString()).definitelyEquals(value);
        return (exact ? "" : "≈ ") + decimal(approximation, notation);
    }
    public static String format(UnifiedReal value, boolean newest, Notation notation, boolean fractions) { return format(value,newest,notation,fractions?1:0); }
    public static String format(UnifiedReal value, boolean newest, Notation notation, int fractionMode) {
        if(fractionMode!=0) {
            BoundedRational rational=value.boundedRationalValue();
            if(rational!=null && value.bigIntegerValue()==null) return fraction(rational,fractionMode==2);
        }
        return format(value,newest,notation);
    }
    public static String clipboard(UnifiedReal value, Notation notation, boolean fractions) { return clipboard(value,notation,fractions?1:0); }
    public static String clipboard(UnifiedReal value, Notation notation, int fractionMode) {
        if(fractionMode!=0) {
            BoundedRational rational=value.boundedRationalValue();
            if(rational!=null) return fraction(rational,fractionMode==2);
        }
        return clipboard(value,notation);
    }
    private static String fraction(BoundedRational rational,boolean mixed) {
        String text=rational.toNiceString();
        if(!mixed || !text.contains("/")) return text;
        String[] parts=text.split("/"); BigInteger numerator=new BigInteger(parts[0]), denominator=new BigInteger(parts[1]);
        BigInteger[] divided=numerator.abs().divideAndRemainder(denominator);
        if(divided[0].signum()==0) return text;
        return (numerator.signum()<0 ? "-" : "")+divided[0]+" "+divided[1]+"/"+denominator;
    }
    public static String clipboard(UnifiedReal value, Notation notation) {
        BigInteger integer = value.bigIntegerValue();
        if(integer!=null) return integer.toString();
        BoundedRational rational = value.boundedRationalValue();
        int places = BoundedRational.digitsRequired(rational);
        if(places<=1000) return new BigDecimal(rational.toStringTruncated(places)).stripTrailingZeros().toPlainString();
        StringFloatRep rep=value.crValue().toStringFloatRep(COPY_DIGITS+10,10,-1000);
        if(rep.sign==0) return "0";
        BigDecimal number=new BigDecimal(new BigInteger(rep.mantissa)).scaleByPowerOfTen(rep.exponent-rep.mantissa.length());
        if(rep.sign<0) number=number.negate();
        return decimal(number.round(new MathContext(COPY_DIGITS,RoundingMode.HALF_EVEN)),notation,COPY_DIGITS).replace('−','-');
    }

    private static String decimal(BigDecimal number, Notation notation) { return decimal(number,notation,DIGITS); }
    private static String decimal(BigDecimal number, Notation notation,int digits) {
        BigDecimal d = number.stripTrailingZeros();
        int exponent = d.precision() - d.scale() - 1;
        if (d.signum() == 0 || (exponent >= -6 && exponent < digits)) return d.toPlainString();
        int power = notation == Notation.ENGINEERING ? Math.floorDiv(exponent, 3) * 3 : exponent;
        return d.scaleByPowerOfTen(-power).toPlainString() + "e" + (power >= 0 ? "+" : "−") + Math.abs(power);
    }
}


