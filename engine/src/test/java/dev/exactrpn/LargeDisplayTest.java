// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class LargeDisplayTest {
    @Test
    void exponentialFallbackUsesTheRequestedPrecision() {
        NumericValue value =
                new NumericValue(
                        java.math.BigInteger.valueOf(123456), 10, 3, true, 0, null);
        assertEquals(
                "1.23456e+2",
                ValueFormatter.exponentialPresentation(
                                value, ValueFormatter.Notation.SCIENTIFIC, 6)
                        .canonical);
        assertEquals(
                "≈ 1.2346e+2",
                ValueFormatter.exponentialPresentation(
                                value, ValueFormatter.Notation.SCIENTIFIC, 5)
                        .canonical);
        assertEquals(
                "≈ 123.46e+0",
                ValueFormatter.exponentialPresentation(
                                value, ValueFormatter.Notation.ENGINEERING, 5)
                        .canonical);
    }

    @Test
    void largeFractionsUseCompactOlderRowsButCopyExactly() {
        var value = RpnCalculator.parse("1e999").divide(new com.android.calculator2.UnifiedReal(7));
        for (FractionMode mode : new FractionMode[] {FractionMode.MIXED, FractionMode.IMPROPER}) {
            String display =
                    TestValueFormatter.format(
                            value, false, ValueFormatter.Notation.SCIENTIFIC, mode);
            assertTrue(
                    display.length() < 100,
                    "Older fractions must not be clipped to a prefix: " + mode);
            assertTrue(display.startsWith("≈ "));
            String copied =
                    TestValueFormatter.clipboard(value, ValueFormatter.Notation.SCIENTIFIC, mode);
            assertTrue(copied.length() >= 1000);
            assertTrue(copied.endsWith("/7"));
        }
    }

    @Test
    void displayLimitsDoNotTruncateExactDecimalCopies() {
        java.math.BigInteger numerator =
                java.math.BigInteger.TEN
                        .pow(1100)
                        .multiply(java.math.BigInteger.TWO)
                        .add(java.math.BigInteger.ONE);
        com.android.calculator2.UnifiedReal value =
                new com.android.calculator2.UnifiedReal(
                        new com.android.calculator2.BoundedRational(
                                numerator, java.math.BigInteger.TWO));
        assertTrue(
                TestValueFormatter.format(value, true, ValueFormatter.Notation.SCIENTIFIC).length()
                        < 100);
        String copied = TestValueFormatter.clipboard(value, ValueFormatter.Notation.SCIENTIFIC);
        assertEquals(1103, copied.length());
        assertTrue(copied.endsWith(".5"));
    }

    @Test
    void hugeIntegersStayExactButNeverReachTheUiInFull() throws Exception {
        TestCalculator c = new TestCalculator();
        CalculatorTestSupport.paste(c, "65536");
        c.key("10ˣ");
        for (FractionMode mode : FractionMode.values()) {
            String text =
                    TestValueFormatter.format(
                            c.values().get(0), true, ValueFormatter.Notation.SCIENTIFIC, mode);
            assertEquals("1e+65536", text);
        }
        assertEquals(
                65537,
                TestValueFormatter.clipboard(c.values().get(0), ValueFormatter.Notation.SCIENTIFIC)
                        .length());
        c.key("ENTER");
        c.key("÷");
        assertEquals(
                "1",
                TestValueFormatter.format(
                        c.values().get(0), true, ValueFormatter.Notation.SCIENTIFIC));
    }
}
