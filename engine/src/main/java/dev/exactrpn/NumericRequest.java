// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;

/** Numerical accuracy and resource bounds; no display notation or locale. */
public final class NumericRequest {
    public static final NumericRequest DEFAULT =
            new NumericRequest(BigInteger.TEN.pow(12), 10, 1024, false);
    public static final NumericRequest PRECISE =
            new NumericRequest(BigInteger.TEN.pow(40), 10, CalculationLimits.TRANSFER_BYTES, true);
    public final BigInteger limit;
    public final int base, exactBytes;
    public final boolean requireRational;

    public NumericRequest(BigInteger limit, int base, int exactBytes, boolean requireRational) {
        if (limit == null
                || limit.compareTo(BigInteger.ONE) <= 0
                || limit.bitLength() > 32768
                || base < 2
                || base > 36
                || exactBytes < 0
                || exactBytes > CalculationLimits.TRANSFER_BYTES)
            throw new IllegalArgumentException("Invalid numerical request");
        this.limit = limit;
        this.base = base;
        this.exactBytes = exactBytes;
        this.requireRational = requireRational;
    }

    public static NumericRequest display(RadixMode mode) {
        return mode == RadixMode.DECIMAL
                ? DEFAULT
                : new NumericRequest(
                        BigInteger.valueOf(mode.radix).pow(12), mode.radix, 1024, false);
    }

    public static NumericRequest precise(RadixMode mode) {
        return mode == RadixMode.DECIMAL
                ? PRECISE
                : new NumericRequest(
                        BigInteger.valueOf(mode.radix).pow(40),
                        mode.radix,
                        CalculationLimits.TRANSFER_BYTES,
                        true);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NumericRequest)) return false;
        NumericRequest request = (NumericRequest) other;
        return base == request.base
                && exactBytes == request.exactBytes
                && requireRational == request.requireRational
                && limit.equals(request.limit);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(limit, base, exactBytes, requireRational);
    }
}
