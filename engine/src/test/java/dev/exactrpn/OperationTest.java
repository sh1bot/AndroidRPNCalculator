// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class OperationTest {
    @ParameterizedTest
    @EnumSource(Operation.class)
    void liveAndSavedOperationsAgree(Operation op) {
        UnifiedReal a =
                op == Operation.ASIN || op == Operation.ACOS
                        ? RpnCalculator.parse("0.5")
                        : UnifiedReal.TWO;
        UnifiedReal b = new UnifiedReal(3);
        for (RpnCalculator.AngleUnit unit : RpnCalculator.AngleUnit.values()) {
            UnifiedReal live = op.evaluate(a, b, unit);
            UnifiedReal saved = SavedValue.operation(op, unit, null, null).evaluate(a, b);
            assertEquals(
                    TestValueFormatter.clipboard(live, ValueFormatter.Notation.SCIENTIFIC),
                    TestValueFormatter.clipboard(saved, ValueFormatter.Notation.SCIENTIFIC),
                    op + " " + unit);
        }
    }

    @Test
    void replayValidatesFactorial() {
        SavedValue factorial =
                SavedValue.operation(
                        Operation.FACTORIAL, RpnCalculator.AngleUnit.RAD, null, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> factorial.evaluate(UnifiedReal.MINUS_ONE, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> factorial.evaluate(RpnCalculator.parse("0.5"), null));
        assertThrows(IllegalArgumentException.class, () -> factorial.evaluate(null, null));
    }

    @Test
    void typedErrorKeepsItsArgument() {
        TestCalculator calculator = new TestCalculator();
        CalculationError error =
                (CalculationError)
                        assertThrows(IllegalArgumentException.class, () -> calculator.key("+"));
        assertEquals(CalculationError.Code.NEED_VALUES, error.code());
        assertEquals(2, error.argument());
    }
}
