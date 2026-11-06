// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class SelectedSumTest {
    @Test
    void sumIsExactDeduplicatedAndPreservesOriginalsAndEntry() throws Exception {
        RpnCalculator c = new RpnCalculator();
        for (String text : new String[] {"0.1", "99", "0.2"}) {
            c.paste(text);
            c.key("ENTER");
        }
        c.paste("45");
        c.select(1, 1);
        Command command = Command.parse("SUM_ROWS:2,0,2");
        assertTrue(command.preservesSelection());
        assertArrayEquals(new int[] {2, 0, 2}, command.rows());
        int history = c.undoCount();
        command.apply(c, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals(4, c.values().size());
        assertTrue(c.values().get(3).definitelyEquals(RpnCalculator.parse("0.3")));
        assertEquals("99", c.values().get(1).bigIntegerValue().toString());
        assertEquals("45", c.entry());
        assertTrue(c.isEditing());
        assertEquals(1, c.selectionStart());
        assertEquals(history + 1, c.undoCount());
        c = RpnCalculator.load(c.save());
        c.key("UNDO");
        assertEquals(3, c.values().size());
        assertEquals("45", c.entry());
        c.key("REDO");
        assertTrue(c.values().get(3).definitelyEquals(RpnCalculator.parse("0.3")));
    }

    @Test
    void irrationalSumRetainsExactExpressionAcrossRestore() throws Exception {
        RpnCalculator c = new RpnCalculator();
        c.key("π");
        c.key("π");
        c.sumRows(0, 1);
        RpnCalculator restored = RpnCalculator.load(c.save());
        assertTrue(
                restored.values()
                        .get(2)
                        .definitelyEquals(UnifiedReal.PI.multiply(UnifiedReal.TWO)));
        restored.key("sin");
        assertTrue(restored.values().get(2).definitelyZero());
    }

    @Test
    void invalidRowsAreAtomicAndEmptySelectionIsANoop() throws Exception {
        RpnCalculator c = new RpnCalculator();
        c.key("π");
        byte[] before = c.save();
        assertThrows(IllegalArgumentException.class, () -> c.sumRows(0, 1));
        assertArrayEquals(before, c.save());
        assertThrows(IllegalArgumentException.class, () -> c.sumRows(-1, 0));
        assertArrayEquals(before, c.save());
        c.sumRows();
        assertArrayEquals(before, c.save());
    }
}
