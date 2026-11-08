// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class EntryDraftTest {
    @Test
    void completedEntryKeepsBoundaryButDropsCharacterHistory() throws Exception {
        RpnCalculator engine = new RpnCalculator(), draft = new RpnCalculator();
        draft.key("1");
        draft.key("2");
        draft.key("3");
        engine.useEntryEditor(RpnCalculator.load(draft.entryForCommit().save()));
        engine.key("ENTER");
        assertEquals("123", engine.values().get(0).bigIntegerValue().toString());
        engine.key("UNDO");
        RpnCalculator resumed = engine.detachEntryEditor();
        assertEquals("123", resumed.entry());
        resumed.key("UNDO");
        assertFalse(resumed.isEditing());
        assertEquals("", resumed.entry());
        resumed.key("REDO");
        assertEquals("123", resumed.entry());
        engine.useEntryEditor(resumed.entryForCommit());
        engine.key("REDO");
        assertEquals("123", engine.values().get(0).bigIntegerValue().toString());
        assertFalse(engine.isEditing());
    }

    @Test
    void lifecycleEnvelopeRestoresDraftUndoRedoAndArithmeticIndependently() throws Exception {
        RpnCalculator engine = new RpnCalculator(), draft = new RpnCalculator();
        CalculatorTestSupport.paste(engine, "42");
        engine.key("ENTER");
        draft.key("1");
        draft.key("2");
        draft.key("3");
        draft.key("UNDO");
        byte[] calculation = engine.save();
        RpnCalculator restored =
                RpnCalculator.load(RpnCalculator.saveWithEntry(calculation, draft));
        assertEquals("12", restored.entry());
        RpnCalculator editor = restored.detachEntryEditor();
        assertEquals(1, restored.values().size());
        assertEquals(2, editor.undoCount());
        editor.key("REDO");
        assertEquals("123", editor.entry());
        editor.key("UNDO");
        editor.key("UNDO");
        assertEquals("1", editor.entry());
        assertArrayEquals(calculation, engine.save());
        restored.useEntryEditor(editor.entryForCommit());
        restored.key("+");
        assertEquals("43", restored.values().get(0).bigIntegerValue().toString());
    }

    @Test
    void lifecycleRestoresRedoAfterUndoLeavesEntryMode() throws Exception {
        RpnCalculator engine = new RpnCalculator(), draft = new RpnCalculator();
        draft.key("7");
        draft.key("UNDO");
        RpnCalculator restored =
                RpnCalculator.load(RpnCalculator.saveWithEntry(engine.save(), draft));
        RpnCalculator editor = restored.detachEntryEditor();
        assertFalse(editor.isEditing());
        editor.key("REDO");
        assertEquals("7", editor.entry());
        assertTrue(editor.isEditing());
    }

    @Test
    void envelopeRejectsTruncationAndNestedEnvelopes() throws Exception {
        RpnCalculator engine = new RpnCalculator();
        byte[] saved = RpnCalculator.saveWithEntry(engine.save(), engine);
        assertThrows(
                java.io.IOException.class,
                () -> RpnCalculator.load(java.util.Arrays.copyOf(saved, saved.length - 1)));
        assertThrows(
                java.io.IOException.class,
                () -> RpnCalculator.load(RpnCalculator.saveWithEntry(saved, engine)));
    }

    @Test
    void completedDraftKeepsAFullHistoryWithinItsStorageLimit() throws Exception {
        RpnCalculator engine = new RpnCalculator(), draft = new RpnCalculator();
        for (int i = 0; i < 110; i++) {
            CalculatorTestSupport.paste(engine, Integer.toString(i));
            engine.key("ENTER");
        }
        assertEquals(100, engine.undoCount());
        draft.key("7");
        engine.useEntryEditor(draft.entryForCommit());
        engine.key("ENTER");
        RpnCalculator restored = RpnCalculator.load(engine.save());
        assertEquals(100, restored.undoCount());
        restored.key("UNDO");
        assertEquals("7", restored.entry());
        assertEquals(110, restored.values().size());
    }

    @Test
    void aRestoredNewDraftCannotRedoTheAbandonedArithmeticBranch() throws Exception {
        RpnCalculator engine = new RpnCalculator(), draft = new RpnCalculator();
        CalculatorTestSupport.paste(engine, "42");
        engine.key("ENTER");
        engine.key("UNDO");
        engine.detachEntryEditor();
        draft.key("9");
        RpnCalculator restored =
                RpnCalculator.load(RpnCalculator.saveWithEntry(engine.save(), draft, true));
        restored.key("REDO");
        assertEquals("9", restored.entry());
        assertTrue(restored.values().isEmpty());
    }
}
