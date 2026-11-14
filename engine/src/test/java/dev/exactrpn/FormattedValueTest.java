// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

final class FormattedValueTest {
    @Test
    void exposesFractionPartsWithoutParsingItsCanonicalText() {
        NumericValue value =
                new NumericValue(
                        BigInteger.valueOf(7),
                        10,
                        0,
                        true,
                        0,
                        new RationalValue(BigInteger.valueOf(7), BigInteger.valueOf(3)));
        FormattedValue formatted =
                ValueFormatter.exactFractionPresentation(value, FractionMode.MIXED);
        assertEquals(FormattedValue.Kind.FRACTION, formatted.kind);
        assertEquals("2", formatted.whole);
        assertEquals("1", formatted.numerator);
        assertEquals("3", formatted.denominator);
        assertEquals('+', formatted.fractionOperator);
        assertEquals("2+1/3", formatted.canonical);
    }

    @Test
    void exposesExponentAndApproximationState() {
        NumericValue value =
                new NumericValue(BigInteger.valueOf(12345), 10, -10, false, 0, null);
        FormattedValue formatted =
                ValueFormatter.exponentialPresentation(
                        value, ValueFormatter.Notation.SCIENTIFIC, 4);
        assertEquals(FormattedValue.Kind.EXPONENTIAL, formatted.kind);
        assertTrue(formatted.approximate);
        assertEquals("1", formatted.whole);
        assertEquals("234", formatted.fractional);
        assertEquals("+14", formatted.exponent);
        assertEquals("1.234e+14", formatted.canonical);
    }
}
