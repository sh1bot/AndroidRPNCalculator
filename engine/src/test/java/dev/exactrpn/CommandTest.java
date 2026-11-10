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
    void entryUpdatesStayLocalRatherThanBecomingArithmeticCommands() {
        EntryEditor editor = new EntryEditor();
        Command edit = Command.edit("123", 1, 1, false, false);
        edit.apply(editor, NumberText.COMPUTER, NumberText.COMPUTER);
        Command.select(2, 2).apply(editor, NumberText.COMPUTER, NumberText.COMPUTER);
        Command.key("BACK").apply(editor, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals("13", editor.entry());
        TestCalculator calculator = new TestCalculator();
        calculator.key("π");
        assertThrows(IllegalArgumentException.class, () -> edit.apply(calculator.engine));
        assertEquals(1, calculator.values().size());
        assertFalse(calculator.isEditing());
    }

    @Test
    void editRetainsEmbeddedPunctuation() {
        Command command = Command.edit("1:234,5", 1, 2, true, false);
        assertEquals(Command.Kind.EDIT, command.kind);
        assertEquals("1:234,5", command.text);
        assertEquals(1, command.start);
        assertEquals(2, command.end);
        assertTrue(command.raw);
        assertFalse(command.explicitDecimal);
    }

    @Test
    void rowCommandsOwnTheirIndicesAndRequireTheRightKind() {
        assertArrayEquals(
                new int[] {3, 1, 3}, Command.rows(Command.Kind.PICK_ROWS, 3, 1, 3).rows());
        assertArrayEquals(new int[] {0, 2}, Command.rows(Command.Kind.DROP_ROWS, 0, 2).rows());
        assertThrows(IllegalArgumentException.class, () -> Command.rows(Command.Kind.KEY, 0));
        assertThrows(IllegalArgumentException.class, () -> Command.unaryRows(Operation.ADD, 0));
        int[] source = {1};
        Command pick = Command.rows(Command.Kind.PICK_ROWS, source);
        source[0] = 7;
        int[] copy = pick.rows();
        copy[0] = 9;
        assertEquals(1, pick.rows()[0]);
    }

    @Test
    void onlyPresentationAndRowActionsPreserveSelection() {
        assertTrue(Command.key("FRACTION").preservesSelection());
        assertTrue(Command.select(1, 1).preservesSelection());
        assertTrue(Command.rows(Command.Kind.DROP_ROWS, 0).preservesSelection());
        assertFalse(Command.key("ENTER").preservesSelection());
        assertFalse(Command.edit("12", 0, 0, false, false).preservesSelection());
    }

    @Test
    void holdDropEditsWithoutCommittingOrMovingTheStack() {
        TestCalculator calculator = new TestCalculator();
        for (String key : new String[] {"1", "ENTER", "2", "ENTER", "3", "ENTER"})
            calculator.key(key);
        CalculatorTestSupport.paste(calculator, "45");
        calculator.key("HOLD_DROP");
        assertEquals("4", calculator.entry());
        calculator.key("HOLD_DROP");
        calculator.key("HOLD_DROP");
        assertTrue(calculator.isEditing());
        assertEquals("", calculator.entry());
        assertEquals(
                java.util.Arrays.asList("1", "2", "3"),
                CalculatorTestSupport.formattedValues(
                        calculator, ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL));
        calculator.key("ENTER");
        calculator.key("HOLD_DROP");
        assertEquals(
                java.util.Arrays.asList("3", "1", "2"),
                CalculatorTestSupport.formattedValues(
                        calculator, ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL));
        calculator.key("UNDO");
        assertEquals(
                java.util.Arrays.asList("1", "2", "3"),
                CalculatorTestSupport.formattedValues(
                        calculator, ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL));
    }

    @Test
    void heldDigitsPickOneBasedLevelsAndZeroPicksTen() throws Exception {
        for (int depth = 1; depth <= 10; depth++) {
            TestCalculator calculator = new TestCalculator();
            for (int n = 101; n <= 110; n++) {
                CalculatorTestSupport.paste(calculator, Integer.toString(n));
                calculator.key("ENTER");
            }
            com.android.calculator2.UnifiedReal original = calculator.values().get(10 - depth);
            int history = calculator.undoCount();
            String digit = depth == 10 ? "0" : Integer.toString(depth);
            Command hold = Command.holdDigit(digit);
            assertTrue(hold.preservesSelection());
            calculator.apply(hold, NumberText.COMPUTER, NumberText.COMPUTER);
            assertEquals(11, calculator.values().size());
            assertSame(original, calculator.values().get(10));
            assertEquals(history + 1, calculator.undoCount());
            calculator = TestCalculator.load(calculator.save());
            assertEquals(original.bigIntegerValue(), calculator.values().get(10).bigIntegerValue());
            calculator.key("UNDO");
            assertEquals(10, calculator.values().size());
            calculator.key("REDO");
            assertEquals(11, calculator.values().size());
        }
    }

    @Test
    void heldDigitsEditAtTheCursorWithoutPickingOrCommitting() {
        TestCalculator calculator = new TestCalculator();
        calculator.key("π");
        CalculatorTestSupport.paste(calculator, "45");
        calculator.select(1, 1);
        calculator.apply(Command.holdDigit("7"), NumberText.COMPUTER, NumberText.COMPUTER);
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
        TestCalculator calculator = new TestCalculator();
        calculator.key("π");
        int history = calculator.undoCount();
        assertThrows(IllegalArgumentException.class, () -> calculator.holdDigit("0"));
        assertEquals(1, calculator.values().size());
        assertEquals(history, calculator.undoCount());
        assertTrue(calculator.entry().isEmpty());
        assertFalse(calculator.isEditing());
        for (String bad : new String[] {"", "10", "-1", "."})
            assertThrows(IllegalArgumentException.class, () -> Command.holdDigit(bad));
    }

    @Test
    void modesFollowTheirDisplayCycle() {
        assertEquals(NumberMode.UNGROUPED, NumberMode.DOT.next());
        assertEquals(FractionMode.MIXED, FractionMode.DECIMAL.next());
        assertEquals(FractionMode.IMPROPER, FractionMode.MIXED.next());
        assertEquals(FractionMode.DECIMAL, FractionMode.IMPROPER.next());
    }
}
