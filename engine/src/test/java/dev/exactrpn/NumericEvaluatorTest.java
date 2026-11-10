// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

final class NumericEvaluatorTest {
    private NumericValue read(String value, int limit, int base) {
        return NumericEvaluator.read(
                RpnCalculator.parse(value),
                new NumericRequest(BigInteger.valueOf(limit), base, 1024, false));
    }

    private void scaled(
            String value, int limit, int base, long coefficient, int scale, boolean exact) {
        NumericValue result = read(value, limit, base);
        assertEquals(BigInteger.valueOf(coefficient), result.num, value);
        assertEquals(scale, result.scale, value);
        assertEquals(exact, result.exact, value);
        assertTrue(result.num.abs().compareTo(BigInteger.valueOf(limit)) < 0);
    }

    @Test
    void exclusiveBoundsAndCanonicalScales() {
        scaled("123", 100000, 10, 123, 0, true);
        scaled("1.23", 100000, 10, 123, 2, true);
        scaled("123000", 100000, 10, 123, -3, true);
        scaled("-123000", 100000, 10, -123, -3, true);
        scaled("99999", 100000, 10, 99999, 0, true);
        scaled("100000", 100000, 10, 1, -5, true);
    }

    @Test
    void roundsHalfEvenAndRecomputesCarriesAtCoarserScales() {
        scaled("1.225", 1000, 10, 122, 2, false);
        scaled("1.235", 1000, 10, 124, 2, false);
        scaled("-1.225", 1000, 10, -122, 2, false);
        scaled("99999.5", 100000, 10, 1, -5, false);
        scaled("99999.4", 100000, 10, 99999, 0, false);
        scaled("1999.5", 2000, 10, 2, -3, false);
    }

    @Test
    void binaryHexAndDozenalUseTheSameContract() {
        scaled("0.125", 256, 2, 1, 3, true);
        scaled("4096", 256, 16, 1, -3, true);
        scaled("0.0625", 256, 16, 1, 1, true);
        scaled("1728", 144, 12, 1, -3, true);
        scaled("18", 2, 36, 0, -1, false);
        for (int base : new int[] {2, 10, 12, 16}) {
            NumericValue value =
                    NumericEvaluator.read(
                            UnifiedReal.PI,
                            new NumericRequest(BigInteger.valueOf(2000), base, 0, false));
            assertTrue(value.num.abs().compareTo(BigInteger.valueOf(2000)) < 0);
            assertFalse(value.exact);
            assertNull(value.rational);
        }
    }

    @Test
    void exactDataIsIndependentOfApproximationPrecisionAndBudget() {
        UnifiedReal third = UnifiedReal.ONE.divide(new UnifiedReal(3));
        NumericValue value = NumericEvaluator.read(third, NumericRequest.DEFAULT);
        assertEquals(BigInteger.ONE, value.rational.numerator);
        assertEquals(BigInteger.valueOf(3), value.rational.denominator);
        assertFalse(value.exact);
        NumericRequest noExact = new NumericRequest(BigInteger.TEN.pow(5), 10, 0, false);
        assertNull(NumericEvaluator.read(third, noExact).rational);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NumericEvaluator.read(
                                third, new NumericRequest(BigInteger.TEN.pow(5), 10, 0, true)));
        NumericValue huge = read("123e999", 100000, 10);
        assertEquals(BigInteger.valueOf(123), huge.num);
        assertEquals(-999, huge.scale);
        assertTrue(huge.exact);
    }

    @Test
    void nearZeroIsABoundNotAnExactZero() {
        UnifiedReal unknown =
                UnifiedReal.PI.add(UnifiedReal.E).subtract(UnifiedReal.E.add(UnifiedReal.PI));
        NumericValue value = NumericEvaluator.read(unknown, NumericRequest.DEFAULT);
        assertEquals(1000, value.zeroBound);
        assertFalse(value.exact);
        NumericValue zero = NumericEvaluator.read(UnifiedReal.ZERO, NumericRequest.DEFAULT);
        assertEquals(0, zero.zeroBound);
        assertTrue(zero.exact);
    }

    @Test
    void validatesRequestBoundsAndSupportsLargeIntegerLimits() {
        for (int base : new int[] {1, 37})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new NumericRequest(BigInteger.TEN, base, 0, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NumericRequest(BigInteger.ONE, 10, 0, false));
        NumericValue value =
                NumericEvaluator.read(
                        UnifiedReal.PI, new NumericRequest(BigInteger.TEN.pow(100), 10, 0, false));
        assertTrue(value.num.abs().compareTo(BigInteger.TEN.pow(100)) < 0);
        assertTrue(value.num.abs().toString().length() >= 99);
    }
}
