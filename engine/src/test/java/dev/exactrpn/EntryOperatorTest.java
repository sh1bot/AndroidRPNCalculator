// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class EntryOperatorTest {
    @Test
    void underflowCommitsAndCanBeUndoneAndRedoneAfterRestore() throws Exception {
        RpnCalculator c = new RpnCalculator();
        CalculatorTestSupport.paste(c, "123");
        Exception failure = assertThrows(IllegalArgumentException.class, () -> c.key("+"));
        assertEquals(CalculationError.Code.NEED_VALUES, ((CalculationError) failure).code());
        assertFalse(c.isEditing());
        assertEquals("123", c.values().get(0).bigIntegerValue().toString());
        RpnCalculator restored = RpnCalculator.load(c.save());
        restored.key("UNDO");
        assertTrue(restored.isEditing());
        assertEquals("123", restored.entry());
        assertTrue(restored.values().isEmpty());
        restored.key("REDO");
        assertFalse(restored.isEditing());
        CalculatorTestSupport.paste(restored, "2");
        restored.key("+");
        assertEquals("125", restored.values().get(0).bigIntegerValue().toString());
    }

    @Test
    void ambiguousAndInvalidEntryNeverReachTheOperator() {
        for (String text : new String[] {"1,234", "abc"}) {
            RpnCalculator c = new RpnCalculator();
            c.pasteText(text, new NumberText.Profile('.', ',', 3, 3));
            assertThrows(IllegalArgumentException.class, () -> c.key("+"));
            assertTrue(c.isEditing());
            assertEquals(text, c.entry());
            assertTrue(c.values().isEmpty());
        }
    }
}
