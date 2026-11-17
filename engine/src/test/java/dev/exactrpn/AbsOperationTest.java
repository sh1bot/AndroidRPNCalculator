// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class AbsOperationTest {
    @Test
    void absIsWiredAsAUnaryOperation() {
        assertEquals("|x|", CalculatorKey.ABS.wire);
        assertEquals("|x|", CalculatorKey.ABS.label);
        assertSame(CalculatorKey.ABS, CalculatorKey.find("|x|"));
        assertSame(Operation.ABS, Operation.find("|x|"));
        assertEquals(1, Operation.ABS.arity);
    }

    @Test
    void absOfNegativePositiveAndZero() throws Exception {
        TestCalculator c = new TestCalculator();
        CalculatorTestSupport.paste(c, "-5");
        c.key("ENTER");
        c.key("|x|");
        assertTrue(c.values().get(0).definitelyEquals(RpnCalculator.parse("5")));
        c.key("|x|");
        assertTrue(c.values().get(0).definitelyEquals(RpnCalculator.parse("5")));
        c.key("±");
        assertTrue(c.values().get(0).definitelyEquals(RpnCalculator.parse("-5")));
        c.key("|x|");
        assertTrue(c.values().get(0).definitelyEquals(RpnCalculator.parse("5")));

        TestCalculator zero = new TestCalculator();
        CalculatorTestSupport.paste(zero, "0");
        zero.key("ENTER");
        zero.key("|x|");
        assertTrue(zero.values().get(0).definitelyZero());
    }

    @Test
    void absOfNegativePiIsPiAndSurvivesRestore() throws Exception {
        TestCalculator c = new TestCalculator();
        c.key("π");
        c.key("±");
        c.key("|x|");
        assertTrue(c.values().get(0).definitelyEquals(UnifiedReal.PI));
        TestCalculator restored = TestCalculator.load(c.save());
        assertTrue(restored.values().get(0).definitelyEquals(UnifiedReal.PI));
        restored.key("sin");
        assertTrue(restored.values().get(0).definitelyZero());
    }

    @Test
    void absAppliesToEachSelectedRow() throws Exception {
        TestCalculator c = new TestCalculator();
        for (String text : new String[] {"-4", "9", "-16"}) {
            CalculatorTestSupport.paste(c, text);
            c.key("ENTER");
        }
        c.apply(
                Command.unaryRows(Operation.find("|x|"), 0, 2),
                NumberText.COMPUTER,
                NumberText.COMPUTER);
        assertEquals("4", c.values().get(0).bigIntegerValue().toString());
        assertEquals("9", c.values().get(1).bigIntegerValue().toString());
        assertEquals("16", c.values().get(2).bigIntegerValue().toString());
        c = TestCalculator.load(c.save());
        c.arithmeticKey("UNDO");
        assertEquals("-4", c.values().get(0).bigIntegerValue().toString());
    }

    @Test
    void absInEntryModeRemovesTheEntrySign() throws Exception {
        TestCalculator c = new TestCalculator();
        CalculatorTestSupport.paste(c, "-12");
        assertTrue(c.isEditing());
        c.key("|x|");
        assertTrue(c.isEditing());
        assertEquals("12", c.entry());
        c.key("|x|");
        assertEquals("12", c.entry());
        c.key("ENTER");
        assertTrue(c.values().get(0).definitelyEquals(RpnCalculator.parse("12")));
    }
}
