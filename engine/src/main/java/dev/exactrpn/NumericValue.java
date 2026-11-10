// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;

/** num * base^(-scale), optionally accompanied by an exact rational. */
public final class NumericValue {
    public final BigInteger num;
    public final int base, scale;
    public final boolean exact;
    // Nonzero means abs(value) < base^(-zeroBound), rather than a proof of zero.
    public final int zeroBound;
    public final RationalValue rational;

    public NumericValue(
            BigInteger num,
            int base,
            int scale,
            boolean exact,
            int zeroBound,
            RationalValue rational) {
        if (num == null
                || base < 2
                || base > 36
                || Math.abs((long) scale) > 1000000
                || zeroBound < 0
                || zeroBound != 0 && (num.signum() != 0 || exact))
            throw new IllegalArgumentException("Invalid numerical result");
        this.num = num;
        this.base = base;
        this.scale = scale;
        this.exact = exact;
        this.zeroBound = zeroBound;
        this.rational = rational;
    }
}
