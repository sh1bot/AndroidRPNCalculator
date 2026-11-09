// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class SelectedProductTest {
    @Test
    void exactProductPreservesEntryAndOriginalsAndSurvivesUndoRedoAndRestore() throws Exception {
        TestCalculator c = new TestCalculator();
        for (String text : new String[] {"0.1", "99", "0.2"}) {
            CalculatorTestSupport.paste(c, text);
            c.key("ENTER");
        }
        CalculatorTestSupport.paste(c, "45");
        c.select(1, 1);
        Command command = Command.rows(Command.Kind.PRODUCT_ROWS, 2, 0, 2);
        assertTrue(command.preservesSelection());
        assertArrayEquals(new int[] {2, 0, 2}, command.rows());
        c.apply(command, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals(4, c.values().size());
        assertTrue(c.values().get(3).definitelyEquals(RpnCalculator.parse("0.02")));
        assertTrue(c.isEditing());
        assertEquals("45", c.entry());
        assertEquals(1, c.selectionStart());
        c = TestCalculator.load(c.save());
        c.arithmeticKey("UNDO");
        assertEquals(3, c.values().size());
        c.arithmeticKey("REDO");
        assertTrue(c.values().get(3).definitelyEquals(RpnCalculator.parse("0.02")));
    }

    @Test
    void irrationalProductRetainsItsExactExpression() throws Exception {
        TestCalculator c = new TestCalculator();
        c.key("π");
        CalculatorTestSupport.paste(c, "2");
        c.key("ENTER");
        c.productRows(0, 1);
        TestCalculator restored = TestCalculator.load(c.save());
        assertTrue(
                restored.values()
                        .get(2)
                        .definitelyEquals(UnifiedReal.PI.multiply(UnifiedReal.TWO)));
        restored.key("sin");
        assertTrue(restored.values().get(2).definitelyZero());
    }

    @Test
    void invalidProductSelectionDoesNotChangeStateAndEmptySelectionDoesNothing() throws Exception {
        TestCalculator c = new TestCalculator();
        c.key("π");
        byte[] before = c.save();
        assertThrows(IllegalArgumentException.class, () -> c.productRows(0, 1));
        assertArrayEquals(before, c.save());
        c.productRows();
        assertArrayEquals(before, c.save());
    }
}
