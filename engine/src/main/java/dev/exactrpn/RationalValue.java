// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;

/** Canonical, locale-independent exact data. */
public final class RationalValue {
    public final BigInteger numerator, denominator;

    public RationalValue(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0)
            throw CalculationError.arithmetic(CalculationError.Code.DIVIDE_BY_ZERO);
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger divisor = numerator.gcd(denominator);
        this.numerator = numerator.divide(divisor);
        this.denominator = denominator.divide(divisor);
    }

    public int byteSize() {
        return numerator.toByteArray().length + denominator.toByteArray().length;
    }
}
