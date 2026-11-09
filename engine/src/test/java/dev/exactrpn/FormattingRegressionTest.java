// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class FormattingRegressionTest extends RegressionSupport {
    @Test
    void decimalFormattingAndPrecision() throws Exception {
        TestCalculator c = new TestCalculator();
        assertEquals(
                "0.3",
                ValueFormatter.format(RpnCalculator.parse("0.3"), true, sci),
                "exact decimal display");
        assertEquals(
                "≈ 56123456789.5",
                ValueFormatter.format(RpnCalculator.parse("56123456789.456"), false, sci),
                "large decimal rounds with guard digits");
        for (int tail = 100; tail < 1000; tail += 7) {
            String input = "56789012345." + tail;
            String expected =
                    new java.math.BigDecimal(input)
                            .round(new java.math.MathContext(12, java.math.RoundingMode.HALF_EVEN))
                            .stripTrailingZeros()
                            .toPlainString();
            assertEquals(
                    (new java.math.BigDecimal(input).compareTo(new java.math.BigDecimal(expected))
                                            == 0
                                    ? ""
                                    : "≈ ")
                            + expected,
                    ValueFormatter.format(RpnCalculator.parse(input), false, sci),
                    "large decimal rounding " + input);
        }
        assertEquals(
                "56565642624.456",
                ValueFormatter.clipboard(RpnCalculator.parse("56565642624.456"), sci),
                "copy preserves large exact decimal");
        assertEquals(
                "-0.0000000000001234567890123456",
                ValueFormatter.clipboard(
                        RpnCalculator.parse("-0.0000000000001234567890123456"), eng),
                "copy preserves tiny exact decimal");
        assertEquals(
                "0.125",
                ValueFormatter.clipboard(
                        RpnCalculator.parse("1").divide(RpnCalculator.parse("8")), sci),
                "copy exact terminating rational result");
        assertEquals(
                "56565642624.456",
                ValueFormatter.format(RpnCalculator.parse("56565642624.456"), true, sci),
                "newest terminating decimal stays exact");
        TestCalculator toggled = new TestCalculator();
        number(toggled, "56565642624.456");
        toggled.key("ENTER");
        for (int i = 0; i < 10; i++) {
            CalculatorTestSupport.formattedValues(toggled, sci, FractionMode.IMPROPER);
            CalculatorTestSupport.formattedValues(toggled, sci, FractionMode.DECIMAL);
        }
        assertTrue(
                top(toggled).definitelyEquals(RpnCalculator.parse("56565642624.456")),
                "repeated fraction formatting preserves exact input");
        assertEquals(
                "56565642624.456",
                ValueFormatter.clipboard(top(toggled), sci),
                "copy stays exact after fraction toggles");

        assertEquals(
                "123456789012345678901234567890",
                ValueFormatter.format(huge, true, sci),
                "newest integer expanded");
        assertEquals(
                "≈ 1.23456789012e+29",
                ValueFormatter.format(huge, false, sci),
                "older integer compact scientific");
        assertEquals(
                "≈ 123.456789012e+27",
                ValueFormatter.format(huge, false, eng),
                "engineering multiple of three");
        assertEquals(
                "-10e−9",
                ValueFormatter.format(RpnCalculator.parse("-0.00000001"), false, eng),
                "negative engineering exponent");
        assertEquals("0", ValueFormatter.format(UnifiedReal.ZERO, true, sci), "zero formatting");
        String exponential = ValueFormatter.format(RpnCalculator.parse("234.56").exp(), true, sci);
        assertTrue(
                exponential.length() < 30 && exponential.contains("e+101"),
                "large noninteger exponential stays compact");
        assertEquals(
                "1e−20",
                ValueFormatter.format(RpnCalculator.parse("1e-20"), false, sci),
                "tiny rational remains visible");

        assertEquals(
                "≈ 0.333333333333",
                ValueFormatter.format(third, true, sci),
                "recurring fraction limited precision");
        keys(c, "CLEAR", "1", "eˣ");
        assertTrue(top(c).definitelyEquals(UnifiedReal.E), "e to x key");
        assertTrue(
                third.multiply(new UnifiedReal(3)).definitelyOne(),
                "display retains exact stack arithmetic");
    }

    @Test
    void fractionFormattingAndCopy() throws Exception {
        assertEquals(
                "3/10",
                ValueFormatter.format(RpnCalculator.parse("0.3"), true, sci, FractionMode.IMPROPER),
                "fraction mode displays exact decimal rational");
        assertEquals(
                "1/3",
                ValueFormatter.format(third, true, sci, FractionMode.IMPROPER),
                "fraction mode preserves recurring rational");
        assertEquals(
                "-3/4",
                ValueFormatter.format(
                        new UnifiedReal(new com.android.calculator2.BoundedRational(6, -8)),
                        true,
                        sci,
                        FractionMode.IMPROPER),
                "fraction sign and reduction");
        assertEquals(
                "2",
                ValueFormatter.format(UnifiedReal.TWO, true, sci, FractionMode.IMPROPER),
                "fraction mode leaves integers natural");
        assertEquals(
                "0",
                ValueFormatter.format(UnifiedReal.ZERO, true, sci, FractionMode.IMPROPER),
                "fraction mode zero");
        assertEquals(
                huge.bigIntegerValue().toString(),
                ValueFormatter.format(huge, true, sci, FractionMode.IMPROPER),
                "fraction mode retains full newest integer");
        assertTrue(
                ValueFormatter.format(huge, false, sci, FractionMode.IMPROPER).contains("e+29"),
                "older integer remains compact in fraction mode");

        assertEquals(
                ValueFormatter.format(rootTwo, true, sci),
                ValueFormatter.format(rootTwo, true, sci, FractionMode.IMPROPER),
                "irrational fraction fallback");
        assertEquals(
                ValueFormatter.format(UnifiedReal.PI, true, sci),
                ValueFormatter.format(UnifiedReal.PI, true, sci, FractionMode.IMPROPER),
                "pi fraction fallback");
        assertEquals(
                "1/100000000000000000000",
                ValueFormatter.format(
                        RpnCalculator.parse("1e-20"), true, eng, FractionMode.IMPROPER),
                "tiny rational remains exact fraction");
        TestCalculator fractions = new TestCalculator();
        keys(fractions, "1", "ENTER", "3", "÷");
        assertEquals(
                "1/3",
                CalculatorTestSupport.formattedValues(fractions, sci, FractionMode.IMPROPER).get(0),
                "stack fraction display");
        assertEquals(
                "≈ 0.333333333333",
                CalculatorTestSupport.formattedValues(fractions, sci, FractionMode.DECIMAL).get(0),
                "stack decimal display");
        assertTrue(
                top(fractions).multiply(new UnifiedReal(3)).definitelyOne(),
                "display mode leaves exact arithmetic unchanged");
        assertEquals(
                "1/3",
                ValueFormatter.clipboard(top(fractions), sci, FractionMode.IMPROPER),
                "fraction display copies exact fraction");
        assertEquals(
                "1/8",
                ValueFormatter.clipboard(RpnCalculator.parse("0.125"), sci, FractionMode.IMPROPER),
                "terminating decimal copies as fraction");
        assertEquals(
                "-3/4",
                ValueFormatter.clipboard(
                        new UnifiedReal(new com.android.calculator2.BoundedRational(6, -8)),
                        sci,
                        FractionMode.IMPROPER),
                "copied fraction is reduced with positive denominator");
        assertEquals(
                "2",
                ValueFormatter.clipboard(UnifiedReal.TWO, sci, FractionMode.IMPROPER),
                "fraction mode copies integer without denominator");
        assertEquals(
                ValueFormatter.clipboard(UnifiedReal.PI, sci, FractionMode.DECIMAL),
                ValueFormatter.clipboard(UnifiedReal.PI, sci, FractionMode.IMPROPER),
                "fraction mode copies irrational decimal fallback");
        assertEquals(
                "0.3333333333333333333333333333333333333333",
                ValueFormatter.clipboard(top(fractions), sci, FractionMode.DECIMAL),
                "decimal mode retains decimal copy");
    }

    @Test
    void mixedFractionsAndRemainder() throws Exception {
        UnifiedReal sevenThirds = RpnCalculator.parse("7").divide(RpnCalculator.parse("3"));
        assertEquals(
                "7/3",
                ValueFormatter.format(sevenThirds, true, sci, FractionMode.IMPROPER),
                "improper fraction display");
        assertEquals(
                "2 1/3",
                ValueFormatter.format(sevenThirds, true, sci, FractionMode.MIXED),
                "mixed fraction display");
        assertEquals(
                "-2 1/3",
                ValueFormatter.clipboard(sevenThirds.negate(), sci, FractionMode.MIXED),
                "mixed negative sign applies to whole value");
        assertEquals(
                "-1/3",
                ValueFormatter.format(third.negate(), true, sci, FractionMode.MIXED),
                "proper fraction remains proper in mixed mode");
        assertEquals(
                "2",
                ValueFormatter.format(UnifiedReal.TWO, true, sci, FractionMode.MIXED),
                "mixed mode leaves integer natural");
        assertEquals(
                ValueFormatter.format(UnifiedReal.PI, true, sci),
                ValueFormatter.format(UnifiedReal.PI, true, sci, FractionMode.MIXED),
                "mixed mode irrational fallback");
        for (String[] example :
                new String[][] {
                    {"7", "3", "1"},
                    {"-7", "3", "-1"},
                    {"7", "-3", "1"},
                    {"-7", "-3", "-1"},
                    {"5.5", "2", "1.5"},
                    {"0.3", "0.1", "0"}
                }) {
            TestCalculator remainder = new TestCalculator();
            CalculatorTestSupport.paste(remainder, example[0]);
            remainder.key("ENTER");
            CalculatorTestSupport.paste(remainder, example[1]);
            remainder.key("rem");
            assertTrue(
                    top(remainder).definitelyEquals(RpnCalculator.parse(example[2])),
                    "exact remainder " + example[0] + " rem " + example[1]);
            TestCalculator remainderSaved = TestCalculator.load(remainder.save());
            assertTrue(
                    top(remainderSaved).definitelyEquals(top(remainder)),
                    "remainder survives restore");
        }
        TestCalculator modular = new TestCalculator();
        CalculatorTestSupport.paste(modular, "-7");
        modular.key("ENTER");
        CalculatorTestSupport.paste(modular, "3");
        modular.key("mod");
        assertTrue(top(modular).definitelyEquals(UnifiedReal.TWO), "modulo has divisor sign");
        modular.key("UNDO");
        assertTrue(
                modular.entry().equals("3") && top(modular).definitelyEquals(new UnifiedReal(-7)),
                "remainder undo restores pending divisor");
        modular.key("REDO");
        assertTrue(top(modular).definitelyEquals(UnifiedReal.TWO), "remainder redo");
        TestCalculator negativeMod = new TestCalculator();
        CalculatorTestSupport.paste(negativeMod, "7");
        negativeMod.key("ENTER");
        CalculatorTestSupport.paste(negativeMod, "-3");
        negativeMod.key("mod");
        assertTrue(
                top(negativeMod).definitelyEquals(new UnifiedReal(-2)), "negative-divisor modulo");
        TestCalculator zeroDivisor = new TestCalculator();
        keys(zeroDivisor, "7", "ENTER", "0");
        try {
            zeroDivisor.key("rem");
            throw new AssertionError("zero remainder divisor accepted");
        } catch (ArithmeticException expected) {
        }
        assertTrue(
                top(zeroDivisor).definitelyZero()
                        && zeroDivisor.values().size() == 2
                        && !zeroDivisor.isEditing(),
                "zero-divisor remainder leaves state intact");
        TestCalculator irrationalRem = new TestCalculator();
        keys(irrationalRem, "π", "2");
        try {
            irrationalRem.key("rem");
            throw new AssertionError("irrational remainder accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(
                top(irrationalRem).definitelyEquals(UnifiedReal.TWO)
                        && irrationalRem.values().size() == 2
                        && !irrationalRem.isEditing(),
                "irrational remainder is rejected without changing state");
    }
}
