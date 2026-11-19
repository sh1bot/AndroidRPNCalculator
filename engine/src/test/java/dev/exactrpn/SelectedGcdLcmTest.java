// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SelectedGcdLcmTest {
    private static TestCalculator calculatorWith(String... values) throws Exception {
        TestCalculator c = new TestCalculator();
        for (String text : values) {
            CalculatorTestSupport.paste(c, text);
            c.key("ENTER");
        }
        return c;
    }

    private static String top(TestCalculator c) {
        return c.values().get(c.values().size() - 1).bigIntegerValue().toString();
    }

    @Test
    void gcdFoldsTheSelectionAndPreservesOriginals() throws Exception {
        TestCalculator c = calculatorWith("12", "18", "30");
        c.gcdRows(0, 1, 2);
        assertEquals(4, c.values().size());
        assertEquals("6", top(c));
        assertEquals("12", c.values().get(0).bigIntegerValue().toString());
    }

    @Test
    void lcmFoldsTheSelectionThroughTheCommand() throws Exception {
        TestCalculator c = calculatorWith("4", "6", "10");
        Command command = Command.rows(Command.Kind.LCM_ROWS, 0, 1, 2);
        c.apply(command, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals(4, c.values().size());
        assertEquals("60", top(c));
    }

    @Test
    void signsAndZerosFollowTheBinaryOperations() throws Exception {
        TestCalculator c = calculatorWith("-12", "18");
        c.gcdRows(0, 1);
        assertEquals("6", top(c));
        c = calculatorWith("0", "7");
        c.lcmRows(0, 1);
        assertEquals("0", top(c));
        c = calculatorWith("0", "0");
        c.gcdRows(0, 1);
        assertEquals("0", top(c));
    }

    @Test
    void nonIntegerRowsAreRejectedAtomically() throws Exception {
        TestCalculator c = calculatorWith("12", "1.5");
        byte[] before = c.save();
        IllegalArgumentException gcd =
                assertThrows(IllegalArgumentException.class, () -> c.gcdRows(0, 1));
        assertEquals(CalculationError.Code.GCD_INTEGER, ((CalculationError) gcd).code());
        assertArrayEquals(before, c.save());
        IllegalArgumentException lcm =
                assertThrows(IllegalArgumentException.class, () -> c.lcmRows(0, 1));
        assertEquals(CalculationError.Code.LCM_INTEGER, ((CalculationError) lcm).code());
        assertArrayEquals(before, c.save());
        // A one-row selection never reaches the fold, so it is checked too.
        assertThrows(IllegalArgumentException.class, () -> c.gcdRows(1));
        assertArrayEquals(before, c.save());
        c.gcdRows();
        assertArrayEquals(before, c.save());
    }
}
