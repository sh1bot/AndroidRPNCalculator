// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.exactrpn.KeypadLayout.FunctionPlacement;
import dev.exactrpn.KeypadLayout.Regime;

import org.junit.jupiter.api.Test;

final class KeypadLayoutTest {
    // The workspace's defaults at font scale 1: gap 12, minimum stack rectangle 300 by 208.
    private KeypadLayout choose(int width, int height, boolean expanded) {
        return KeypadLayout.choose(width, height, expanded, false, 12, 300, 208, 4, 5);
    }

    private KeypadLayout chooseMirrored(int width, int height, boolean expanded) {
        return KeypadLayout.choose(width, height, expanded, true, 12, 300, 208, 4, 5);
    }

    @Test
    void r1CedesTheColumnAndReturnsItOnClose() {
        KeypadLayout open = choose(740, 360, true), closed = choose(740, 360, false);
        assertEquals(Regime.R1, open.regime);
        assertEquals(Regime.R1, closed.regime);
        assertTrue(open.besideStack);
        assertEquals(FunctionPlacement.LEFT, open.functionPlacement);
        // Unit is 360 / 5.5; the column (2 units, 131) and the natural keypad (4 units, 262)
        // come out of the stack across two gaps.
        assertEquals(262, open.primaryWidth);
        assertEquals(131, open.functionWidth);
        assertEquals(323, open.stackWidth);
        assertTrue(open.stackWidth >= 300);
        assertEquals(466, closed.stackWidth);
        assertEquals(open.primaryWidth, closed.primaryWidth);
        assertEquals(0, closed.functionWidth);
    }

    @Test
    void r1BoundaryIsWhereCedingBreaksTheStackMinimum() {
        assertEquals(Regime.R1, choose(717, 360, true).regime);
        assertEquals(300, choose(717, 360, true).stackWidth);
        assertEquals(Regime.R2, choose(716, 360, true).regime);
    }

    @Test
    void r2KeepsTheFixedHalfBoundaryAndSqueezes() {
        KeypadLayout open = choose(716, 360, true), closed = choose(716, 360, false);
        assertEquals(Regime.R2, open.regime);
        assertTrue(open.besideStack);
        assertEquals(FunctionPlacement.SQUEEZE, open.functionPlacement);
        assertEquals(358, open.stackWidth);
        assertEquals(open.stackWidth, closed.stackWidth);
        assertTrue(open.functionHeight > 0);
        assertEquals(0, closed.functionHeight);
        assertEquals(405, choose(810, 800, true).stackWidth);
        assertEquals(Regime.R2, choose(810, 800, true).regime);
    }

    @Test
    void exactlySquareIsStacked() {
        KeypadLayout plan = choose(800, 800, true);
        assertFalse(plan.besideStack);
        assertEquals(Regime.R3, plan.regime);
    }

    @Test
    void r3PutsFunctionsLeftOfTheKeypadUnderTheFixedBoundary() {
        KeypadLayout fold = choose(700, 840, true);
        assertEquals(Regime.R3, fold.regime);
        assertEquals(FunctionPlacement.LEFT, fold.functionPlacement);
        assertEquals(302, fold.stackHeight);
        // The column (2 units of the 526-high band, 191) and one gap come out of the width;
        // the leftover widens the digits beyond their natural 4 units (382).
        assertEquals(191, fold.functionWidth);
        assertEquals(497, fold.primaryWidth);
        assertTrue(fold.primaryWidth > 382);

        KeypadLayout plan = choose(600, 800, true);
        assertEquals(Regime.R3, plan.regime);
        assertEquals(288, plan.stackHeight);
        assertEquals(182, plan.functionWidth);
        assertEquals(406, plan.primaryWidth);
        KeypadLayout closed = choose(600, 800, false);
        assertEquals(Regime.R3, closed.regime);
        assertEquals(600, closed.primaryWidth);
        assertEquals(0, closed.functionWidth);
    }

    @Test
    void r4SqueezesWhenFunctionsLeftDoesNotFit() {
        assertEquals(Regime.R4, choose(500, 800, true).regime);
        KeypadLayout phone = choose(360, 840, true);
        assertEquals(Regime.R4, phone.regime);
        assertEquals(FunctionPlacement.SQUEEZE, phone.functionPlacement);
        assertEquals(302, phone.stackHeight);
        assertTrue(phone.functionHeight > 0);
        assertEquals(0, choose(360, 840, false).functionHeight);
        assertEquals(phone.keypadHeight, choose(360, 840, false).keypadHeight);
    }

    @Test
    void r5OnlyWhenTheExpandedBandLeavesTheStackItsMinimum() {
        assertEquals(Regime.R4, choose(360, 900, true).regime);
        KeypadLayout boundary = choose(360, 907, true);
        assertEquals(Regime.R5, boundary.regime);
        assertEquals(FunctionPlacement.ABOVE, boundary.functionPlacement);
        assertEquals(208, boundary.stackHeight);
        assertEquals(180, boundary.functionHeight);
        assertEquals(687, boundary.keypadHeight);

        KeypadLayout open = choose(360, 920, true), closed = choose(360, 920, false);
        assertEquals(Regime.R5, open.regime);
        assertEquals(Regime.R5, closed.regime);
        assertEquals(221, open.stackHeight);
        // Closed, the band and its gap return to the stack: 920 - 12 - 495 natural keypad.
        assertEquals(495, closed.keypadHeight);
        assertEquals(413, closed.stackHeight);
        assertEquals(45, open.controlHeight);
    }

    @Test
    void unmirroredPositionsAreTheOnesTheViewsUsedToDerive() {
        KeypadLayout r1 = choose(740, 360, true);
        assertEquals(0, r1.stackX);
        assertEquals(335, r1.keypadX); // 740 - 405 of keypad
        assertEquals(0, r1.functionX);
        assertEquals(143, r1.primaryX); // 131 column + 12 gap
        KeypadLayout r3 = choose(600, 800, true);
        assertEquals(0, r3.stackX);
        assertEquals(0, r3.keypadX);
        assertEquals(0, r3.functionX);
        assertEquals(194, r3.primaryX); // 182 column + 12 gap
        KeypadLayout r5 = choose(360, 920, true);
        assertEquals(0, r5.primaryX);
        assertEquals(0, r5.functionX);
    }

    @Test
    void mirroredPlansAreExactMirrorsInEveryRegime() {
        int[][] sizes = {{740, 360}, {716, 360}, {600, 800}, {360, 840}, {360, 920}};
        Regime[] regimes = {Regime.R1, Regime.R2, Regime.R3, Regime.R4, Regime.R5};
        for (int i = 0; i < sizes.length; i++) {
            int width = sizes[i][0], height = sizes[i][1];
            for (boolean expanded : new boolean[] {false, true}) {
                KeypadLayout plan = choose(width, height, expanded);
                KeypadLayout mirror = chooseMirrored(width, height, expanded);
                assertEquals(regimes[i], plan.regime, width + "x" + height);
                assertEquals(plan.regime, mirror.regime);
                assertEquals(plan.besideStack, mirror.besideStack);
                // Sizes and vertical geometry are unchanged.
                assertEquals(plan.stackWidth, mirror.stackWidth);
                assertEquals(plan.stackHeight, mirror.stackHeight);
                assertEquals(plan.keypadWidth, mirror.keypadWidth);
                assertEquals(plan.keypadHeight, mirror.keypadHeight);
                assertEquals(plan.primaryWidth, mirror.primaryWidth);
                assertEquals(plan.controlHeight, mirror.controlHeight);
                assertEquals(plan.functionWidth, mirror.functionWidth);
                assertEquals(plan.functionHeight, mirror.functionHeight);
                assertEquals(plan.gap, mirror.gap);
                // Every horizontal position is the exact mirror.
                assertEquals(width - plan.stackX - plan.stackWidth, mirror.stackX);
                assertEquals(width - plan.keypadX - plan.keypadWidth, mirror.keypadX);
                assertEquals(
                        plan.keypadWidth - plan.primaryX - plan.primaryWidth, mirror.primaryX);
                assertEquals(
                        plan.keypadWidth - plan.functionX - plan.functionWidth, mirror.functionX);
                // A LEFT column becomes a RIGHT column; other placements are unchanged.
                if (plan.functionPlacement == FunctionPlacement.LEFT)
                    assertEquals(FunctionPlacement.RIGHT, mirror.functionPlacement);
                else assertEquals(plan.functionPlacement, mirror.functionPlacement);
            }
        }
    }

    @Test
    void mirroredR1SwapsTheStackAndKeypadSides() {
        KeypadLayout mirror = chooseMirrored(740, 360, true);
        assertEquals(FunctionPlacement.RIGHT, mirror.functionPlacement);
        assertEquals(0, mirror.keypadX);
        assertEquals(417, mirror.stackX); // 740 - 323 of stack
        assertEquals(0, mirror.primaryX);
        assertEquals(274, mirror.functionX); // 405 of keypad - 131 of column
    }

    @Test
    void togglingThePanelNeverFlipsTheRegime() {
        int[][] sizes = {
            {740, 360}, {716, 360}, {810, 800}, {800, 800},
            {700, 840}, {600, 800}, {500, 800}, {360, 840},
            {360, 900}, {360, 920}, {392, 700}, {280, 900}
        };
        for (int[] size : sizes)
            assertEquals(
                    choose(size[0], size[1], true).regime,
                    choose(size[0], size[1], false).regime,
                    size[0] + "x" + size[1]);
    }

    @Test
    void plansStayInsideTheWindowAtThresholds() {
        for (int width = 280; width <= 1000; width += 17)
            for (int height = 200; height <= 900; height += 23)
                for (boolean expanded : new boolean[] {false, true}) {
                    KeypadLayout plan = choose(width, height, expanded);
                    assertTrue(plan.stackWidth > 0 && plan.stackHeight > 0);
                    assertTrue(plan.keypadWidth > 0 && plan.keypadHeight > 0);
                    assertTrue(plan.primaryWidth > 0 && plan.controlHeight > 0);
                    if (plan.besideStack)
                        assertEquals(width, plan.stackWidth + 12 + plan.keypadWidth);
                    else assertEquals(height, plan.stackHeight + 12 + plan.keypadHeight);
                }
    }
}
