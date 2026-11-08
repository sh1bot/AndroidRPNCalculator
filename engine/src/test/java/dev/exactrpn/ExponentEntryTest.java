// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ExponentEntryTest {
    @Test
    void exponentEntryCommitsExactlyAndUndoRestoresTheCompleteEntry() {
        RpnCalculator c = new RpnCalculator();
        CalculatorTestSupport.paste(c, "1.23");
        c.key("HOLD_DECIMAL");
        assertEquals("1.23e", c.entry());
        assertTrue(c.isEditing());
        c.key("4");
        c.key("ENTER");
        assertEquals("12300", c.values().get(0).bigIntegerValue().toString());
        c.key("UNDO");
        assertEquals("1.23e4", c.entry());
    }

    @Test
    void insertionUsesCursorAndSelectionAndDoesNotDuplicateTheExponent() {
        RpnCalculator c = new RpnCalculator();
        CalculatorTestSupport.paste(c, "1234");
        c.select(2, 2);
        c.key("HOLD_DECIMAL");
        assertEquals("12e34", c.entry());
        assertEquals(3, c.selectionStart());
        int history = c.undoCount();
        c.key("HOLD_DECIMAL");
        assertEquals("12e34", c.entry());
        assertEquals(history, c.undoCount());
        c.key("UNDO");
        assertEquals("1234", c.entry());
        c.select(1, 3);
        c.key("HOLD_DECIMAL");
        assertEquals("1e4", c.entry());
    }

    @Test
    void longPressDoesNothingInStackModeAndAcceptsLocaleEntry() {
        RpnCalculator c = new RpnCalculator();
        c.key("π");
        int history = c.undoCount();
        c.key("HOLD_DECIMAL");
        assertFalse(c.isEditing());
        assertEquals(1, c.values().size());
        assertEquals(history, c.undoCount());
        NumberText.Profile comma = new NumberText.Profile(',', '.', 3, 3);
        c.setInputProfile(comma);
        c.pasteText("1,25", comma);
        c.key("HOLD_DECIMAL");
        c.key("2");
        c.key("ENTER");
        assertEquals("125", c.values().get(1).bigIntegerValue().toString());
    }
}
