// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class MultilinePasteTest {
    @Test
    void copiedFractionsPasteExactly() throws Exception {
        RpnCalculator c = new RpnCalculator();
        c.pasteClipboard("7/3\n-2 1/3", NumberText.COMPUTER);
        c = RpnCalculator.load(c.save());
        assertTrue(c.values().get(0).add(c.values().get(1)).definitelyZero());
    }

    @Test
    void batchIsAtomicOrderedUndoableAndRestorable() throws Exception {
        RpnCalculator c = new RpnCalculator();
        c.paste("7");
        Command.parse("PASTE_RAW:1\r\n\n2.5\n3").apply(c, NumberText.COMPUTER, NumberText.COMPUTER);
        assertFalse(c.isEditing());
        assertEquals(4, c.values().size());
        assertEquals("3", c.values().get(3).bigIntegerValue().toString());
        c = RpnCalculator.load(c.save());
        c.key("UNDO");
        assertEquals("7", c.entry());
        assertTrue(c.values().isEmpty());
        c.key("REDO");
        assertEquals(4, c.values().size());
    }

    @Test
    void badOrAmbiguousLineLeavesEntryStackAndHistoryUntouched() throws Exception {
        for (String text : new String[] {"1\nnope\n3", "1\n1,234\n3", "1\n1e1001\n3"}) {
            RpnCalculator c = new RpnCalculator();
            c.paste("8");
            byte[] before = c.save();
            Exception ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> c.pasteClipboard(text, NumberText.COMPUTER));
            assertEquals(CalculationError.Code.INVALID_PASTE_LINE, ((CalculationError) ex).code());
            assertEquals(2, ((CalculationError) ex).argument());
            assertArrayEquals(before, c.save());
        }
    }

    @Test
    void invalidExistingEntryBlocksBatchAndSingleValueStillEdits() throws Exception {
        RpnCalculator c = new RpnCalculator();
        c.pasteClipboard("1,234", NumberText.COMPUTER);
        byte[] before = c.save();
        assertTrue(c.isEditing());
        assertThrows(
                IllegalArgumentException.class,
                () -> c.pasteClipboard("1\n2", NumberText.COMPUTER));
        assertArrayEquals(before, c.save());
        RpnCalculator single = new RpnCalculator();
        single.pasteClipboard("12\n", NumberText.COMPUTER);
        assertTrue(single.isEditing());
        assertEquals("12", single.entry());
    }
}
