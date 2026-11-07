// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class RowsRegressionTest extends RegressionSupport {
    @Test
    void dropRows() throws Exception {
        RpnCalculator rowDrop = new RpnCalculator();
        keys(rowDrop, "1", "ENTER", "2", "ENTER", "3", "ENTER", "4", "ENTER");
        rowDrop.pasteText("1,234", new NumberText.Profile('.', ',', 3, 3));
        rowDrop.select(1, 2);
        int beforeDropHistory = rowDrop.undoCount();
        rowDrop.dropRows(2, 0, 2);
        check(
                rowDrop.values().size() == 2
                        && rowDrop.values().get(0).definitelyEquals(UnifiedReal.TWO)
                        && top(rowDrop).definitelyEquals(new UnifiedReal(4)),
                "drop nonadjacent rows once, preserving order");
        check(
                rowDrop.rawEntry()
                        && rowDrop.entry().equals("1,234")
                        && rowDrop.selectionStart() == 1
                        && rowDrop.selectionEnd() == 2,
                "row drop preserves ambiguous entry and caret");
        check(rowDrop.undoCount() == beforeDropHistory + 1, "multi-row drop is one undo action");
        RpnCalculator rowDropRestored = RpnCalculator.load(rowDrop.save());
        rowDropRestored.key("UNDO");
        check(
                rowDropRestored.values().size() == 4 && rowDropRestored.entry().equals("1,234"),
                "undo multi-row drop after restart");
        rowDropRestored.key("REDO");
        check(
                rowDropRestored.values().size() == 2
                        && top(rowDropRestored).definitelyEquals(new UnifiedReal(4)),
                "redo multi-row drop");
        try {
            rowDrop.dropRows(0, 99);
            throw new AssertionError("invalid row accepted");
        } catch (IllegalArgumentException expected) {
        }
        check(
                rowDrop.values().size() == 2
                        && rowDrop.values().get(0).definitelyEquals(UnifiedReal.TWO),
                "invalid row drop is atomic");
        int noOpUndo = rowDrop.undoCount();
        rowDrop.dropRows();
        check(rowDrop.undoCount() == noOpUndo, "empty row drop does not add undo");
        rowDrop.dropRows(1, 0);
        check(
                rowDrop.values().isEmpty() && rowDrop.rawEntry(),
                "drop entire stack without discarding entry");
    }

    @Test
    void pickRows() throws Exception {
        RpnCalculator pickedRows = new RpnCalculator();
        keys(pickedRows, "1", "ENTER", "2", "ENTER", "3", "ENTER", "4", "ENTER");
        pickedRows.pasteText("1,234", new NumberText.Profile('.', ',', 3, 3));
        pickedRows.select(1, 2);
        int pickHistory = pickedRows.undoCount();
        pickedRows.pickRows(3, 1, 3);
        check(
                pickedRows.values().size() == 6
                        && pickedRows.values().get(4).definitelyEquals(UnifiedReal.TWO)
                        && top(pickedRows).definitelyEquals(new UnifiedReal(4)),
                "multi-pick appends in display order with originals retained");
        check(
                pickedRows.rawEntry()
                        && pickedRows.entry().equals("1,234")
                        && pickedRows.selectionStart() == 1
                        && pickedRows.selectionEnd() == 2,
                "pick leaves unfinished entry and cursor intact");
        check(pickedRows.undoCount() == pickHistory + 1, "multi-pick is one undo action");
        RpnCalculator pickedRestored = RpnCalculator.load(pickedRows.save());
        pickedRestored.key("UNDO");
        check(pickedRestored.values().size() == 4, "undo pick after restore");
        pickedRestored.key("REDO");
        check(
                pickedRestored.values().size() == 6
                        && top(pickedRestored).definitelyEquals(new UnifiedReal(4)),
                "redo restores picked rows");
        try {
            pickedRows.pickRows(0, 99);
            throw new AssertionError("invalid pick accepted");
        } catch (IllegalArgumentException expected) {
        }
        check(pickedRows.values().size() == 6, "invalid multi-pick is atomic");
        int noPickHistory = pickedRows.undoCount();
        pickedRows.pickRows();
        check(pickedRows.undoCount() == noPickHistory, "empty pick does not add history");
        RpnCalculator exactPick = new RpnCalculator();
        keys(exactPick, "π", "1", "ENTER", "3", "÷");
        exactPick.pickRows(0, 1);
        check(
                exactPick.values().size() == 4
                        && exactPick.values().get(2).definitelyEquals(UnifiedReal.PI)
                        && top(exactPick).multiply(new UnifiedReal(3)).definitelyOne(),
                "pick preserves irrational and recurring values exactly");
    }

    @Test
    void rollStack() throws Exception {
        RpnCalculator rolled = new RpnCalculator();
        keys(rolled, "1", "ENTER", "2", "ENTER", "3", "ENTER");
        int rollHistory = rolled.undoCount();
        rolled.key("ROLL_UP");
        check(
                rolled.values().get(0).definitelyEquals(UnifiedReal.TWO)
                        && rolled.values().get(1).definitelyEquals(new UnifiedReal(3))
                        && top(rolled).definitelyOne(),
                "roll up moves displayed values upward, wrapping top to X");
        check(rolled.undoCount() == rollHistory + 1, "roll is one undoable action");
        RpnCalculator rollRestored = RpnCalculator.load(rolled.save());
        rollRestored.key("UNDO");
        check(
                rollRestored.values().get(0).definitelyOne()
                        && top(rollRestored).definitelyEquals(new UnifiedReal(3)),
                "undo roll after restore");
        rollRestored.key("REDO");
        check(top(rollRestored).definitelyOne(), "redo roll after restore");
        rolled.key("ROLL_DOWN");
        check(
                rolled.values().get(0).definitelyOne()
                        && top(rolled).definitelyEquals(new UnifiedReal(3)),
                "roll down reverses roll up");
        keys(rolled, "ROLL_UP", "ROLL_UP", "ROLL_UP");
        check(
                rolled.values().get(0).definitelyOne()
                        && top(rolled).definitelyEquals(new UnifiedReal(3)),
                "full roll cycle restores original order");
        RpnCalculator emptyRoll = new RpnCalculator();
        keys(emptyRoll, "ROLL_UP", "ROLL_DOWN");
        check(emptyRoll.isEmpty() && emptyRoll.undoCount() == 0, "empty roll is a no-op");
        keys(emptyRoll, "7", "ENTER");
        int singleRollHistory = emptyRoll.undoCount();
        keys(emptyRoll, "ROLL_UP", "ROLL_DOWN");
        check(
                top(emptyRoll).definitelyEquals(new UnifiedReal(7))
                        && emptyRoll.undoCount() == singleRollHistory,
                "single-item roll is a no-op");
        RpnCalculator editRoll = new RpnCalculator();
        keys(editRoll, "1", "ENTER", "2", "ROLL_UP");
        check(
                !editRoll.isEditing()
                        && editRoll.values().size() == 2
                        && top(editRoll).definitelyOne(),
                "roll commits entry before cycling");
        editRoll.key("UNDO");
        check(
                editRoll.isEditing()
                        && editRoll.entry().equals("2")
                        && editRoll.values().size() == 1,
                "undo roll restores entry");
        editRoll.pasteText("1,234", new NumberText.Profile('.', ',', 3, 3));
        try {
            editRoll.key("ROLL_DOWN");
            throw new AssertionError("ambiguous entry was rolled");
        } catch (IllegalArgumentException expected) {
        }
        check(
                editRoll.rawEntry()
                        && editRoll.entry().equals("1,234")
                        && editRoll.values().size() == 1,
                "roll refuses ambiguous entry without changing the stack");
    }
}
