// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

final class FunctionsRegressionTest extends RegressionSupport {
    @Test
    void factorialAndPrimeFactorisation() throws Exception {
        TestCalculator factorial = new TestCalculator();
        keys(factorial, "0", "!");
        assertTrue(top(factorial).definitelyOne(), "zero factorial");
        keys(factorial, "CLEAR", "2", "0", "!");
        assertEquals(
                "2432902008176640000",
                top(factorial).bigIntegerValue().toString(),
                "factorial is exact beyond double precision");
        factorial.key("UNDO");
        assertEquals("20", factorial.entry(), "factorial undo restores full entry");
        factorial.key("REDO");
        assertEquals(
                "2432902008176640000",
                top(factorial).bigIntegerValue().toString(),
                "factorial redo");
        for (String invalid : new String[] {"-1", "1.5"}) {
            TestCalculator rejected = new TestCalculator();
            CalculatorTestSupport.paste(rejected, invalid);
            try {
                rejected.key("!");
                throw new AssertionError("invalid factorial accepted");
            } catch (IllegalArgumentException expected) {
            }
            assertTrue(
                    !rejected.isEditing()
                            && rejected.values().size() == 1
                            && top(rejected).definitelyEquals(RpnCalculator.parse(invalid)),
                    "invalid factorial leaves entry intact");
        }
        TestCalculator largeFactorial = new TestCalculator();
        CalculatorTestSupport.paste(largeFactorial, "20000");
        largeFactorial.key("!");
        assertTrue(
                top(largeFactorial).bigIntegerValue().toString().length() > 65535,
                "large factorial exceeds legacy UTF literal limit");
        assertEquals(
                top(largeFactorial).bigIntegerValue(),
                top(TestCalculator.load(largeFactorial.save())).bigIntegerValue(),
                "large factorial saves as exact expression");
        for (String[] example :
                new String[][] {
                    {"84", "2,2,3,7"},
                    {"2", "2"},
                    {"81", "3,3,3,3"},
                    {"1000000016000000063", "1000000007,1000000009"},
                    {"2305843009213693951", "2305843009213693951"},
                    {"18446744073709551615", "3,5,17,257,641,65537,6700417"},
                    {"1208925819614629174706176", "2"}
                }) {
            TestCalculator factorised = new TestCalculator();
            CalculatorTestSupport.paste(factorised, example[0]);
            factorised.key("Factor");
            java.math.BigInteger product = java.math.BigInteger.ONE,
                    previous = java.math.BigInteger.ZERO;
            java.util.List<String> actual = new java.util.ArrayList<>();
            for (UnifiedReal factor : factorised.values()) {
                java.math.BigInteger n = factor.bigIntegerValue();
                assertTrue(n.compareTo(previous) >= 0, "factors pushed smallest first");
                product = product.multiply(n);
                previous = n;
                actual.add(n.toString());
            }
            assertEquals(
                    new java.math.BigInteger(example[0]),
                    product,
                    "factor product is original integer");
            if (!example[0].equals("1208925819614629174706176"))
                assertEquals(
                        example[1],
                        String.join(",", actual),
                        "prime factor multiplicities " + example[0]);
            else
                assertTrue(
                        actual.size() == 80, "large integer factorisation preserves multiplicity");
            TestCalculator factorRestored = TestCalculator.load(factorised.save());
            assertTrue(
                    factorRestored.values().size() == factorised.values().size(),
                    "factor stack survives restore");
            factorRestored.key("UNDO");
            assertTrue(
                    factorRestored.entry().equals(example[0]) && factorRestored.values().isEmpty(),
                    "factor undo restores original input");
            factorRestored.key("REDO");
            assertTrue(
                    factorRestored.values().size() == factorised.values().size(),
                    "factor redo restores all factors");
        }
        for (String invalid : new String[] {"0", "1", "-12", "1.5"}) {
            TestCalculator rejected = new TestCalculator();
            CalculatorTestSupport.paste(rejected, invalid);
            try {
                rejected.key("Factor");
                throw new AssertionError("invalid factorisation accepted");
            } catch (IllegalArgumentException expected) {
            }
            assertTrue(
                    !rejected.isEditing()
                            && rejected.values().size() == 1
                            && top(rejected).definitelyEquals(RpnCalculator.parse(invalid)),
                    "invalid factorisation is transactional");
        }
        TestCalculator interruptedFactors = new TestCalculator();
        CalculatorTestSupport.paste(interruptedFactors, "170141183460469231731687303715884105727");
        int factorUndo = interruptedFactors.undoCount();
        Thread.currentThread().interrupt();
        try {
            interruptedFactors.key("Factor");
            throw new AssertionError("factorisation ignored cancellation");
        } catch (com.hp.creals.CR.AbortedException expected) {
        } finally {
            Thread.interrupted();
        }
        assertTrue(
                interruptedFactors.entry().equals("170141183460469231731687303715884105727")
                        && interruptedFactors.values().isEmpty()
                        && interruptedFactors.undoCount() == factorUndo,
                "factorisation cancellation is transactional");
    }

    @Test
    void inverseFunctionsAndAnglePersistence() throws Exception {
        TestCalculator inverseTrig = new TestCalculator();
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.DEG);
        CalculatorTestSupport.paste(inverseTrig, "0.5");
        inverseTrig.key("asin");
        assertTrue(top(inverseTrig).definitelyEquals(new UnifiedReal(30)), "asin degrees exact");
        keys(inverseTrig, "CLEAR", "0", "acos");
        assertTrue(top(inverseTrig).definitelyEquals(new UnifiedReal(90)), "acos degrees exact");
        keys(inverseTrig, "CLEAR", "1", "atan");
        assertTrue(top(inverseTrig).definitelyEquals(new UnifiedReal(45)), "atan degrees exact");
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        keys(inverseTrig, "CLEAR", "1", "atan");
        assertTrue(top(inverseTrig).definitelyEquals(new UnifiedReal(50)), "atan gradians exact");
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        keys(inverseTrig, "CLEAR", "0", "asin");
        assertTrue(top(inverseTrig).definitelyZero(), "asin radians");
        CalculatorTestSupport.paste(inverseTrig, "0.25");
        inverseTrig.key("asin");
        TestCalculator inverseSaved = TestCalculator.load(inverseTrig.save());
        assertEquals(
                top(inverseTrig).toStringTruncated(60),
                top(inverseSaved).toStringTruncated(60),
                "inverse trig expression survives restore");
        TestCalculator square = new TestCalculator();
        keys(square, "π", "x²");
        assertEquals(
                top(square).toStringTruncated(60),
                top(TestCalculator.load(square.save())).toStringTruncated(60),
                "square expression survives restore");
        TestCalculator root = new TestCalculator();
        keys(root, "9", "ENTER", "2", "root");
        assertTrue(top(root).definitelyEquals(new UnifiedReal(3)), "Xth root of Y operand order");
        assertEquals(
                "rem",
                KeyboardInput.command(0, '%', false, false, false),
                "remainder keyboard shortcut");
        assertEquals(
                "!",
                KeyboardInput.command(0, '!', false, false, false),
                "factorial keyboard shortcut");
        assertEquals(
                "Factor",
                KeyboardInput.command(0, 'f', false, false, false),
                "factorise keyboard shortcut");
    }

    @Test
    void exactRootsAndLargePrimeProofs() throws Exception {
        TestCalculator rationalRoot = new TestCalculator();
        keys(rationalRoot, "8", "ENTER", "2", "7", "÷", "3", "root");
        assertTrue(
                top(rationalRoot).multiply(new UnifiedReal(3)).definitelyEquals(UnifiedReal.TWO),
                "perfect rational cube root stays exact");
        TestCalculator cubeZero = new TestCalculator();
        keys(cubeZero, "2", "7", "ENTER", "3", "root", "3", "−");
        assertTrue(top(cubeZero).definitelyZero(), "cube root subtraction gives exact zero");
        for (String[] invalid : new String[][] {{"-8", "2"}, {"9", "0"}, {"0", "-3"}}) {
            TestCalculator rejected = new TestCalculator();
            CalculatorTestSupport.paste(rejected, invalid[0]);
            rejected.key("ENTER");
            CalculatorTestSupport.paste(rejected, invalid[1]);
            try {
                rejected.key("root");
                throw new AssertionError("invalid root accepted");
            } catch (ArithmeticException expected) {
            }
            assertTrue(
                    rejected.values().get(0).definitelyEquals(RpnCalculator.parse(invalid[0]))
                            && top(rejected).definitelyEquals(RpnCalculator.parse(invalid[1]))
                            && !rejected.isEditing(),
                    "invalid root preserves state");
        }
        assertEquals(
                "3.141592653589793238462643383279502884197",
                TestValueFormatter.clipboard(UnifiedReal.PI, sci),
                "copy pi to forty significant digits");
        assertEquals(
                "1.41421356237309504880168872420969807857",
                TestValueFormatter.clipboard(UnifiedReal.TWO.sqrt(), sci),
                "copy sqrt2 to forty significant digits");
        BigInteger largeSemi = new BigInteger("1000000007").multiply(new BigInteger("34359738337"));
        assertEquals(
                java.util.Arrays.asList(
                        new BigInteger("1000000007"), new BigInteger("34359738337")),
                ExactFunctions.factors(largeSemi),
                "rho splits semiprime above 64 bits");
        BigInteger mersenne89 = BigInteger.ONE.shiftLeft(89).subtract(BigInteger.ONE);
        assertEquals(
                java.util.Arrays.asList(mersenne89),
                ExactFunctions.factors(mersenne89),
                "large prime has a proof, not only probable-prime acceptance");
        assertEquals(
                "SMART_CLEAR",
                KeyboardInput.command(40, 0, true, false, false),
                "Ctrl L uses safe contextual clear");
    }
}
