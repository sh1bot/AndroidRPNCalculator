// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class KeypadLayoutTest {
    private KeypadLayout choose(int width, int height, boolean expanded) {
        return KeypadLayout.choose(width, height, expanded, 12, 48, 48, 200, 112);
    }

    @Test
    void portraitKeepsTheKeypadBelowAndCompressesItsRows() {
        KeypadLayout closed = choose(392, 700, false), open = choose(392, 700, true);
        assertFalse(open.besideStack);
        assertFalse(open.functionsBesideDigits);
        assertEquals(closed.keypadHeight, open.keypadHeight);
        assertEquals(392, open.stackWidth);
        assertTrue(open.stackHeight >= 112);
    }

    @Test
    void ordinaryPhoneLandscapeReturnsFunctionWidthToStack() {
        KeypadLayout closed = choose(760, 280, false), open = choose(760, 280, true);
        assertTrue(open.besideStack);
        assertTrue(open.functionsBesideDigits);
        assertEquals(closed.primaryWidth, open.primaryWidth);
        assertEquals(closed.stackWidth - open.stackWidth, open.keypadWidth - closed.keypadWidth);
        assertTrue(open.stackWidth >= 200);
        assertTrue(open.keypadWidth - open.primaryWidth - 12 >= 5 * 48);
    }

    @Test
    void narrowerLandscapeCompressesRowsInsteadOfStarvingTheStack() {
        KeypadLayout plan = choose(600, 300, true);
        assertTrue(plan.besideStack);
        assertFalse(plan.functionsBesideDigits);
        assertTrue(plan.stackWidth >= 200);
        assertEquals(plan.primaryWidth, plan.keypadWidth);
    }

    @Test
    void tallWideWindowsStillUseVerticalLayout() {
        assertFalse(choose(600, 800, true).besideStack);
    }

    @Test
    void plansStayInsideTheWindowAtThresholds() {
        for (int width = 280; width <= 1000; width += 17)
            for (int height = 200; height <= 900; height += 23)
                for (boolean expanded : new boolean[] {false, true}) {
                    KeypadLayout plan = choose(width, height, expanded);
                    assertTrue(plan.stackWidth > 0 && plan.stackHeight > 0);
                    assertTrue(plan.keypadWidth > 0 && plan.keypadHeight > 0);
                    if (plan.besideStack)
                        assertEquals(width, plan.stackWidth + 12 + plan.keypadWidth);
                    else assertEquals(height, plan.stackHeight + plan.keypadHeight);
                }
    }

    @Test
    void fiveColumnRadixKeypadsUseTheirActualRowCount() {
        KeypadLayout dozenal = KeypadLayout.choose(392, 700, false, 12, 48, 48, 200, 112, 5, 5),
                hex = KeypadLayout.choose(392, 700, false, 12, 48, 48, 200, 112, 5, 6);
        assertFalse(dozenal.besideStack);
        assertFalse(hex.besideStack);
        assertTrue(hex.keypadHeight > dozenal.keypadHeight);
        assertTrue(hex.stackHeight < dozenal.stackHeight);
    }
}
