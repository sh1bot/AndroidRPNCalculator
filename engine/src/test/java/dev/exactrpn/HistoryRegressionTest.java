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
        CalculatorTestSupport.paste(durable, "12.345");
        durable.select(2, 5);
        RpnCalculator restored = RpnCalculator.load(durable.save());
        assertTrue(restored.values().size() == 3, "restore full stack");
        assertTrue(
                restored.values().get(0).definitelyEquals(RpnCalculator.parse("56565642624.456")),
                "restore exact large decimal");
        assertEquals(
                UnifiedReal.PI.add(UnifiedReal.TWO.sqrt()).toStringTruncated(80),
                restored.values().get(1).toStringTruncated(80),
                "restore exact irrational expression");
        assertTrue(
                top(restored).multiply(new UnifiedReal(3)).definitelyOne(),
                "restore exact recurring fraction");
        assertTrue(
                restored.entry().equals("12.345")
                        && restored.isEditing()
                        && restored.selectionStart() == 2
                        && restored.selectionEnd() == 5,
                "restore editing and cursor selection");
        assertTrue(restored.angleUnit() == RpnCalculator.AngleUnit.DEG, "restore angle mode");
        restored.key("UNDO");
        assertTrue(
                restored.entry().equals("") && restored.values().size() == 3,
                "restore undo history");
        restored.key("CLEAR");
        RpnCalculator cleared = RpnCalculator.load(restored.save());
        assertTrue(
                cleared.isEmpty() && cleared.undoCount() > 0,
                "empty foreground state retains undo");
        cleared.key("UNDO");
        assertTrue(cleared.values().size() == 3, "undo accidental Clear after restoration");
        cleared.key("CLEAR");
        cleared.clearUndo();
        assertTrue(
                cleared.isEmpty() && cleared.undoCount() == 0,
                "empty background state discards undo");
        cleared.key("UNDO");
        assertTrue(cleared.isEmpty(), "discarded undo cannot resurrect cleared values");
        RpnCalculator rawSaved = new RpnCalculator();
        rawSaved.pasteText("1,234", new NumberText.Profile('.', ',', 3, 3));
        rawSaved.select(1, 2);
        RpnCalculator rawRestored = RpnCalculator.load(rawSaved.save());
        assertTrue(
                rawRestored.rawEntry()
                        && rawRestored.entryProblem().equals("Ambiguous")
                        && rawRestored.entry().equals("1,234"),
                "restore ambiguous clipboard entry");
        assertTrue(
                rawRestored.selectionStart() == 1 && rawRestored.selectionEnd() == 2,
                "restore raw cursor selection");
        RpnCalculator angleSaved = new RpnCalculator();
        angleSaved.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        number(angleSaved, "12.34");
        angleSaved.key("sin");
        angleSaved.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        RpnCalculator angleRestored = RpnCalculator.load(angleSaved.save());
        assertEquals(
                ValueFormatter.format(top(angleSaved), true, sci),
                ValueFormatter.format(top(angleRestored), true, sci),
                "restore trig expression with original angle unit");
        RpnCalculator bounded = new RpnCalculator();
        for (int i = 0; i < 150; i++) CalculatorTestSupport.paste(bounded, Integer.toString(i));
        assertTrue(
                bounded.undoCount() == 100 && RpnCalculator.load(bounded.save()).undoCount() == 100,
                "undo count is bounded across persistence");
        String longRaw = "字".repeat(4095);
        for (int i = 0; i < 110; i++)
            bounded.pasteText(longRaw + (i % 10), new NumberText.Profile('.', ',', 3, 3));
        byte[] boundedBytes = bounded.save();
        assertTrue(
                boundedBytes.length <= CalculationLimits.ARITHMETIC_BYTES
                        && bounded.undoCount() < 100,
                "saved undo history respects byte budget");
        assertEquals(
                longRaw + "9",
                RpnCalculator.load(boundedBytes).entry(),
                "undo eviction preserves current entry");
        for (String operation : new String[] {"±", "√", "1/x", "ln", "eˣ", "sin", "cos", "tan"}) {
            RpnCalculator expression = new RpnCalculator();
            keys(expression, "π", operation);
            RpnCalculator loaded = RpnCalculator.load(expression.save());
            assertEquals(
                    top(expression).toStringTruncated(60),
                    top(loaded).toStringTruncated(60),
                    "restore unary expression " + operation);
            loaded.key("UNDO");
            assertTrue(
                    top(loaded).definitelyEquals(UnifiedReal.PI),
                    "restore unary undo " + operation);
        }
        for (String operation : new String[] {"+", "−", "×", "÷", "xʸ"}) {
            RpnCalculator expression = new RpnCalculator();
            keys(expression, "π", "2", "√", operation);
            RpnCalculator loaded = RpnCalculator.load(expression.save());
            assertEquals(
                    top(expression).toStringTruncated(60),
                    top(loaded).toStringTruncated(60),
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
        assertTrue(editingHistory.undoCount() == 2, "commit drops intermediate entry edits");
        editingHistory.key("UNDO");
        assertTrue(
                editingHistory.entry().equals("123") && editingHistory.values().isEmpty(),
                "undo commit restores complete entry");
        editingHistory.key("UNDO");
        assertTrue(editingHistory.isEmpty(), "next undo skips intermediate spellings");
        editingHistory.key("REDO");
        assertEquals("123", editingHistory.entry(), "redo complete entry");
        editingHistory.key("REDO");
        assertTrue(
                top(editingHistory).definitelyEquals(new UnifiedReal(123))
                        && !editingHistory.isEditing(),
                "redo commit");
        RpnCalculator operationHistory = new RpnCalculator();
        number(operationHistory, "12");
        operationHistory.key("ENTER");
        number(operationHistory, "345");
        operationHistory.key("+");
        operationHistory.key("UNDO");
        assertTrue(
                operationHistory.entry().equals("345")
                        && top(operationHistory).definitelyEquals(new UnifiedReal(12)),
                "undo operator restores full entry");
        operationHistory.key("UNDO");
        assertTrue(
                operationHistory.entry().isEmpty()
                        && top(operationHistory).definitelyEquals(new UnifiedReal(12)),
                "operator commit drops intermediate edits");
        operationHistory.key("UNDO");
        assertTrue(
                operationHistory.entry().equals("12") && operationHistory.values().isEmpty(),
                "older calculation remains reachable");
        operationHistory.key("REDO");
        operationHistory.key("REDO");
        operationHistory.key("REDO");
        assertTrue(
                top(operationHistory).definitelyEquals(new UnifiedReal(357)),
                "redo full calculation sequence");
        operationHistory.key("CLEAR");
        operationHistory.key("UNDO");
        assertTrue(
                top(operationHistory).definitelyEquals(new UnifiedReal(357)),
                "undo Clear still works");
        operationHistory.key("REDO");
        assertTrue(operationHistory.isEmpty(), "redo Clear");
        operationHistory.key("UNDO");
        operationHistory.key("9");
        assertTrue(operationHistory.redoCount() == 0, "new edit discards redo branch");
        operationHistory.key("REDO");
        assertEquals("9", operationHistory.entry(), "discarded redo cannot replay stale Clear");
        RpnCalculator failedCommit = new RpnCalculator();
        number(failedCommit, "123");
        try {
            failedCommit.key("+");
            throw new AssertionError("underflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(
                failedCommit.undoCount() == 2,
                "failed operation commits entry and collapses edit history");
        failedCommit.key("UNDO");
        assertEquals(
                "123", failedCommit.entry(), "undo restores complete entry after failed operation");
        RpnCalculator failedRedo = new RpnCalculator();
        keys(failedRedo, "π", "UNDO");
        try {
            failedRedo.key("+");
            throw new AssertionError("underflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue(failedRedo.redoCount() == 1, "failed operation preserves redo");
        failedRedo.key("REDO");
        assertTrue(
                top(failedRedo).definitelyEquals(UnifiedReal.PI),
                "redo exact constant after failure");
        RpnCalculator redoSaved = new RpnCalculator();
        keys(redoSaved, "π", "2", "√", "+", "UNDO");
        RpnCalculator redoRestored = RpnCalculator.load(redoSaved.save());
        assertTrue(
                redoRestored.redoCount() == 1 && redoRestored.entry().isEmpty(),
                "restore redo history");
        redoRestored.key("REDO");
        assertEquals(
                UnifiedReal.PI.add(UnifiedReal.TWO.sqrt()).toStringTruncated(80),
                top(redoRestored).toStringTruncated(80),
                "redo exact irrational operation after restart");
        redoRestored.key("UNDO");
        RpnCalculator redoCopy = CalculatorTestSupport.copy(redoRestored);
        redoCopy.key("REDO");
        assertTrue(
                redoRestored.redoCount() == 1 && redoCopy.redoCount() == 0,
                "redo history isolated between candidates");
        redoRestored.clearUndo();
        assertTrue(
                redoRestored.undoCount() == 0 && redoRestored.redoCount() == 0,
                "clear history removes undo and redo");
        assertEquals(
                "REDO", KeyboardInput.command(53, 0, true, false, false), "Ctrl Y redo shortcut");
    }

    @Test
    void safeClearAndNoOpHistory() throws Exception {
        RpnCalculator safeClear = new RpnCalculator();
        keys(safeClear, "9", "ENTER", "7", "SMART_CLEAR", "SMART_CLEAR");
        assertTrue(
                safeClear.isEditing()
                        && safeClear.entry().isEmpty()
                        && top(safeClear).definitelyEquals(new UnifiedReal(9)),
                "repeated contextual clear cannot clear the stack");
        int emptyEdits = safeClear.undoCount();
        keys(safeClear, "CE", "CE", "BACK", "DELETE");
        assertTrue(
                safeClear.undoCount() == emptyEdits,
                "unchanged entry commands do not consume undo");
        safeClear.key("ENTER");
        safeClear.key("SMART_CLEAR");
        assertTrue(
                safeClear.isEmpty() && !safeClear.isEditing(),
                "contextual Clear clears stack only after leaving entry");
        safeClear.key("UNDO");
        int remainingUndo = safeClear.undoCount(), remainingRedo = safeClear.redoCount();
        safeClear.key("CE");
        assertTrue(
                safeClear.undoCount() == remainingUndo && safeClear.redoCount() == remainingRedo,
                "no-op clear preserves undo and redo");
        RpnCalculator noOps = new RpnCalculator();
        keys(noOps, "1", "ENTER", "CLEAR");
        int realHistory = noOps.undoCount();
        for (int i = 0; i < 150; i++) keys(noOps, "CLEAR", "CE", "BACK", "DELETE");
        assertTrue(noOps.undoCount() == realHistory, "no-op commands cannot evict real history");
        noOps.key("UNDO");
        assertTrue(top(noOps).definitelyOne(), "undo skips no-op commands");
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
            CalculatorTestSupport.paste(rooted, example[0]);
            rooted.key("ENTER");
            CalculatorTestSupport.paste(rooted, example[1]);
            rooted.key("root");
            assertTrue(
                    top(rooted).definitelyEquals(RpnCalculator.parse(example[2])),
                    "exact root " + example[0] + " degree " + example[1]);
            assertTrue(
                    top(RpnCalculator.load(rooted.save())).definitelyEquals(top(rooted)),
                    "root exactness survives restart");
        }
    }

    @Test
    void corruptSnapshots() throws Exception {
        RpnCalculator durable = new RpnCalculator();
        keys(durable, "π", "2", "√", "+");
        byte[] valid = durable.save();
        assertThrows(
                java.io.IOException.class,
                () -> RpnCalculator.load(java.util.Arrays.copyOf(valid, valid.length - 1)));
        for (int version : new int[] {0, 0x31, 0x32, 0x33, 0x34}) {
            byte[] unsupported = valid.clone();
            unsupported[3] = (byte) version;
            assertThrows(java.io.IOException.class, () -> RpnCalculator.load(unsupported));
        }
    }
}
