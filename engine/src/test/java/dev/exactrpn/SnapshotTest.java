// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SnapshotTest {
    @Test
    void draftSnapshotsRestoreTheLocalEditHistory() throws Exception {
        TestCalculator calculator = new TestCalculator();
        calculator.key("1");
        calculator.key("2");
        calculator.key("3");
        EntryEditor editor = TestCalculator.load(calculator.save()).detachEntryEditor();
        editor.key("UNDO");
        assertEquals("12", editor.entry());
        editor.key("REDO");
        assertEquals("123", editor.entry());
    }

    @Test
    void replacingAnEntryDoesNotNestOrGrowTheArithmeticSection() throws Exception {
        TestCalculator calculator = new TestCalculator();
        calculator.key("π");
        byte[] first = calculator.save();
        EntryEditor entry = new EntryEditor();
        entry.key("7");
        byte[] second = RpnCalculator.saveWithEntry(first, entry);
        byte[] third = RpnCalculator.saveWithEntry(second, entry);
        assertArrayEquals(second, third);
        assertEquals("7", TestCalculator.load(third).entry());
        assertEquals(calculator.values().size(), TestCalculator.load(third).values().size());
    }
}
