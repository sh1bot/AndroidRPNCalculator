// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class HistoryReferencesTest {
    private int retainedValues(RpnCalculator calculator) throws Exception {
        var field = RpnCalculator.class.getDeclaredField("savedValues");
        field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(calculator)).size();
    }

    @Test
    void evictionBranchesAndClearReleaseOnlyUnreachableRoots() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        CalculatorTestSupport.paste(calculator, "2");
        calculator.key("ENTER");
        for (int i = 0; i < 130; i++) {
            CalculatorTestSupport.paste(calculator, "1");
            calculator.key("+");
        }
        assertTrue(retainedValues(calculator) <= 201);
        assertEquals(
                "132",
                RpnCalculator.load(calculator.save()).values().get(0).bigIntegerValue().toString());
        calculator.key("UNDO");
        calculator.key("UNDO");
        CalculatorTestSupport.paste(calculator, "7");
        calculator.key("+");
        calculator.clearUndo();
        assertEquals(1, retainedValues(calculator));
        assertEquals(
                "138",
                RpnCalculator.load(calculator.save()).values().get(0).bigIntegerValue().toString());
        calculator.key("DROP");
        calculator.clearUndo();
        assertEquals(0, retainedValues(calculator));
    }

    @Test
    void duplicateAliasesKeepTheirSharedExpressionUntilTheLastOwnerDisappears() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        CalculatorTestSupport.paste(calculator, "17");
        calculator.key("ENTER");
        for (int i = 0; i < 130; i++) calculator.key("ENTER");
        calculator.clearUndo();
        calculator.key("DROP");
        calculator.clearUndo();
        assertEquals(1, retainedValues(calculator));
        assertEquals(130, RpnCalculator.load(calculator.save()).values().size());
        calculator.key("CLEAR");
        calculator.clearUndo();
        assertEquals(0, retainedValues(calculator));
    }
}
