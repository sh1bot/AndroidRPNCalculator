// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ArithmeticEdgeCasesTest {
    @Test
    void zeroRaisedToNegativePowersAndRootsReportsDivisionByZero() {
        for (Operation operation : new Operation[] {Operation.POWER, Operation.ROOT}) {
            for (UnifiedReal exponent : new UnifiedReal[] {
                new UnifiedReal(-3), new UnifiedReal(-1), RpnCalculator.parse("-0.5"),
                UnifiedReal.PI.negate()
            }) {
                ArithmeticException error = assertThrows(
                        ArithmeticException.class,
                        () -> operation.evaluate(
                                UnifiedReal.ZERO, exponent, RpnCalculator.AngleUnit.RAD,
                                RadixMode.DECIMAL));
                assertEquals(CalculationError.Code.DIVIDE_BY_ZERO, CalculationError.code(error));
            }
        }
    }

    @Test
    void repeatedAbsoluteValuePreservesSnapshotAndUndoHistory() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("π");
        byte[] before = calculator.save();
        int undoCount = calculator.undoCount();
        for (int i = 0; i < 2_000; i++) calculator.key("|x|");
        assertEquals(undoCount, calculator.undoCount());
        assertArrayEquals(before, calculator.save());
        assertArrayEquals(before, RpnCalculator.load(calculator.save()).save());
    }

    @Test
    void irrationalValuesOnEitherSideOfMidpointRoundDifferently() {
        for (int sign : new int[] {1, -1}) {
            checkSquareRootRounding("1.00000000003000000000022498", "1.00000000001", sign);
            checkSquareRootRounding("1.00000000003000000000022502", "1.00000000002", sign);
        }
    }

    private static void checkSquareRootRounding(String input, String expected, int sign) {
        UnifiedReal root = RpnCalculator.parse(input).sqrt();
        NumericValue result = NumericEvaluator.read(sign < 0 ? root.negate() : root,
                NumericRequest.DEFAULT);
        assertEquals(new BigDecimal(expected).multiply(BigDecimal.valueOf(sign)),
                new BigDecimal(result.num, result.scale));
        assertFalse(result.exact);
    }

    @Test
    void invalidDigitDoesNotSuppressEnterDuplication() {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("π");
        EntryEditor editor = new EntryEditor();
        editor.key("A");
        assertFalse(editor.isEditing());
        assertEquals("", editor.entry());
        calculator.useEntryEditor(editor);
        calculator.key("ENTER");
        assertEquals(2, calculator.values().size());
    }

    @Test
    void leadingZerosDoNotCountTowardExponentLimit() {
        assertTrue(RpnCalculator.parse("1E0000000001").definitelyEquals(new UnifiedReal(10)));
        assertTrue(RpnCalculator.parse("1E-0000000001")
                .definitelyEquals(RpnCalculator.parse("0.1")));
    }

    @Test
    void unknownOperationPreservesThePendingEntry() {
        RpnCalculator calculator = new RpnCalculator();
        EntryEditor editor = new EntryEditor();
        editor.key("7");
        calculator.useEntryEditor(editor);

        assertThrows(IllegalArgumentException.class, () -> calculator.key("UNKNOWN_KEY"));
        assertTrue(calculator.values().isEmpty());
        assertEquals(0, calculator.undoCount());
        assertEquals("7", calculator.detachEntryEditor().entry());
    }
}
