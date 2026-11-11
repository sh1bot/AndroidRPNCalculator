// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

final class MoreFunctionsTest {
    private UnifiedReal apply(String value, String operation) {
        TestCalculator calculator = new TestCalculator();
        CalculatorTestSupport.paste(calculator, value);
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
    void configuredRadixLogarithmsUseTheirRadixAsTheBase() {
        assertTrue(apply("144", "log12").definitelyEquals(new UnifiedReal(2)));
        assertTrue(apply("3", "12ˣ").definitelyEquals(new UnifiedReal(1728)));
        assertTrue(apply("256", "log16").definitelyEquals(new UnifiedReal(2)));
        assertTrue(apply("3", "16ˣ").definitelyEquals(new UnifiedReal(4096)));
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
    void ceilingRoundsUpAcrossZeroAndAtIntegers() {
        assertEquals(BigInteger.valueOf(3), apply("2.1", "ceil").bigIntegerValue());
        assertEquals(BigInteger.valueOf(-2), apply("-2.9", "ceil").bigIntegerValue());
        assertEquals(BigInteger.valueOf(-2), apply("-2", "ceil").bigIntegerValue());
        assertEquals(BigInteger.ZERO, apply("-0.01", "ceil").bigIntegerValue());
    }

    @Test
    void tauRetainsPiIdentitiesAndRestoresExactly() throws Exception {
        TestCalculator calculator = new TestCalculator();
        calculator.key("τ");
        UnifiedReal tau = calculator.values().get(0);
        assertTrue(tau.divide(UnifiedReal.TWO).definitelyEquals(UnifiedReal.PI));
        assertTrue(tau.sin().definitelyZero());
        assertTrue(tau.cos().definitelyOne());
        UnifiedReal restored = TestCalculator.load(calculator.save()).values().get(0);
        assertTrue(restored.divide(UnifiedReal.TWO).definitelyEquals(UnifiedReal.PI));
        assertTrue(restored.sin().definitelyZero());
        assertTrue(restored.cos().definitelyOne());
        calculator.key("UNDO");
        assertTrue(calculator.values().isEmpty());
        calculator.key("REDO");
        assertTrue(calculator.values().get(0).definitelyEquals(tau));
    }

    @Test
    void goldenRatioIsExactAndSurvivesRestore() throws Exception {
        TestCalculator calculator = new TestCalculator();
        calculator.key("φ");
        UnifiedReal phi = calculator.values().get(0);
        assertEquals(
                "1.61803398874989484820458683436563811772",
                TestValueFormatter.clipboard(phi, ValueFormatter.Notation.SCIENTIFIC));
        UnifiedReal restored = TestCalculator.load(calculator.save()).values().get(0);
        assertEquals(
                TestValueFormatter.clipboard(phi, ValueFormatter.Notation.SCIENTIFIC),
                TestValueFormatter.clipboard(restored, ValueFormatter.Notation.SCIENTIFIC));
        calculator.key("UNDO");
        assertTrue(calculator.values().isEmpty());
        calculator.key("REDO");
        assertTrue(calculator.values().get(0).definitelyEquals(phi));
    }

    @Test
    void newFunctionsAreTransactionalAndUndoable() throws Exception {
        TestCalculator calculator = new TestCalculator();
        CalculatorTestSupport.paste(calculator, "2.9");
        calculator.key("floor");
        assertEquals("2", calculator.values().get(0).bigIntegerValue().toString());
        calculator = TestCalculator.load(calculator.save());
        calculator.key("UNDO");
        assertEquals("2.9", calculator.entry());
        assertTrue(calculator.values().isEmpty());
        TestCalculator invalid = new TestCalculator();
        CalculatorTestSupport.paste(invalid, "-1");
        assertThrows(ArithmeticException.class, () -> invalid.key("log10"));
        assertFalse(invalid.isEditing());
        assertEquals("-1", invalid.values().get(0).bigIntegerValue().toString());
    }
}
