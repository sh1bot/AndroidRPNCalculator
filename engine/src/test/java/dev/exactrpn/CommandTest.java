// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void modeStorageIdsStayStable() {
        assertEquals(FractionMode.MIXED, FractionMode.fromId(2));
        assertEquals(FractionMode.IMPROPER, FractionMode.fromId(1));
        assertEquals(FractionMode.DECIMAL, FractionMode.fromId(-1));
        assertEquals(NumberMode.DOT, NumberMode.fromId(1));
        assertEquals(NumberMode.UNGROUPED, NumberMode.DOT.next());
        assertEquals(FractionMode.MIXED, FractionMode.DECIMAL.next());
    }
}
