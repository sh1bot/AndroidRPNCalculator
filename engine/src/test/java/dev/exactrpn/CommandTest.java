// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CommandTest {
    @Test
    void editRetainsEmbeddedPunctuation() {
        Command command = Command.parse("EDIT:1:2:true:false:1:234,5");
        assertEquals(Command.Kind.EDIT, command.kind);
        assertEquals("1:234,5", command.text);
        assertEquals(1, command.start);
        assertEquals(2, command.end);
        assertTrue(command.raw);
        assertFalse(command.explicitDecimal);
    }

    @Test
    void rowCommandsShareValidatedParsing() {
        assertArrayEquals(new int[] {3, 1, 3}, Command.parse("PICK_ROWS:3,1,3").rows());
        assertArrayEquals(new int[] {0, 2}, Command.parse("DROP_ROWS:0,2").rows());
        assertThrows(IllegalArgumentException.class, () -> Command.parse("DROP_ROWS:hello"));
        assertThrows(IllegalArgumentException.class, () -> Command.parse("EDIT:1:2"));
        int[] copy = Command.parse("PICK_ROWS:1").rows();
        copy[0] = 9;
        assertEquals(1, Command.parse("PICK_ROWS:1").rows()[0]);
    }

    @Test
    void onlyPresentationAndRowActionsPreserveSelection() {
        assertTrue(Command.parse("FRACTION").preservesSelection());
        assertTrue(Command.parse("SELECT:1:1").preservesSelection());
        assertTrue(Command.parse("DROP_ROWS:0").preservesSelection());
        assertFalse(Command.parse("ENTER").preservesSelection());
        assertFalse(Command.parse("EDIT:0:0:false:false:12").preservesSelection());
    }

    @Test
    void holdDropEditsWithoutCommittingOrMovingTheStack() {
        RpnCalculator calculator = new RpnCalculator();
        for (String key : new String[] {"1", "ENTER", "2", "ENTER", "3", "ENTER"})
            calculator.key(key);
        calculator.paste("45");
        calculator.key("HOLD_DROP");
        assertEquals("4", calculator.entry());
        calculator.key("HOLD_DROP");
        calculator.key("HOLD_DROP");
        assertTrue(calculator.isEditing());
        assertEquals("", calculator.entry());
        assertEquals(
                java.util.Arrays.asList("1", "2", "3"),
                calculator.formattedValues(ValueFormatter.Notation.SCIENTIFIC));
        calculator.key("ENTER");
        calculator.key("HOLD_DROP");
        assertEquals(
                java.util.Arrays.asList("3", "1", "2"),
                calculator.formattedValues(ValueFormatter.Notation.SCIENTIFIC));
        calculator.key("UNDO");
        assertEquals(
                java.util.Arrays.asList("1", "2", "3"),
                calculator.formattedValues(ValueFormatter.Notation.SCIENTIFIC));
    }

    @Test
    void heldDigitsPickOneBasedLevelsAndZeroPicksTen() throws Exception {
        for (int depth = 1; depth <= 10; depth++) {
            RpnCalculator calculator = new RpnCalculator();
            for (int n = 101; n <= 110; n++) {
                calculator.paste(Integer.toString(n));
                calculator.key("ENTER");
            }
            com.android.calculator2.UnifiedReal original = calculator.values().get(10 - depth);
            int history = calculator.undoCount();
            String digit = depth == 10 ? "0" : Integer.toString(depth);
            Command hold = Command.parse("HOLD_DIGIT:" + digit);
            assertTrue(hold.preservesSelection());
            hold.apply(calculator, NumberText.COMPUTER, NumberText.COMPUTER);
            assertEquals(11, calculator.values().size());
            assertSame(original, calculator.values().get(10));
            assertEquals(history + 1, calculator.undoCount());
            calculator = RpnCalculator.load(calculator.save());
            assertEquals(original.bigIntegerValue(), calculator.values().get(10).bigIntegerValue());
            calculator.key("UNDO");
            assertEquals(10, calculator.values().size());
            calculator.key("REDO");
            assertEquals(11, calculator.values().size());
        }
    }

    @Test
    void heldDigitsEditAtTheCursorWithoutPickingOrCommitting() {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("π");
        calculator.paste("45");
        calculator.select(1, 1);
        Command.parse("HOLD_DIGIT:7").apply(calculator, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals("475", calculator.entry());
        assertEquals(1, calculator.values().size());
        assertTrue(calculator.isEditing());
        calculator.key("CE");
        calculator.holdDigit("0");
        assertEquals("0", calculator.entry());
        assertEquals(1, calculator.values().size());
    }

    @Test
    void unavailableLevelsDoNotChangeStackOrHistory() {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("π");
        int history = calculator.undoCount();
        assertThrows(IllegalArgumentException.class, () -> calculator.holdDigit("0"));
        assertEquals(1, calculator.values().size());
        assertEquals(history, calculator.undoCount());
        assertTrue(calculator.entry().isEmpty());
        assertFalse(calculator.isEditing());
        for (String bad : new String[] {"", "10", "-1", "."})
            assertThrows(IllegalArgumentException.class, () -> Command.parse("HOLD_DIGIT:" + bad));
    }

    @Test
    void modeStorageIdsStayStable() {
        assertEquals(FractionMode.MIXED, FractionMode.fromId(2));
        assertEquals(FractionMode.IMPROPER, FractionMode.fromId(1));
        assertEquals(FractionMode.DECIMAL, FractionMode.fromId(-1));
        assertEquals(NumberMode.DOT, NumberMode.fromId(1));
        assertEquals(NumberMode.UNGROUPED, NumberMode.DOT.next());
        assertEquals(FractionMode.MIXED, FractionMode.DECIMAL.next());
    }
}
