// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SnapshotTest {
    @Test
    void ordinaryEngineSnapshotsExposeTheirEditHistoryToTheLocalEditor() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("1");
        calculator.key("2");
        calculator.key("3");
        EntryEditor editor = RpnCalculator.load(calculator.save()).detachEntryEditor();
        editor.key("UNDO");
        assertEquals("12", editor.entry());
        editor.key("REDO");
        assertEquals("123", editor.entry());
    }

    @Test
    void replacingAnEntryDoesNotNestOrGrowTheArithmeticSection() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("π");
        byte[] first = calculator.save();
        EntryEditor entry = new EntryEditor();
        entry.key("7");
        byte[] second = RpnCalculator.saveWithEntry(first, entry);
        byte[] third = RpnCalculator.saveWithEntry(second, entry);
        assertArrayEquals(second, third);
        assertEquals("7", RpnCalculator.load(third).entry());
        assertEquals(calculator.values().size(), RpnCalculator.load(third).values().size());
    }
}
