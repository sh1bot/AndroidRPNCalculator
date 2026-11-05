// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

final class MoreFunctionsTest {
    private UnifiedReal apply(String value, String operation) {
        RpnCalculator calculator = new RpnCalculator();
        calculator.paste(value);
        calculator.key(operation);
        return calculator.values().get(0);
    }

    @Test
    void baseTenLogarithmIsExactForPowersOfTen() {
        for (String[] sample :
                new String[][] {{"1", "0"}, {"10", "1"}, {"1000", "3"}, {"0.01", "-2"}})
            assertTrue(
                    apply(sample[0], "log10").definitelyEquals(RpnCalculator.parse(sample[1])),
                    sample[0]);
        assertTrue(apply("3", "10ˣ").definitelyEquals(new UnifiedReal(1000)));
        assertTrue(apply("-2", "10ˣ").definitelyEquals(RpnCalculator.parse("0.01")));
        assertThrows(ArithmeticException.class, () -> apply("0", "log10"));
        assertThrows(ArithmeticException.class, () -> apply("-1", "log10"));
    }

    @Test
    void floorHandlesSignsIntegersAndLargeExactValues() {
        for (String[] sample :
                new String[][] {
                    {"2.9", "2"},
                    {"-2.1", "-3"},
                    {"-2", "-2"},
                    {"0", "0"},
                    {"-0.000000000000000000001", "-1"},
                    {"1000000000000000000000000.99", "1000000000000000000000000"}
                })
            assertEquals(
                    new BigInteger(sample[1]),
                    apply(sample[0], "floor").bigIntegerValue(),
                    sample[0]);
        assertEquals(BigInteger.valueOf(3), ExactFunctions.floor(UnifiedReal.PI).bigIntegerValue());
        assertEquals(
                BigInteger.valueOf(-4),
                ExactFunctions.floor(UnifiedReal.PI.negate()).bigIntegerValue());
        assertEquals(
                BigInteger.ONE, ExactFunctions.floor(UnifiedReal.TWO.sqrt()).bigIntegerValue());
    }

    @Test
    void goldenRatioIsExactAndSurvivesRestore() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("φ");
        UnifiedReal phi = calculator.values().get(0);
        assertEquals(
                "1.61803398874989484820458683436563811772",
                ValueFormatter.clipboard(phi, ValueFormatter.Notation.SCIENTIFIC));
        UnifiedReal restored = RpnCalculator.load(calculator.save()).values().get(0);
        assertEquals(
                ValueFormatter.clipboard(phi, ValueFormatter.Notation.SCIENTIFIC),
                ValueFormatter.clipboard(restored, ValueFormatter.Notation.SCIENTIFIC));
        calculator.key("UNDO");
        assertTrue(calculator.values().isEmpty());
        calculator.key("REDO");
        assertTrue(calculator.values().get(0).definitelyEquals(phi));
    }

    @Test
    void newFunctionsAreTransactionalAndUndoable() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.paste("2.9");
        calculator.key("floor");
        assertEquals("2", calculator.values().get(0).bigIntegerValue().toString());
        calculator = RpnCalculator.load(calculator.save());
        calculator.key("UNDO");
        assertEquals("2.9", calculator.entry());
        assertTrue(calculator.values().isEmpty());
        RpnCalculator invalid = new RpnCalculator();
        invalid.paste("-1");
        assertThrows(ArithmeticException.class, () -> invalid.key("log10"));
        assertEquals("-1", invalid.entry());
        assertTrue(invalid.values().isEmpty());
    }
}
