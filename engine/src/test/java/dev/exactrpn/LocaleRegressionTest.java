// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class LocaleRegressionTest extends RegressionSupport {
    @Test
    void localePasteAndCursorEditing() throws Exception {
        NumberText.Profile us = new NumberText.Profile('.', ',', 3, 3);
        NumberText.Profile de = new NumberText.Profile(',', '.', 3, 3);
        assertTrue(NumberText.analyze("1,234", us).ambiguous(), "comma can be decimal or grouping");
        assertTrue(
                NumberText.analyze("1.234", us).ambiguous(),
                "dot can be decimal or foreign grouping");
        assertTrue(
                NumberText.analyze("1.234", de).ambiguous(),
                "locale does not resolve unknown clipboard");
        assertEquals(
                "1234.56",
                NumberText.analyze("1.234,56", us).canonical(),
                "explicit European format is unambiguous");
        assertEquals(
                "1234.56",
                NumberText.analyze("1,234.56", de).canonical(),
                "explicit US format is unambiguous");
        assertEquals(
                "1234.56",
                NumberText.analyze("1’234.56", de).canonical(),
                "apostrophe grouping is explicit");
        assertEquals(
                "1.2340",
                NumberText.analyze("1.2340", de).canonical(),
                "trailing zero resolves ambiguity");
        assertTrue(
                !NumberText.analyze("hello", us).valid(),
                "invalid pasted text has no guessed value");
        RpnCalculator cursor = new RpnCalculator();
        keys(cursor, "9", "ENTER");
        cursor.pasteText("1,234", us);
        assertTrue(
                cursor.isEditing()
                        && cursor.rawEntry()
                        && cursor.entryProblem().equals("Ambiguous"),
                "ambiguous paste stays raw and red");
        assertEquals(1, cursor.values().size(), "paste never commits");
        assertEquals("1,234", cursor.entry(), "paste preserves text");
        try {
            cursor.key("ENTER");
            throw new AssertionError("ambiguous number committed");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(
                cursor.values().size() == 1 && cursor.entry().equals("1,234"),
                "ambiguous Enter preserves text and stack");
        try {
            cursor.key("+");
            throw new AssertionError("ambiguous arithmetic accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(
                cursor.values().size() == 1 && cursor.rawEntry(),
                "arithmetic also cannot guess ambiguity");
        cursor.select(1, 2);
        cursor.key(".");
        assertTrue(
                cursor.entry().equals("1.234")
                        && !cursor.rawEntry()
                        && cursor.entryProblem().isEmpty(),
                "explicit decimal replaces selected comma");
        assertTrue(
                cursor.selectionStart() == 2 && cursor.selectionEnd() == 2,
                "cursor stays next to replaced decimal");
        cursor.key("ENTER");
        assertTrue(
                top(cursor).definitelyEquals(RpnCalculator.parse("1.234")),
                "corrected decimal commits exactly");
        cursor.key("UNDO");
        assertTrue(
                cursor.isEditing()
                        && cursor.entry().equals("1.234")
                        && cursor.selectionStart() == 2,
                "undo restores editing and caret");
        cursor.select(1, 1);
        cursor.key("DELETE");
        assertTrue(
                cursor.entry().equals("1234") && cursor.selectionStart() == 1,
                "Delete removes character after cursor");
        cursor.key("UNDO");
        cursor.select(2, 2);
        cursor.key("BACK");
        assertTrue(
                cursor.entry().equals("1234") && cursor.selectionStart() == 1,
                "Backspace removes character before cursor");
        cursor.select(1, 3);
        cursor.key("8");
        assertEquals("184", cursor.entry(), "digit replaces selected range");
        cursor.key("UNDO");
        assertTrue(
                cursor.entry().equals("1234")
                        && cursor.selectionStart() == 1
                        && cursor.selectionEnd() == 3,
                "undo restores selection");
        cursor.pasteText("1,234", us);
        cursor.key("BACK");
        assertTrue(
                cursor.entry().equals("1.23") && !cursor.rawEntry(),
                "editing into unambiguous decimal clears warning");
        cursor.pasteText("not a number", us);
        assertTrue(
                cursor.entryProblem().equals("Invalid") && cursor.isEditing(),
                "invalid paste remains editable");
        cursor.select(0, cursor.entry().length());
        cursor.key("7");
        assertTrue(
                cursor.entry().equals("7") && cursor.entryProblem().isEmpty(),
                "replace invalid paste with valid input");
        cursor.pasteText("1234,56", de);
        assertTrue(
                cursor.entry().equals("1234.56") && cursor.isEditing(),
                "unambiguous local paste stays uncommitted");
        cursor.setInputProfile(de);
        cursor.pasteText("1.234", de);
        cursor.select(1, 2);
        cursor.key(".");
        assertTrue(
                !cursor.rawEntry() && cursor.entry().equals("1.234"),
                "locale decimal key explicitly resolves dot grouping");
        cursor.pasteText("1.234", de);
        cursor.editText("1,234", 2, 2, true, true, de);
        assertTrue(
                !cursor.rawEntry() && cursor.entry().equals("1.234"),
                "native single-decimal edit resolves meaning");
        cursor.pasteText("1,234", us);
        RpnCalculator cursorCopy = CalculatorTestSupport.copy(cursor);
        cursorCopy.select(1, 2);
        cursorCopy.key(".");
        assertTrue(
                cursor.rawEntry() && cursor.entryProblem().equals("Ambiguous"),
                "candidate edits cannot change live ambiguous entry");
        cursor.pasteText("1.23", us);
        cursor.select(0, 0);
        cursor.key("8");
        assertEquals("81.23", cursor.entry(), "insert at beginning");
        cursor.select(cursor.entry().length(), cursor.entry().length());
        cursor.key("BACK");
        assertEquals("81.2", cursor.entry(), "append-end deletion remains supported");
        cursor.pasteText("123", us);
        cursor.key("ENTER");
        cursor.key("DELETE");
        assertTrue(!cursor.isEditing(), "Delete in stack mode drops without opening entry");
        RpnCalculator accept = new RpnCalculator();
        keys(accept, "2", "ENTER");
        accept.pasteText("12.34", us);
        assertTrue(
                accept.isEditing() && accept.values().size() == 1,
                "safe paste waits in entry without auto commit");
        accept.key("+");
        assertTrue(
                !accept.isEditing() && top(accept).definitelyEquals(RpnCalculator.parse("14.34")),
                "operator accepts safe pasted number");
        accept.key("UNDO");
        assertTrue(
                accept.isEditing()
                        && accept.entry().equals("12.34")
                        && top(accept).definitelyEquals(UnifiedReal.TWO),
                "undo restores safe paste before operator");
        accept.key("ENTER");
        assertTrue(
                !accept.isEditing() && accept.values().size() == 2,
                "Enter accepts safe pasted number");
        accept.pasteText("9", us);
        accept.key("√");
        assertTrue(
                !accept.isEditing() && top(accept).definitelyEquals(new UnifiedReal(3)),
                "function accepts safe pasted number");
        RpnCalculator commaPaste = new RpnCalculator();
        keys(commaPaste, "2", "ENTER");
        commaPaste.pasteText("9,5", us);
        commaPaste.key("+");
        assertTrue(
                top(commaPaste).definitelyEquals(RpnCalculator.parse("11.5")),
                "safe comma decimal accepted by operator");
        RpnCalculator blocked = new RpnCalculator();
        keys(blocked, "9", "ENTER");
        blocked.pasteText("1,234", us);
        for (String operation :
                new String[] {"ENTER", "+", "−", "×", "÷", "√", "sin", "ln", "eˣ"}) {
            try {
                blocked.key(operation);
                throw new AssertionError("ambiguous input accepted by " + operation);
            } catch (IllegalArgumentException expected) {
                assertTrue(
                        blocked.rawEntry()
                                && blocked.values().size() == 1
                                && blocked.entry().equals("1,234"),
                        "ambiguity blocks " + operation);
            }
        }
        blocked.select(1, 2);
        blocked.key(".");
        blocked.key("+");
        assertTrue(
                top(blocked).definitelyEquals(RpnCalculator.parse("10.234")),
                "corrected decimal can proceed with operator");
        RpnCalculator localized = new RpnCalculator();
        localized.setInputProfile(de);
        keys(localized, "2", "ENTER");
        localized.pasteText("1.234", de);
        localized.select(1, 2);
        localized.key(".");
        localized.key("+");
        assertTrue(
                top(localized).definitelyEquals(RpnCalculator.parse("3.234")),
                "configured comma resolves input before operator");
        accept.pasteText("1.234", us);
        accept.select(1, 1);
        accept.key("DELETE");
        assertTrue(
                accept.entry().equals("1234") && accept.entryProblem().isEmpty(),
                "removing ambiguous separator makes a safe integer");
        accept.key("√");
        assertTrue(!accept.isEditing(), "function accepts corrected safe entry");
        try {
            normalize("1.234", '.', ',', 3, 3);
            throw new AssertionError("normalizer guessed ambiguity");
        } catch (IllegalArgumentException expected) {
            assertTrue(true, "all public clipboard parsing rejects ambiguity");
        }
        accept.pasteText("", us);
        assertTrue(
                accept.isEditing() && accept.entry().isEmpty(), "empty paste remains in edit mode");
        accept.key("ENTER");
        assertTrue(!accept.isEditing(), "Enter leaves empty pasted edit without pushing zero");
        assertEquals("LEFT", KeyboardInput.command(21, 0, false, false, false), "left key mapping");
        assertEquals(
                "RIGHT", KeyboardInput.command(22, 0, false, false, false), "right key mapping");
        assertEquals(
                "HOME", KeyboardInput.command(122, 0, false, false, false), "home key mapping");
        assertEquals("END", KeyboardInput.command(123, 0, false, false, false), "end key mapping");
        assertEquals(
                "HOME", KeyboardInput.command(122, 0, true, false, false), "Ctrl Home mapping");
        assertEquals("END", KeyboardInput.command(123, 0, true, false, false), "Ctrl End mapping");
        RpnCalculator nav = new RpnCalculator();
        CalculatorTestSupport.paste(nav, "123.45");
        nav.key("LEFT");
        assertTrue(nav.selectionEnd() == 5, "left moves one character");
        nav.key("RIGHT");
        assertTrue(nav.selectionEnd() == 6, "right moves one character");
        nav.key("HOME");
        assertTrue(
                nav.selectionStart() == 0 && nav.selectionEnd() == 0, "home moves to entry start");
        nav.key("LEFT");
        assertTrue(nav.selectionEnd() == 0, "left stays at start");
        nav.key("END");
        nav.key("RIGHT");
        assertTrue(nav.selectionEnd() == 6, "end and right stay at end");
        nav.key("SELECT_LEFT");
        assertTrue(
                nav.selectionStart() == 6 && nav.selectionEnd() == 5,
                "Shift Left extends backwards");
        nav.key("SELECT_HOME");
        assertTrue(
                nav.selectionStart() == 6 && nav.selectionEnd() == 0,
                "Shift Home selects to start");
        nav.key("RIGHT");
        assertTrue(
                nav.selectionStart() == 6 && nav.selectionEnd() == 6,
                "right collapses selection to right edge");
        nav.key("HOME");
        nav.key("SELECT_RIGHT");
        assertTrue(
                nav.selectionStart() == 0 && nav.selectionEnd() == 1,
                "Shift Right extends forwards");
        nav.key("SELECT_END");
        assertTrue(
                nav.selectionStart() == 0 && nav.selectionEnd() == 6, "Shift End selects to end");
        nav.key("LEFT");
        assertTrue(
                nav.selectionStart() == 0 && nav.selectionEnd() == 0,
                "left collapses selection to left edge");
        nav.key("RIGHT");
        nav.key("RIGHT");
        nav.key("9");
        assertEquals("1293.45", nav.entry(), "navigation places inserted digit correctly");
        nav.key("UNDO");
        assertTrue(
                nav.entry().equals("123.45") && nav.selectionEnd() == 2,
                "undo restores edit and navigation position");
        nav.key("ENTER");
        nav.key("HOME");
        assertTrue(
                !nav.isEditing() && nav.selectionEnd() == 0,
                "navigation does not start editing stack");
    }
}
