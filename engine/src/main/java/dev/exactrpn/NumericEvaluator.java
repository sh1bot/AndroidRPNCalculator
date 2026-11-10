// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;

import com.hp.creals.StringFloatRep;

import java.math.BigInteger;

/** Evaluates numbers, leaving notation and language to callers. */
public final class NumericEvaluator {
    private NumericEvaluator() {}

    public static NumericValue read(UnifiedReal value, NumericRequest request) {
        RationalValue rational = null;
        if (value.boundedRationalValue() != null) {
            BigInteger[] parts = ExactFunctions.parts(value);
            rational = new RationalValue(parts[0], parts[1]);
            NumericValue approximation = round(rational, request);
            if (rational.byteSize() > request.exactBytes) {
                if (request.requireRational)
                    throw CalculationError.invalid(CalculationError.Code.TRANSFER_TOO_LARGE);
                rational = null;
            }
            return new NumericValue(
                    approximation.num,
                    request.base,
                    approximation.scale,
                    approximation.exact,
                    0,
                    rational);
        }
        int digits = request.limit.toString(request.base).length() + 12;
        StringFloatRep rep = value.crValue().toStringFloatRep(digits, request.base, -1000);
        if (rep.sign == 0)
            return new NumericValue(BigInteger.ZERO, request.base, 0, false, 1000, null);
        BigInteger mantissa = new BigInteger(rep.mantissa, request.base);
        if (rep.sign < 0) mantissa = mantissa.negate();
        int scale = rep.mantissa.length() - rep.exponent;
        BigInteger power = BigInteger.valueOf(request.base).pow(Math.abs(scale));
        NumericValue result =
                round(
                        new RationalValue(
                                scale < 0 ? mantissa.multiply(power) : mantissa,
                                scale > 0 ? power : BigInteger.ONE),
                        request);
        BigInteger factor = BigInteger.valueOf(request.base).pow(Math.abs(result.scale));
        UnifiedReal rounded =
                new UnifiedReal(
                        new BoundedRational(
                                result.scale < 0 ? result.num.multiply(factor) : result.num,
                                result.scale > 0 ? factor : BigInteger.ONE));
        return new NumericValue(
                result.num, request.base, result.scale, value.definitelyEquals(rounded), 0, null);
    }

    private static NumericValue round(RationalValue value, NumericRequest request) {
        BigInteger numerator = value.numerator.abs(), denominator = value.denominator;
        if (numerator.signum() == 0)
            return new NumericValue(BigInteger.ZERO, request.base, 0, true, 0, null);
        int scale =
                request.limit.toString(request.base).length()
                        - numerator.toString(request.base).length()
                        + denominator.toString(request.base).length();
        BigInteger num = rounded(numerator, denominator, request.base, scale);
        while (num.compareTo(request.limit) >= 0)
            num = rounded(numerator, denominator, request.base, --scale);
        while (rounded(numerator, denominator, request.base, scale + 1).compareTo(request.limit)
                < 0) num = rounded(numerator, denominator, request.base, ++scale);
        BigInteger radix = BigInteger.valueOf(request.base);
        while (num.signum() != 0 && num.mod(radix).signum() == 0) {
            num = num.divide(radix);
            scale--;
        }
        if (value.numerator.signum() < 0) num = num.negate();
        BigInteger power = radix.pow(Math.abs(scale));
        boolean exact =
                scale < 0
                        ? num.multiply(power).multiply(value.denominator).equals(value.numerator)
                        : num.multiply(value.denominator).equals(value.numerator.multiply(power));
        return new NumericValue(num, request.base, scale, exact, 0, null);
    }

    private static BigInteger rounded(
            BigInteger numerator, BigInteger denominator, int base, int scale) {
        BigInteger power = BigInteger.valueOf(base).pow(Math.abs(scale));
        if (scale > 0) numerator = numerator.multiply(power);
        else denominator = denominator.multiply(power);
        BigInteger[] parts = numerator.divideAndRemainder(denominator);
        int halfway = parts[1].shiftLeft(1).compareTo(denominator);
        return halfway > 0 || halfway == 0 && parts[0].testBit(0)
                ? parts[0].add(BigInteger.ONE)
                : parts[0];
    }
}
