// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class SelectedMeanTest {
    @Test
    void meanIsExactDeduplicatedAndPreservesOriginalsAndEntry() throws Exception {
        TestCalculator c = new TestCalculator();
        for (String text : new String[] {"0.1", "99", "0.2"}) {
            CalculatorTestSupport.paste(c, text);
            c.key("ENTER");
        }
        CalculatorTestSupport.paste(c, "45");
        c.select(1, 1);
        Command command = Command.rows(Command.Kind.MEAN_ROWS, 2, 0, 2);
        assertTrue(command.preservesSelection());
        assertArrayEquals(new int[] {2, 0, 2}, command.rows());
        int history = c.undoCount();
        c.apply(command, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals(4, c.values().size());
        assertTrue(c.values().get(3).definitelyEquals(RpnCalculator.parse("0.15")));
        assertEquals("99", c.values().get(1).bigIntegerValue().toString());
        assertEquals("45", c.entry());
        assertTrue(c.isEditing());
        assertEquals(1, c.selectionStart());
        assertEquals(history + 1, c.undoCount());
        c = TestCalculator.load(c.save());
        c.arithmeticKey("UNDO");
        assertEquals(3, c.values().size());
        assertEquals("45", c.entry());
        c.arithmeticKey("REDO");
        assertTrue(c.values().get(3).definitelyEquals(RpnCalculator.parse("0.15")));
    }

    @Test
    void irrationalMeanRetainsExactExpressionAcrossRestore() throws Exception {
        TestCalculator c = new TestCalculator();
        c.key("π");
        c.key("π");
        c.meanRows(0, 1);
        TestCalculator restored = TestCalculator.load(c.save());
        assertTrue(restored.values().get(2).definitelyEquals(UnifiedReal.PI));
        restored.key("sin");
        assertTrue(restored.values().get(2).definitelyZero());
    }

    @Test
    void meanOfSingleRowAppendsThatValue() throws Exception {
        TestCalculator c = new TestCalculator();
        CalculatorTestSupport.paste(c, "7");
        c.key("ENTER");
        c.meanRows(0);
        assertEquals(2, c.values().size());
        assertTrue(c.values().get(1).definitelyEquals(RpnCalculator.parse("7")));
    }

    @Test
    void invalidRowsAreAtomicAndEmptySelectionIsANoop() throws Exception {
        TestCalculator c = new TestCalculator();
        c.key("π");
        byte[] before = c.save();
        assertThrows(IllegalArgumentException.class, () -> c.meanRows(0, 1));
        assertArrayEquals(before, c.save());
        assertThrows(IllegalArgumentException.class, () -> c.meanRows(-1, 0));
        assertArrayEquals(before, c.save());
        c.meanRows();
        assertArrayEquals(before, c.save());
    }
}
