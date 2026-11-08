// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class EntryRegressionTest extends RegressionSupport {
    @Test
    void arithmeticAndEntryTransactions() throws Exception {
        assertTrue((FractionMode.DECIMAL).next() == FractionMode.MIXED, "decimal cycles to mixed");
        assertTrue(
                (FractionMode.MIXED).next() == FractionMode.IMPROPER, "mixed cycles to improper");
        assertTrue(
                (FractionMode.IMPROPER).next() == FractionMode.DECIMAL,
                "improper cycles to decimal");
        RpnCalculator c = new RpnCalculator();
        number(c, "0.1");
        c.key("ENTER");
        number(c, "0.2");
        c.key("+");
        assertTrue(top(c).definitelyEquals(RpnCalculator.parse("0.3")), "exact decimal addition");
        keys(c, "CLEAR", "2", "ENTER", "3", "−");
        assertTrue(top(c).definitelyEquals(UnifiedReal.MINUS_ONE), "subtraction order");
        keys(c, "CLEAR", "2", "ENTER", "3", "÷");
        assertTrue(
                top(c).multiply(new UnifiedReal(3)).definitelyEquals(UnifiedReal.TWO),
                "division order and exact rational");
        keys(c, "CLEAR", "2", "√", "ENTER", "×");
        assertTrue(top(c).definitelyEquals(UnifiedReal.TWO), "sqrt simplification");
        keys(c, "CLEAR", "π", "sin");
        assertTrue(top(c).definitelyZero(), "symbolic sine pi");
        keys(c, "CLEAR", "2", "ENTER", "3", "xʸ");
        assertTrue(top(c).definitelyEquals(new UnifiedReal(8)), "power order");
        keys(c, "UNDO");
        assertEquals(1, c.values().size(), "undo restores stack");
        assertEquals("3", c.entry(), "undo restores pending entry");
        keys(c, "CLEAR", "5", "ENTER", "0");
        try {
            c.key("÷");
            throw new AssertionError("zero division accepted");
        } catch (ArithmeticException expected) {
        }
        assertTrue(
                c.values().size() == 2 && !c.isEditing(),
                "zero division preserves committed entry");
        keys(c, "CLEAR", "1", "±", "ENTER");
        try {
            c.key("√");
            throw new AssertionError("negative root accepted");
        } catch (ArithmeticException expected) {
        }
        assertTrue(top(c).definitelyEquals(UnifiedReal.MINUS_ONE), "domain error preserves stack");
        keys(c, "CLEAR", "1", "ENTER", "2", "ENTER", "SWAP");
        assertTrue(top(c).definitelyOne(), "swap");
        keys(c, "DROP");
        assertTrue(top(c).definitelyEquals(UnifiedReal.TWO), "drop");
        keys(c, "DUP");
        assertTrue(c.values().size() == 2, "duplicate");
        keys(c, "CLEAR");
        try {
            c.key("+");
            throw new AssertionError("underflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(c.values().isEmpty(), "underflow preserves stack");
        assertTrue(
                RpnCalculator.parse("1e-20").multiply(RpnCalculator.parse("1e20")).definitelyOne(),
                "exact decimal parser");
        // Cancellation must interrupt undecidable real comparisons and roll back pending input.
        keys(c, "1", "ENTER", "0");
        Thread.currentThread().interrupt();
        try {
            c.key("÷");
        } catch (RuntimeException expected) {
        } finally {
            Thread.interrupted();
        }
        assertTrue(
                c.values().size() == 2 && !c.isEditing(),
                "operator error preserves committed entry");
    }

    @Test
    void copyAndCancellation() throws Exception {
        RpnCalculator original = new RpnCalculator();
        keys(original, "1", "ENTER", "2");
        RpnCalculator candidate = CalculatorTestSupport.copy(original);
        candidate.key("+");
        assertTrue(
                original.entry().equals("2") && original.values().size() == 1,
                "candidate does not mutate live state");
        candidate.key("UNDO");
        assertTrue(
                candidate.entry().equals("2") && candidate.values().size() == 1,
                "candidate preserves undo");
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean aborted =
                new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread difficult =
                new Thread(
                        () -> {
                            started.countDown();
                            try {
                                com.hp.creals.CR.PI.subtract(com.hp.creals.CR.PI).signum();
                            } catch (com.hp.creals.CR.AbortedException expected) {
                                aborted.set(true);
                            } catch (Throwable unexpected) {
                                failure.set(unexpected);
                            }
                        });
        difficult.setDaemon(true);
        difficult.start();
        started.await();
        Thread.sleep(25);
        difficult.interrupt();
        difficult.join(2000);
        assertTrue(
                !difficult.isAlive() && aborted.get() && failure.get() == null,
                "interrupt stops difficult real comparison");
        original.key("+");
        assertTrue(
                top(original).definitelyEquals(new UnifiedReal(3)),
                "calculation works after cancelled candidate");
        assertEquals("7", KeyboardInput.command(14, '7', false, false, false), "digit key mapping");
        assertEquals(
                "7", KeyboardInput.command(151, 0, false, false, false), "numpad digit mapping");
        assertEquals("+", KeyboardInput.command(157, 0, false, false, false), "numpad arithmetic");
        assertEquals(
                "+", KeyboardInput.command(81, '+', false, false, false), "shifted plus mapping");
        assertEquals(
                "ENTER", KeyboardInput.command(66, 0, false, false, false), "enter key mapping");
        assertEquals(
                "ENTER",
                KeyboardInput.command(160, 0, false, false, false),
                "numpad enter mapping");
        assertEquals("ENTER", KeyboardInput.command(62, ' ', false, false, false), "space mapping");
        assertEquals(
                "BACK", KeyboardInput.command(67, 0, false, false, false), "backspace mapping");
        assertEquals(
                "DELETE", KeyboardInput.command(112, 0, false, false, false), "delete mapping");
        assertEquals(
                "ESCAPE", KeyboardInput.command(111, 0, false, false, false), "escape mapping");
        assertEquals("UNDO", KeyboardInput.command(54, 0, true, false, false), "undo shortcut");
        assertEquals("DUP", KeyboardInput.command(32, 0, true, false, false), "duplicate shortcut");
        assertEquals("SWAP", KeyboardInput.command(47, 0, true, false, false), "swap shortcut");
        assertTrue(
                KeyboardInput.command(54, 'z', false, true, false) == null,
                "leave alt shortcuts to system");
        assertEquals(
                "eˣ",
                KeyboardInput.command(33, 'E', false, false, false),
                "uppercase exponential shortcut");
        keys(original, "4", "5", "CE");
        assertTrue(
                original.entry().isEmpty() && top(original).definitelyEquals(new UnifiedReal(3)),
                "clear entry preserves stack");
        RpnCalculator branch = CalculatorTestSupport.copy(original);
        branch.key("UNDO");
        branch.key("ENTER");
        original.key("UNDO");
        assertTrue(
                original.entry().equals("45") && original.values().size() == 1,
                "undo history independent across candidates");
    }

    @Test
    void stackAndEditModes() throws Exception {
        RpnCalculator hp = new RpnCalculator();
        assertTrue(!hp.isEditing() && hp.entry().isEmpty(), "initial stack mode");
        hp.key("BACK");
        assertTrue(hp.values().isEmpty() && !hp.isEditing(), "drop on empty stack");
        number(hp, "123456789012345678901234567890");
        hp.key("ENTER");
        assertTrue(
                !hp.isEditing()
                        && CalculatorTestSupport.formattedValues(hp, sci, FractionMode.DECIMAL)
                                .get(0)
                                .equals("123456789012345678901234567890"),
                "bottom integer expands in stack mode");
        hp.key("4");
        assertTrue(
                hp.isEditing()
                        && CalculatorTestSupport.formattedValues(hp, sci, FractionMode.DECIMAL)
                                .get(0)
                                .contains("e+29"),
                "typing moves prior integer to compact stack");
        hp.key("BACK");
        assertTrue(
                hp.isEditing() && hp.entry().isEmpty() && hp.values().size() == 1,
                "backspace to empty stays editing");
        hp.key("BACK");
        assertTrue(hp.isEditing() && hp.values().size() == 1, "empty backspace never drops stack");
        hp.key("DROP");
        assertTrue(
                hp.isEditing() && hp.values().size() == 1,
                "delete key also respects empty entry mode");
        hp.key("ENTER");
        assertTrue(
                !hp.isEditing() && hp.values().size() == 1,
                "enter on blank entry returns to stack without duplication");
        hp.key("ENTER");
        assertTrue(hp.values().size() == 2 && !hp.isEditing(), "enter duplicates in stack mode");
        hp.key("BACK");
        assertTrue(hp.values().size() == 1 && !hp.isEditing(), "context key drops result");
        hp.key("UNDO");
        assertTrue(hp.values().size() == 2 && !hp.isEditing(), "undo drop restores stack mode");
        keys(hp, "5", "BACK", "CANCEL_ENTRY", "UNDO");
        assertTrue(
                hp.isEditing() && hp.entry().isEmpty(),
                "undo restores explicitly empty editing mode");
        RpnCalculator editCopy = CalculatorTestSupport.copy(hp);
        editCopy.key("6");
        assertTrue(
                hp.isEditing() && hp.entry().isEmpty() && editCopy.entry().equals("6"),
                "copy preserves independent empty edit");
        keys(hp, "CE", "CLEAR", "9", "ENTER", "0");
        try {
            hp.key("÷");
            throw new AssertionError("division accepted");
        } catch (ArithmeticException expected) {
        }
        assertTrue(
                !hp.isEditing() && hp.values().size() == 2 && top(hp).definitelyZero(),
                "error keeps committed zero on stack");
        hp.key("BACK");
        try {
            hp.key("+");
            throw new AssertionError("underflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(!hp.isEditing() && hp.values().size() == 1, "failed operation preserves stack");
        keys(hp, "CLEAR", "2", "ENTER", "3", "+");
        assertTrue(
                !hp.isEditing() && top(hp).definitelyEquals(new UnifiedReal(5)),
                "operation ends entry mode");
        hp.setAngleUnit(RpnCalculator.AngleUnit.DEG);
        keys(hp, "CLEAR", "9", "0", "sin");
        assertTrue(top(hp).definitelyOne(), "sin 90 degrees exactly one");
        keys(hp, "CLEAR", "4", "5", "tan");
        assertTrue(top(hp).definitelyOne(), "tan 45 degrees exactly one");
        hp.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        keys(hp, "CLEAR", "1", "0", "0", "sin");
        assertTrue(top(hp).definitelyOne(), "sin 100 gradians exactly one");
        keys(hp, "CLEAR", "2", "0", "0", "cos");
        assertTrue(
                top(hp).definitelyEquals(UnifiedReal.MINUS_ONE),
                "cos 200 gradians exactly minus one");
        hp.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        keys(hp, "CLEAR", "π", "2", "÷", "sin");
        assertTrue(top(hp).definitelyOne(), "radians unchanged");
        hp.key("7");
        hp.key("ANGLE");
        assertTrue(
                hp.angleUnit() == RpnCalculator.AngleUnit.DEG
                        && hp.isEditing()
                        && hp.entry().equals("7"),
                "angle toggle preserves entry and stack");
        hp.key("ANGLE");
        assertTrue(hp.angleUnit() == RpnCalculator.AngleUnit.GRAD, "cycle to gradians");
        hp.key("ANGLE");
        assertTrue(hp.angleUnit() == RpnCalculator.AngleUnit.RAD, "cycle to radians");
        assertEquals("1234.56", normalize("1234.56", ',', '.', 3, 3), "C decimal in comma locale");
        assertTrue(
                NumberText.analyze("1.234", new NumberText.Profile(',', '.', 3, 3)).ambiguous(),
                "dot-only ambiguity is never guessed");
        assertEquals("1234.56", normalize("1.234,56", ',', '.', 3, 3), "German grouped decimal");
        assertEquals("-0.1250", normalize("-0,1250", ',', '.', 3, 3), "negative local subunit");
        assertEquals("0.5", normalize(",5", ',', '.', 3, 3), "local leading decimal");
        assertEquals("1234.56", normalize("1,234.56", '.', ',', 3, 3), "US grouped decimal");
        assertTrue(
                NumberText.analyze("1,234", new NumberText.Profile('.', ',', 3, 3)).ambiguous(),
                "US grouped integer can be a foreign decimal");
        assertTrue(
                NumberText.analyze("1,234", new NumberText.Profile(',', '.', 3, 3)).ambiguous(),
                "local decimal can be foreign grouping");
        assertEquals("1234567.89", normalize("12,34,567.89", '.', ',', 3, 2), "Indian grouping");
        assertEquals("1234.5", normalize("1 234,5", ',', '\u202f', 3, 3), "French space grouping");
        assertEquals(
                "1234.5",
                normalize("1\u00a0234,5", ',', '\u202f', 3, 3),
                "French nonbreaking space grouping");
        assertEquals("1234.5", normalize("1’234.5", '.', '’', 3, 3), "Swiss grouping");
        assertEquals("1234.5", normalize("1'234.5", '.', '’', 3, 3), "Swiss ASCII apostrophe");
        assertEquals(
                "-1.25e-3",
                normalize("−1,25e−3", ',', '.', 3, 3),
                "local scientific notation and minus");
        assertEquals(
                "-1234.5",
                normalize("\u061c-١٬٢٣٤٫٥", '٫', '٬', 3, 3),
                "Arabic digits and bidi mark");
        assertEquals(
                "+12.50E+2",
                normalize("  +12.50E+2  ", ',', '.', 3, 3),
                "C scientific paste preserves exact text");
        assertEquals(
                "1234.5",
                normalize("1\u2009234.5", '.', ',', 3, 3),
                "thin space computer grouping");
        assertEquals("1234.5", normalize("1 234.5", '.', ',', 3, 3), "space computer grouping");
        for (String invalid :
                new String[] {
                    "1,23.4",
                    "1,,234",
                    "1,234,",
                    "1\n2",
                    "$12",
                    "1/2",
                    "NaN",
                    "Infinity",
                    "",
                    "1e10000"
                }) {
            try {
                normalize(invalid, '.', ',', 3, 3);
                throw new AssertionError("accepted invalid paste: " + invalid);
            } catch (IllegalArgumentException expected) {
                assertTrue(true, "reject invalid clipboard number");
            }
        }
        try {
            NumberText.selected("123,456", new NumberText.Profile('.', ',', 3, 2));
            throw new AssertionError("bad Indian grouping accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(true, "Indian secondary width enforced");
        }
    }

    @Test
    void canonicalPaste() throws Exception {
        RpnCalculator pasted = new RpnCalculator();
        keys(pasted, "7", "ENTER", "8");
        CalculatorTestSupport.paste(pasted, normalize("1.234,56", ',', '.', 3, 3));
        assertTrue(
                pasted.isEditing()
                        && pasted.entry().equals("1234.56")
                        && top(pasted).definitelyEquals(new UnifiedReal(7)),
                "paste replaces entry without changing stack");
        pasted.key("UNDO");
        assertTrue(
                pasted.entry().equals("8") && pasted.isEditing(), "paste is one undo transaction");
        try {
            CalculatorTestSupport.paste(pasted, "oops");
            throw new AssertionError("invalid direct paste accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("8", pasted.entry(), "invalid paste preserves state");
        }
        keys(pasted, "PASTE_RAW:0.1", "ENTER", "PASTE_RAW:0.2", "+");
        assertTrue(
                top(pasted).definitelyEquals(RpnCalculator.parse("0.3")),
                "pasted arithmetic remains exact");
        assertEquals(
                "123456789012345678901234567890",
                ValueFormatter.clipboard(huge, sci),
                "clipboard retains full exact integer");
        assertEquals(
                "-0.00000000000000000001",
                ValueFormatter.clipboard(RpnCalculator.parse("-1e-20"), sci),
                "clipboard preserves tiny exact value with ASCII minus");
        assertEquals(
                "0.3333333333333333333333333333333333333333",
                ValueFormatter.clipboard(third, sci),
                "clipboard omits approximation annotation");
        assertEquals(
                "PASTE", KeyboardInput.command(50, 0, true, false, false), "Ctrl V paste shortcut");
        assertEquals(
                "5",
                KeyboardInput.command(0, '٥', false, false, false),
                "localized hardware digits");
        CalculatorTestSupport.paste(pasted, "1e-3");
        pasted.key(".");
        assertEquals("1e-3", pasted.entry(), "decimal key does not corrupt pasted exponent");
    }
}
