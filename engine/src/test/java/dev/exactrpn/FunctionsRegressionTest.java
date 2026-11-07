// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

final class FunctionsRegressionTest extends RegressionSupport {
    @Test
    void factorialAndPrimeFactorisation() throws Exception {
        RpnCalculator factorial = new RpnCalculator();
        keys(factorial, "0", "!");
        check(top(factorial).definitelyOne(), "zero factorial");
        keys(factorial, "CLEAR", "2", "0", "!");
        check(
                top(factorial).bigIntegerValue().toString().equals("2432902008176640000"),
                "factorial is exact beyond double precision");
        factorial.key("UNDO");
        assertEquals("20", factorial.entry(), "factorial undo restores full entry");
        factorial.key("REDO");
        check(
                top(factorial).bigIntegerValue().toString().equals("2432902008176640000"),
                "factorial redo");
        for (String invalid : new String[] {"-1", "1.5"}) {
            RpnCalculator rejected = new RpnCalculator();
            rejected.paste(invalid);
            try {
                rejected.key("!");
                throw new AssertionError("invalid factorial accepted");
            } catch (IllegalArgumentException expected) {
            }
            check(
                    !rejected.isEditing()
                            && rejected.values().size() == 1
                            && top(rejected).definitelyEquals(RpnCalculator.parse(invalid)),
                    "invalid factorial leaves entry intact");
        }
        RpnCalculator largeFactorial = new RpnCalculator();
        largeFactorial.paste("20000");
        largeFactorial.key("!");
        check(
                top(largeFactorial).bigIntegerValue().toString().length() > 65535,
                "large factorial exceeds legacy UTF literal limit");
        check(
                top(RpnCalculator.load(largeFactorial.save()))
                        .bigIntegerValue()
                        .equals(top(largeFactorial).bigIntegerValue()),
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
            RpnCalculator factorised = new RpnCalculator();
            factorised.paste(example[0]);
            factorised.key("Factor");
            java.math.BigInteger product = java.math.BigInteger.ONE,
                    previous = java.math.BigInteger.ZERO;
            java.util.List<String> actual = new java.util.ArrayList<>();
            for (UnifiedReal factor : factorised.values()) {
                java.math.BigInteger n = factor.bigIntegerValue();
                check(n.compareTo(previous) >= 0, "factors pushed smallest first");
                product = product.multiply(n);
                previous = n;
                actual.add(n.toString());
            }
            check(
                    product.equals(new java.math.BigInteger(example[0])),
                    "factor product is original integer");
            if (!example[0].equals("1208925819614629174706176"))
                check(
                        String.join(",", actual).equals(example[1]),
                        "prime factor multiplicities " + example[0]);
            else check(actual.size() == 80, "large integer factorisation preserves multiplicity");
            RpnCalculator factorRestored = RpnCalculator.load(factorised.save());
            check(
                    factorRestored.values().size() == factorised.values().size(),
                    "factor stack survives restore");
            factorRestored.key("UNDO");
            check(
                    factorRestored.entry().equals(example[0]) && factorRestored.values().isEmpty(),
                    "factor undo restores original input");
            factorRestored.key("REDO");
            check(
                    factorRestored.values().size() == factorised.values().size(),
                    "factor redo restores all factors");
        }
        for (String invalid : new String[] {"0", "1", "-12", "1.5"}) {
            RpnCalculator rejected = new RpnCalculator();
            rejected.paste(invalid);
            try {
                rejected.key("Factor");
                throw new AssertionError("invalid factorisation accepted");
            } catch (IllegalArgumentException expected) {
            }
            check(
                    !rejected.isEditing()
                            && rejected.values().size() == 1
                            && top(rejected).definitelyEquals(RpnCalculator.parse(invalid)),
                    "invalid factorisation is transactional");
        }
        RpnCalculator interruptedFactors = new RpnCalculator();
        interruptedFactors.paste("170141183460469231731687303715884105727");
        int factorUndo = interruptedFactors.undoCount();
        Thread.currentThread().interrupt();
        try {
            interruptedFactors.key("Factor");
            throw new AssertionError("factorisation ignored cancellation");
        } catch (com.hp.creals.CR.AbortedException expected) {
        } finally {
            Thread.interrupted();
        }
        check(
                interruptedFactors.entry().equals("170141183460469231731687303715884105727")
                        && interruptedFactors.values().isEmpty()
                        && interruptedFactors.undoCount() == factorUndo,
                "factorisation cancellation is transactional");
    }

    @Test
    void inverseFunctionsAndAnglePersistence() throws Exception {
        RpnCalculator inverseTrig = new RpnCalculator();
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.DEG);
        inverseTrig.paste("0.5");
        inverseTrig.key("asin");
        check(top(inverseTrig).definitelyEquals(new UnifiedReal(30)), "asin degrees exact");
        keys(inverseTrig, "CLEAR", "0", "acos");
        check(top(inverseTrig).definitelyEquals(new UnifiedReal(90)), "acos degrees exact");
        keys(inverseTrig, "CLEAR", "1", "atan");
        check(top(inverseTrig).definitelyEquals(new UnifiedReal(45)), "atan degrees exact");
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        keys(inverseTrig, "CLEAR", "1", "atan");
        check(top(inverseTrig).definitelyEquals(new UnifiedReal(50)), "atan gradians exact");
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        keys(inverseTrig, "CLEAR", "0", "asin");
        check(top(inverseTrig).definitelyZero(), "asin radians");
        inverseTrig.paste("0.25");
        inverseTrig.key("asin");
        RpnCalculator inverseSaved = RpnCalculator.load(inverseTrig.save());
        check(
                top(inverseSaved)
                        .toStringTruncated(60)
                        .equals(top(inverseTrig).toStringTruncated(60)),
                "inverse trig expression survives restore");
        RpnCalculator square = new RpnCalculator();
        keys(square, "π", "x²");
        check(
                top(RpnCalculator.load(square.save()))
                        .toStringTruncated(60)
                        .equals(top(square).toStringTruncated(60)),
                "square expression survives restore");
        RpnCalculator root = new RpnCalculator();
        keys(root, "9", "ENTER", "2", "root");
        check(top(root).definitelyEquals(new UnifiedReal(3)), "Xth root of Y operand order");
        check(
                "rem".equals(KeyboardInput.command(0, '%', false, false, false)),
                "remainder keyboard shortcut");
        check(
                "!".equals(KeyboardInput.command(0, '!', false, false, false)),
                "factorial keyboard shortcut");
        check(
                "Factor".equals(KeyboardInput.command(0, 'f', false, false, false)),
                "factorise keyboard shortcut");
    }

    @Test
    void exactRootsAndLargePrimeProofs() throws Exception {
        RpnCalculator rationalRoot = new RpnCalculator();
        keys(rationalRoot, "8", "ENTER", "2", "7", "÷", "3", "root");
        check(
                top(rationalRoot).multiply(new UnifiedReal(3)).definitelyEquals(UnifiedReal.TWO),
                "perfect rational cube root stays exact");
        RpnCalculator cubeZero = new RpnCalculator();
        keys(cubeZero, "2", "7", "ENTER", "3", "root", "3", "−");
        check(top(cubeZero).definitelyZero(), "cube root subtraction gives exact zero");
        for (String[] invalid : new String[][] {{"-8", "2"}, {"9", "0"}, {"0", "-3"}}) {
            RpnCalculator rejected = new RpnCalculator();
            rejected.paste(invalid[0]);
            rejected.key("ENTER");
            rejected.paste(invalid[1]);
            try {
                rejected.key("root");
                throw new AssertionError("invalid root accepted");
            } catch (ArithmeticException expected) {
            }
            check(
                    rejected.values().get(0).definitelyEquals(RpnCalculator.parse(invalid[0]))
                            && top(rejected).definitelyEquals(RpnCalculator.parse(invalid[1]))
                            && !rejected.isEditing(),
                    "invalid root preserves state");
        }
        check(
                ValueFormatter.clipboard(UnifiedReal.PI, sci)
                        .equals("3.141592653589793238462643383279502884197"),
                "copy pi to forty significant digits");
        check(
                ValueFormatter.clipboard(UnifiedReal.TWO.sqrt(), sci)
                        .equals("1.41421356237309504880168872420969807857"),
                "copy sqrt2 to forty significant digits");
        BigInteger largeSemi = new BigInteger("1000000007").multiply(new BigInteger("34359738337"));
        check(
                ExactFunctions.factors(largeSemi)
                        .equals(
                                java.util.Arrays.asList(
                                        new BigInteger("1000000007"),
                                        new BigInteger("34359738337"))),
                "rho splits semiprime above 64 bits");
        BigInteger mersenne89 = BigInteger.ONE.shiftLeft(89).subtract(BigInteger.ONE);
        check(
                ExactFunctions.factors(mersenne89).equals(java.util.Arrays.asList(mersenne89)),
                "large prime has a proof, not only probable-prime acceptance");
        check(
                "SMART_CLEAR".equals(KeyboardInput.command(40, 0, true, false, false)),
                "Ctrl L uses safe contextual clear");
    }
}
