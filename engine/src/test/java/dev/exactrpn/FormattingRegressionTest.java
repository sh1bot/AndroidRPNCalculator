// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class FormattingRegressionTest extends RegressionSupport {
    @Test
    void decimalFormattingAndPrecision() throws Exception {
        RpnCalculator c = new RpnCalculator();
        check(
                ValueFormatter.format(RpnCalculator.parse("0.3"), true, sci).equals("0.3"),
                "exact decimal display");
        check(
                ValueFormatter.format(RpnCalculator.parse("56123456789.456"), false, sci)
                        .equals("≈ 56123456789.5"),
                "large decimal rounds with guard digits");
        for (int tail = 100; tail < 1000; tail += 7) {
            String input = "56789012345." + tail;
            String expected =
                    new java.math.BigDecimal(input)
                            .round(new java.math.MathContext(12, java.math.RoundingMode.HALF_EVEN))
                            .stripTrailingZeros()
                            .toPlainString();
            check(
                    ValueFormatter.format(RpnCalculator.parse(input), false, sci)
                            .equals(
                                    (new java.math.BigDecimal(input)
                                                                    .compareTo(
                                                                            new java.math
                                                                                    .BigDecimal(
                                                                                    expected))
                                                            == 0
                                                    ? ""
                                                    : "≈ ")
                                            + expected),
                    "large decimal rounding " + input);
        }
        check(
                ValueFormatter.clipboard(RpnCalculator.parse("56565642624.456"), sci)
                        .equals("56565642624.456"),
                "copy preserves large exact decimal");
        check(
                ValueFormatter.clipboard(
                                RpnCalculator.parse("-0.0000000000001234567890123456"), eng)
                        .equals("-0.0000000000001234567890123456"),
                "copy preserves tiny exact decimal");
        check(
                ValueFormatter.clipboard(
                                RpnCalculator.parse("1").divide(RpnCalculator.parse("8")), sci)
                        .equals("0.125"),
                "copy exact terminating rational result");
        check(
                ValueFormatter.format(RpnCalculator.parse("56565642624.456"), true, sci)
                        .equals("56565642624.456"),
                "newest terminating decimal stays exact");
        RpnCalculator toggled = new RpnCalculator();
        number(toggled, "56565642624.456");
        toggled.key("ENTER");
        for (int i = 0; i < 10; i++) {
            toggled.formattedValues(sci, FractionMode.IMPROPER);
            toggled.formattedValues(sci, FractionMode.DECIMAL);
        }
        check(
                top(toggled).definitelyEquals(RpnCalculator.parse("56565642624.456")),
                "repeated fraction formatting preserves exact input");
        check(
                ValueFormatter.clipboard(top(toggled), sci).equals("56565642624.456"),
                "copy stays exact after fraction toggles");

        check(
                ValueFormatter.format(huge, true, sci).equals("123456789012345678901234567890"),
                "newest integer expanded");
        check(
                ValueFormatter.format(huge, false, sci).equals("≈ 1.23456789012e+29"),
                "older integer compact scientific");
        check(
                ValueFormatter.format(huge, false, eng).equals("≈ 123.456789012e+27"),
                "engineering multiple of three");
        check(
                ValueFormatter.format(RpnCalculator.parse("-0.00000001"), false, eng)
                        .equals("-10e−9"),
                "negative engineering exponent");
        assertEquals("0", ValueFormatter.format(UnifiedReal.ZERO, true, sci), "zero formatting");
        String exponential = ValueFormatter.format(RpnCalculator.parse("234.56").exp(), true, sci);
        check(
                exponential.length() < 30 && exponential.contains("e+101"),
                "large noninteger exponential stays compact");
        check(
                ValueFormatter.format(RpnCalculator.parse("1e-20"), false, sci).equals("1e−20"),
                "tiny rational remains visible");

        check(
                ValueFormatter.format(third, true, sci).equals("≈ 0.333333333333"),
                "recurring fraction limited precision");
        keys(c, "CLEAR", "1", "eˣ");
        check(top(c).definitelyEquals(UnifiedReal.E), "e to x key");
        check(
                third.multiply(new UnifiedReal(3)).definitelyOne(),
                "display retains exact stack arithmetic");
    }

    @Test
    void fractionFormattingAndCopy() throws Exception {
        check(
                ValueFormatter.format(RpnCalculator.parse("0.3"), true, sci, FractionMode.IMPROPER)
                        .equals("3/10"),
                "fraction mode displays exact decimal rational");
        check(
                ValueFormatter.format(third, true, sci, FractionMode.IMPROPER).equals("1/3"),
                "fraction mode preserves recurring rational");
        check(
                ValueFormatter.format(
                                new UnifiedReal(new com.android.calculator2.BoundedRational(6, -8)),
                                true,
                                sci,
                                FractionMode.IMPROPER)
                        .equals("-3/4"),
                "fraction sign and reduction");
        check(
                ValueFormatter.format(UnifiedReal.TWO, true, sci, FractionMode.IMPROPER)
                        .equals("2"),
                "fraction mode leaves integers natural");
        check(
                ValueFormatter.format(UnifiedReal.ZERO, true, sci, FractionMode.IMPROPER)
                        .equals("0"),
                "fraction mode zero");
        check(
                ValueFormatter.format(huge, true, sci, FractionMode.IMPROPER)
                        .equals(huge.bigIntegerValue().toString()),
                "fraction mode retains full newest integer");
        check(
                ValueFormatter.format(huge, false, sci, FractionMode.IMPROPER).contains("e+29"),
                "older integer remains compact in fraction mode");

        check(
                ValueFormatter.format(rootTwo, true, sci, FractionMode.IMPROPER)
                        .equals(ValueFormatter.format(rootTwo, true, sci)),
                "irrational fraction fallback");
        check(
                ValueFormatter.format(UnifiedReal.PI, true, sci, FractionMode.IMPROPER)
                        .equals(ValueFormatter.format(UnifiedReal.PI, true, sci)),
                "pi fraction fallback");
        check(
                ValueFormatter.format(
                                RpnCalculator.parse("1e-20"), true, eng, FractionMode.IMPROPER)
                        .equals("1/100000000000000000000"),
                "tiny rational remains exact fraction");
        RpnCalculator fractions = new RpnCalculator();
        keys(fractions, "1", "ENTER", "3", "÷");
        check(
                fractions.formattedValues(sci, FractionMode.IMPROPER).get(0).equals("1/3"),
                "stack fraction display");
        check(
                fractions
                        .formattedValues(sci, FractionMode.DECIMAL)
                        .get(0)
                        .equals("≈ 0.333333333333"),
                "stack decimal display");
        check(
                top(fractions).multiply(new UnifiedReal(3)).definitelyOne(),
                "display mode leaves exact arithmetic unchanged");
        check(
                ValueFormatter.clipboard(top(fractions), sci, FractionMode.IMPROPER).equals("1/3"),
                "fraction display copies exact fraction");
        check(
                ValueFormatter.clipboard(RpnCalculator.parse("0.125"), sci, FractionMode.IMPROPER)
                        .equals("1/8"),
                "terminating decimal copies as fraction");
        check(
                ValueFormatter.clipboard(
                                new UnifiedReal(new com.android.calculator2.BoundedRational(6, -8)),
                                sci,
                                FractionMode.IMPROPER)
                        .equals("-3/4"),
                "copied fraction is reduced with positive denominator");
        check(
                ValueFormatter.clipboard(UnifiedReal.TWO, sci, FractionMode.IMPROPER).equals("2"),
                "fraction mode copies integer without denominator");
        check(
                ValueFormatter.clipboard(UnifiedReal.PI, sci, FractionMode.IMPROPER)
                        .equals(
                                ValueFormatter.clipboard(
                                        UnifiedReal.PI, sci, FractionMode.DECIMAL)),
                "fraction mode copies irrational decimal fallback");
        check(
                ValueFormatter.clipboard(top(fractions), sci, FractionMode.DECIMAL)
                        .equals("0.3333333333333333333333333333333333333333"),
                "decimal mode retains decimal copy");
    }

    @Test
    void mixedFractionsAndRemainder() throws Exception {
        UnifiedReal sevenThirds = RpnCalculator.parse("7").divide(RpnCalculator.parse("3"));
        check(
                ValueFormatter.format(sevenThirds, true, sci, FractionMode.IMPROPER).equals("7/3"),
                "improper fraction display");
        check(
                ValueFormatter.format(sevenThirds, true, sci, FractionMode.MIXED).equals("2 1/3"),
                "mixed fraction display");
        check(
                ValueFormatter.clipboard(sevenThirds.negate(), sci, FractionMode.MIXED)
                        .equals("-2 1/3"),
                "mixed negative sign applies to whole value");
        check(
                ValueFormatter.format(third.negate(), true, sci, FractionMode.MIXED).equals("-1/3"),
                "proper fraction remains proper in mixed mode");
        check(
                ValueFormatter.format(UnifiedReal.TWO, true, sci, FractionMode.MIXED).equals("2"),
                "mixed mode leaves integer natural");
        check(
                ValueFormatter.format(UnifiedReal.PI, true, sci, FractionMode.MIXED)
                        .equals(ValueFormatter.format(UnifiedReal.PI, true, sci)),
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
            RpnCalculator remainder = new RpnCalculator();
            remainder.paste(example[0]);
            remainder.key("ENTER");
            remainder.paste(example[1]);
            remainder.key("rem");
            check(
                    top(remainder).definitelyEquals(RpnCalculator.parse(example[2])),
                    "exact remainder " + example[0] + " rem " + example[1]);
            RpnCalculator remainderSaved = RpnCalculator.load(remainder.save());
            check(
                    top(remainderSaved).definitelyEquals(top(remainder)),
                    "remainder survives restore");
        }
        RpnCalculator modular = new RpnCalculator();
        modular.paste("-7");
        modular.key("ENTER");
        modular.paste("3");
        modular.key("mod");
        check(top(modular).definitelyEquals(UnifiedReal.TWO), "modulo has divisor sign");
        modular.key("UNDO");
        check(
                modular.entry().equals("3") && top(modular).definitelyEquals(new UnifiedReal(-7)),
                "remainder undo restores pending divisor");
        modular.key("REDO");
        check(top(modular).definitelyEquals(UnifiedReal.TWO), "remainder redo");
        RpnCalculator negativeMod = new RpnCalculator();
        negativeMod.paste("7");
        negativeMod.key("ENTER");
        negativeMod.paste("-3");
        negativeMod.key("mod");
        check(top(negativeMod).definitelyEquals(new UnifiedReal(-2)), "negative-divisor modulo");
        RpnCalculator zeroDivisor = new RpnCalculator();
        keys(zeroDivisor, "7", "ENTER", "0");
        try {
            zeroDivisor.key("rem");
            throw new AssertionError("zero remainder divisor accepted");
        } catch (ArithmeticException expected) {
        }
        check(
                top(zeroDivisor).definitelyZero()
                        && zeroDivisor.values().size() == 2
                        && !zeroDivisor.isEditing(),
                "zero-divisor remainder leaves state intact");
        RpnCalculator irrationalRem = new RpnCalculator();
        keys(irrationalRem, "π", "2");
        try {
            irrationalRem.key("rem");
            throw new AssertionError("irrational remainder accepted");
        } catch (IllegalArgumentException expected) {
        }
        check(
                top(irrationalRem).definitelyEquals(UnifiedReal.TWO)
                        && irrationalRem.values().size() == 2
                        && !irrationalRem.isEditing(),
                "irrational remainder is rejected without changing state");
    }
}
