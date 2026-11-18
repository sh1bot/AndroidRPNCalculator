// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Canonicalisation translates selection offsets instead of merely clamping them. */
final class CursorOffsetTest {
    private static final NumberText.Profile US = new NumberText.Profile('.', ',', 3, 3);
    private static final NumberText.Profile DE = new NumberText.Profile(',', '.', 3, 3);

    private static EntryEditor rawEditor(NumberText.Profile profile) {
        EntryEditor editor = new EntryEditor();
        editor.pasteText("5", profile); // a raw capture fixes the profile later edits keep
        return editor;
    }

    @Test
    void rawCaretAfterGroupingSeparatorMovesWithTheSeparator() {
        EntryEditor editor = rawEditor(US);
        editor.editText("1,234,567", 2, 2, true, false, US);
        assertEquals("1234567", editor.entry());
        assertFalse(editor.rawEntry());
        assertEquals(1, editor.selectionStart());
        assertEquals(1, editor.selectionEnd());
    }

    @Test
    void rawSelectionSpanningGroupingSeparatorsIsTranslated() {
        EntryEditor editor = rawEditor(US);
        editor.editText("1,234,567", 1, 6, true, false, US);
        assertEquals("1234567", editor.entry());
        assertEquals(1, editor.selectionStart());
        assertEquals(4, editor.selectionEnd());
        assertEquals(
                "234", editor.entry().substring(editor.selectionStart(), editor.selectionEnd()));
    }

    @Test
    void explicitDecimalCaretAfterGroupingSeparatorMovesWithTheSeparator() {
        EntryEditor editor = new EntryEditor();
        editor.editText("1.234", 2, 2, true, true, DE);
        assertEquals("1234", editor.entry());
        assertFalse(editor.rawEntry());
        assertEquals(1, editor.selectionStart());
        assertEquals(1, editor.selectionEnd());
    }

    @Test
    void explicitDecimalPointKeepsTheCaretBesideIt() {
        EntryEditor editor = new EntryEditor();
        editor.editText("1,234", 2, 2, true, true, DE);
        assertEquals("1.234", editor.entry());
        assertEquals(2, editor.selectionStart());
        assertEquals(2, editor.selectionEnd());
    }
}
