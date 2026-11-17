// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class SelectedSdTest {
    private static TestCalculator seeded(String... values) {
        TestCalculator c = new TestCalculator();
        for (String text : values) {
            CalculatorTestSupport.paste(c, text);
            c.key("ENTER");
        }
        return c;
    }

    @Test
    void populationSdOfClassicDatasetIsExactlyTwo() throws Exception {
        TestCalculator c = seeded("2", "4", "4", "4", "5", "5", "7", "9");
        c.meanRows(0, 1, 2, 3, 4, 5, 6, 7);
        assertTrue(c.values().get(8).definitelyEquals(RpnCalculator.parse("5")));
        c.sdRows(0, 1, 2, 3, 4, 5, 6, 7);
        assertEquals(10, c.values().size());
        assertTrue(c.values().get(9).definitelyEquals(RpnCalculator.parse("2")));
        // Originals are untouched and the action is a single undo step.
        assertEquals("2", c.values().get(0).bigIntegerValue().toString());
        assertEquals("9", c.values().get(7).bigIntegerValue().toString());
        c = TestCalculator.load(c.save());
        c.arithmeticKey("UNDO");
        assertEquals(9, c.values().size());
        c.arithmeticKey("REDO");
        assertTrue(c.values().get(9).definitelyEquals(RpnCalculator.parse("2")));
    }

    @Test
    void sdOfSingleValueIsExactlyZero() throws Exception {
        TestCalculator c = seeded("7");
        c.sdRows(0);
        assertEquals(2, c.values().size());
        assertTrue(c.values().get(1).definitelyZero());
    }

    @Test
    void irrationalSdRetainsExactExpressionAcrossRestore() throws Exception {
        TestCalculator c = new TestCalculator();
        CalculatorTestSupport.paste(c, "0");
        c.key("ENTER");
        c.key("π");
        c.sdRows(0, 1);
        // The SD of {0, π} is π/2. Its final square root passes through the
        // constructive reals, so definite equality with a separately built
        // π/2 is unprovable; compare at clipboard precision instead.
        String live = clipboard(c.values().get(2));
        assertTrue(live.startsWith("1.5707963267948966"), live);
        TestCalculator restored = TestCalculator.load(c.save());
        assertEquals(live, clipboard(restored.values().get(2)));
        restored.arithmeticKey("UNDO");
        assertEquals(2, restored.values().size());
        restored.arithmeticKey("REDO");
        assertEquals(live, clipboard(restored.values().get(2)));
    }

    private static String clipboard(UnifiedReal value) {
        return TestValueFormatter.clipboard(
                value, ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL);
    }

    @Test
    void invalidRowsAreAtomicAndEmptySelectionIsANoop() throws Exception {
        TestCalculator c = seeded("3");
        byte[] before = c.save();
        assertThrows(IllegalArgumentException.class, () -> c.sdRows(0, 1));
        assertArrayEquals(before, c.save());
        assertThrows(IllegalArgumentException.class, () -> c.sdRows(-1, 0));
        assertArrayEquals(before, c.save());
        c.sdRows();
        assertArrayEquals(before, c.save());
    }
}
