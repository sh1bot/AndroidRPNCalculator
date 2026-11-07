// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class HistoryRegressionTest extends RegressionSupport {
    @Test
    void persistenceAndBudget() throws Exception {
        RpnCalculator durable = new RpnCalculator();
        number(durable, "56565642624.456");
        keys(durable, "ENTER", "π", "2", "√", "+", "1", "ENTER", "3", "÷");
        durable.key("ANGLE");
        durable.paste("12.345");
        durable.select(2, 5);
        RpnCalculator restored = RpnCalculator.load(durable.save());
        check(restored.values().size() == 3, "restore full stack");
        check(
                restored.values().get(0).definitelyEquals(RpnCalculator.parse("56565642624.456")),
                "restore exact large decimal");
        check(
                restored.values()
                        .get(1)
                        .toStringTruncated(80)
                        .equals(UnifiedReal.PI.add(UnifiedReal.TWO.sqrt()).toStringTruncated(80)),
                "restore exact irrational expression");
        check(
                top(restored).multiply(new UnifiedReal(3)).definitelyOne(),
                "restore exact recurring fraction");
        check(
                restored.entry().equals("12.345")
                        && restored.isEditing()
                        && restored.selectionStart() == 2
                        && restored.selectionEnd() == 5,
                "restore editing and cursor selection");
        check(restored.angleUnit() == RpnCalculator.AngleUnit.DEG, "restore angle mode");
        restored.key("UNDO");
        check(restored.entry().equals("") && restored.values().size() == 3, "restore undo history");
        restored.key("CLEAR");
        RpnCalculator cleared = RpnCalculator.load(restored.save());
        check(cleared.isEmpty() && cleared.undoCount() > 0, "empty foreground state retains undo");
        cleared.key("UNDO");
        check(cleared.values().size() == 3, "undo accidental Clear after restoration");
        cleared.key("CLEAR");
        cleared.clearUndo();
        check(
                cleared.isEmpty() && cleared.undoCount() == 0,
                "empty background state discards undo");
        cleared.key("UNDO");
        check(cleared.isEmpty(), "discarded undo cannot resurrect cleared values");
        RpnCalculator rawSaved = new RpnCalculator();
        rawSaved.pasteText("1,234", new NumberText.Profile('.', ',', 3, 3));
        rawSaved.select(1, 2);
        RpnCalculator rawRestored = RpnCalculator.load(rawSaved.save());
        check(
                rawRestored.rawEntry()
                        && rawRestored.entryProblem().equals("Ambiguous")
                        && rawRestored.entry().equals("1,234"),
                "restore ambiguous clipboard entry");
        check(
                rawRestored.selectionStart() == 1 && rawRestored.selectionEnd() == 2,
                "restore raw cursor selection");
        RpnCalculator angleSaved = new RpnCalculator();
        angleSaved.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        number(angleSaved, "12.34");
        angleSaved.key("sin");
        angleSaved.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        RpnCalculator angleRestored = RpnCalculator.load(angleSaved.save());
        check(
                ValueFormatter.format(top(angleRestored), true, sci)
                        .equals(ValueFormatter.format(top(angleSaved), true, sci)),
                "restore trig expression with original angle unit");
        RpnCalculator bounded = new RpnCalculator();
        for (int i = 0; i < 150; i++) bounded.paste(Integer.toString(i));
        check(
                bounded.undoCount() == 100 && RpnCalculator.load(bounded.save()).undoCount() == 100,
                "undo count is bounded across persistence");
        String longRaw = "字".repeat(4095);
        for (int i = 0; i < 110; i++)
            bounded.pasteText(longRaw + (i % 10), new NumberText.Profile('.', ',', 3, 3));
        byte[] boundedBytes = bounded.save();
        check(
                boundedBytes.length <= 1024 * 1024 && bounded.undoCount() < 100,
                "saved undo history respects byte budget");
        check(
                RpnCalculator.load(boundedBytes).entry().equals(longRaw + "9"),
                "undo eviction preserves current entry");
        for (String operation : new String[] {"±", "√", "1/x", "ln", "eˣ", "sin", "cos", "tan"}) {
            RpnCalculator expression = new RpnCalculator();
            keys(expression, "π", operation);
            RpnCalculator loaded = RpnCalculator.load(expression.save());
            check(
                    top(loaded).toStringTruncated(60).equals(top(expression).toStringTruncated(60)),
                    "restore unary expression " + operation);
            loaded.key("UNDO");
            check(top(loaded).definitelyEquals(UnifiedReal.PI), "restore unary undo " + operation);
        }
        for (String operation : new String[] {"+", "−", "×", "÷", "xʸ"}) {
            RpnCalculator expression = new RpnCalculator();
            keys(expression, "π", "2", "√", operation);
            RpnCalculator loaded = RpnCalculator.load(expression.save());
            check(
                    top(loaded).toStringTruncated(60).equals(top(expression).toStringTruncated(60)),
                    "restore binary expression " + operation);
        }
    }

    @Test
    void undoAndRedo() throws Exception {
        RpnCalculator editingHistory = new RpnCalculator();
        number(editingHistory, "123");
        editingHistory.key("UNDO");
        assertEquals("12", editingHistory.entry(), "current entry edits undo individually");
        editingHistory.key("REDO");
        assertEquals("123", editingHistory.entry(), "redo current entry edit");
        editingHistory.key("ENTER");
        check(editingHistory.undoCount() == 2, "commit drops intermediate entry edits");
        editingHistory.key("UNDO");
        check(
                editingHistory.entry().equals("123") && editingHistory.values().isEmpty(),
                "undo commit restores complete entry");
        editingHistory.key("UNDO");
        check(editingHistory.isEmpty(), "next undo skips intermediate spellings");
        editingHistory.key("REDO");
        assertEquals("123", editingHistory.entry(), "redo complete entry");
        editingHistory.key("REDO");
        check(
                top(editingHistory).definitelyEquals(new UnifiedReal(123))
                        && !editingHistory.isEditing(),
                "redo commit");
        RpnCalculator operationHistory = new RpnCalculator();
        number(operationHistory, "12");
        operationHistory.key("ENTER");
        number(operationHistory, "345");
        operationHistory.key("+");
        operationHistory.key("UNDO");
        check(
                operationHistory.entry().equals("345")
                        && top(operationHistory).definitelyEquals(new UnifiedReal(12)),
                "undo operator restores full entry");
        operationHistory.key("UNDO");
        check(
                operationHistory.entry().isEmpty()
                        && top(operationHistory).definitelyEquals(new UnifiedReal(12)),
                "operator commit drops intermediate edits");
        operationHistory.key("UNDO");
        check(
                operationHistory.entry().equals("12") && operationHistory.values().isEmpty(),
                "older calculation remains reachable");
        operationHistory.key("REDO");
        operationHistory.key("REDO");
        operationHistory.key("REDO");
        check(
                top(operationHistory).definitelyEquals(new UnifiedReal(357)),
                "redo full calculation sequence");
        operationHistory.key("CLEAR");
        operationHistory.key("UNDO");
        check(
                top(operationHistory).definitelyEquals(new UnifiedReal(357)),
                "undo Clear still works");
        operationHistory.key("REDO");
        check(operationHistory.isEmpty(), "redo Clear");
        operationHistory.key("UNDO");
        operationHistory.key("9");
        check(operationHistory.redoCount() == 0, "new edit discards redo branch");
        operationHistory.key("REDO");
        assertEquals("9", operationHistory.entry(), "discarded redo cannot replay stale Clear");
        RpnCalculator failedCommit = new RpnCalculator();
        number(failedCommit, "123");
        try {
            failedCommit.key("+");
            throw new AssertionError("underflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        check(
                failedCommit.undoCount() == 2,
                "failed operation commits entry and collapses edit history");
        failedCommit.key("UNDO");
        check(
                failedCommit.entry().equals("123"),
                "undo restores complete entry after failed operation");
        RpnCalculator failedRedo = new RpnCalculator();
        keys(failedRedo, "π", "UNDO");
        try {
            failedRedo.key("+");
            throw new AssertionError("underflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        check(failedRedo.redoCount() == 1, "failed operation preserves redo");
        failedRedo.key("REDO");
        check(
                top(failedRedo).definitelyEquals(UnifiedReal.PI),
                "redo exact constant after failure");
        RpnCalculator redoSaved = new RpnCalculator();
        keys(redoSaved, "π", "2", "√", "+", "UNDO");
        RpnCalculator redoRestored = RpnCalculator.load(redoSaved.save());
        check(
                redoRestored.redoCount() == 1 && redoRestored.entry().isEmpty(),
                "restore redo history");
        redoRestored.key("REDO");
        check(
                top(redoRestored)
                        .toStringTruncated(80)
                        .equals(UnifiedReal.PI.add(UnifiedReal.TWO.sqrt()).toStringTruncated(80)),
                "redo exact irrational operation after restart");
        redoRestored.key("UNDO");
        RpnCalculator redoCopy = redoRestored.copy();
        redoCopy.key("REDO");
        check(
                redoRestored.redoCount() == 1 && redoCopy.redoCount() == 0,
                "redo history isolated between candidates");
        redoRestored.clearUndo();
        check(
                redoRestored.undoCount() == 0 && redoRestored.redoCount() == 0,
                "clear history removes undo and redo");
        check(
                "REDO".equals(KeyboardInput.command(53, 0, true, false, false)),
                "Ctrl Y redo shortcut");
    }

    @Test
    void legacyPersistence() throws Exception {
        java.io.ByteArrayOutputStream legacyBytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream legacy = new java.io.DataOutputStream(legacyBytes);
        legacy.writeInt(0x52504e31);
        legacy.writeUTF("RAD");
        legacy.writeChar('.');
        legacy.writeChar(',');
        legacy.writeInt(3);
        legacy.writeInt(3);
        legacy.writeInt(0);
        legacy.writeInt(1);
        legacy.writeInt(0);
        legacy.writeUTF("12.3");
        legacy.writeBoolean(true);
        legacy.writeBoolean(false);
        legacy.writeInt(2);
        legacy.writeInt(4);
        legacy.writeChar('.');
        legacy.writeChar(',');
        legacy.writeInt(3);
        legacy.writeInt(3);
        RpnCalculator legacyRestored = RpnCalculator.load(legacyBytes.toByteArray());
        check(
                legacyRestored.entry().equals("12.3")
                        && legacyRestored.selectionStart() == 2
                        && legacyRestored.selectionEnd() == 4,
                "restore previous saved-state format");
        legacyRestored.key("ENTER");
        check(
                top(legacyRestored).definitelyEquals(RpnCalculator.parse("12.3")),
                "legacy state remains usable");
    }

    @Test
    void safeClearAndNoOpHistory() throws Exception {
        RpnCalculator safeClear = new RpnCalculator();
        keys(safeClear, "9", "ENTER", "7", "SMART_CLEAR", "SMART_CLEAR");
        check(
                safeClear.isEditing()
                        && safeClear.entry().isEmpty()
                        && top(safeClear).definitelyEquals(new UnifiedReal(9)),
                "repeated contextual clear cannot clear the stack");
        int emptyEdits = safeClear.undoCount();
        keys(safeClear, "CE", "CE", "BACK", "DELETE");
        check(safeClear.undoCount() == emptyEdits, "unchanged entry commands do not consume undo");
        safeClear.key("ENTER");
        safeClear.key("SMART_CLEAR");
        check(
                safeClear.isEmpty() && !safeClear.isEditing(),
                "contextual Clear clears stack only after leaving entry");
        safeClear.key("UNDO");
        int remainingUndo = safeClear.undoCount(), remainingRedo = safeClear.redoCount();
        safeClear.key("CE");
        check(
                safeClear.undoCount() == remainingUndo && safeClear.redoCount() == remainingRedo,
                "no-op clear preserves undo and redo");
        RpnCalculator noOps = new RpnCalculator();
        keys(noOps, "1", "ENTER", "CLEAR");
        int realHistory = noOps.undoCount();
        for (int i = 0; i < 150; i++) keys(noOps, "CLEAR", "CE", "BACK", "DELETE");
        check(noOps.undoCount() == realHistory, "no-op commands cannot evict real history");
        noOps.key("UNDO");
        check(top(noOps).definitelyOne(), "undo skips no-op commands");
        for (String[] example :
                new String[][] {
                    {"9", "1", "9"},
                    {"27", "3", "3"},
                    {"-8", "3", "-2"},
                    {"-32", "5", "-2"},
                    {"81", "4", "3"},
                    {"16", "-2", "0.25"},
                    {"-8", "-3", "-0.5"},
                    {"0", "3", "0"},
                    {"1", "999", "1"}
                }) {
            RpnCalculator rooted = new RpnCalculator();
            rooted.paste(example[0]);
            rooted.key("ENTER");
            rooted.paste(example[1]);
            rooted.key("root");
            check(
                    top(rooted).definitelyEquals(RpnCalculator.parse(example[2])),
                    "exact root " + example[0] + " degree " + example[1]);
            check(
                    top(RpnCalculator.load(rooted.save())).definitelyEquals(top(rooted)),
                    "root exactness survives restart");
        }
    }

    @Test
    void corruptSnapshots() throws Exception {
        RpnCalculator durable = new RpnCalculator();
        keys(durable, "π", "2", "√", "+");
        byte[] valid = durable.save();
        try {
            RpnCalculator.load(java.util.Arrays.copyOf(valid, valid.length - 1));
            throw new AssertionError("truncated state accepted");
        } catch (java.io.IOException expected) {
            check(true, "reject truncated state");
        }
        valid[0] = 0;
        try {
            RpnCalculator.load(valid);
            throw new AssertionError("unknown state version accepted");
        } catch (java.io.IOException expected) {
            check(true, "reject unknown saved state version");
        }
    }
}
