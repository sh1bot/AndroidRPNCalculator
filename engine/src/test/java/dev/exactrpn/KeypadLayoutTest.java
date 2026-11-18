// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.exactrpn.KeypadLayout.FunctionPlacement;
import dev.exactrpn.KeypadLayout.Regime;

import org.junit.jupiter.api.Test;

/**
 * The layout policy is a table of forms over a few design constants, so this suite tests
 * the constants, the properties the views rely on, and a handful of golden sizes — not a
 * pin per boundary. Retuning a constant should mean changing one number here, while the
 * property sweeps stand guard over everything else.
 */
final class KeypadLayoutTest {
    // The workspace's defaults at font scale 1: gap 12, minimum stack rectangle 300 by 208,
    // a key grid 4 columns by 5 primary rows, function keys spanning 2 units.
    private static final int GAP = 12, MIN_WIDTH = 300, MIN_HEIGHT = 208;
    private static final int COLUMNS = 4, ROWS = 5, FN_UNITS = 2;

    private KeypadLayout choose(int width, int height, boolean expanded) {
        return KeypadLayout.choose(
                width, height, expanded, false, GAP, MIN_WIDTH, MIN_HEIGHT, COLUMNS, ROWS);
    }

    private KeypadLayout chooseMirrored(int width, int height, boolean expanded) {
        return KeypadLayout.choose(
                width, height, expanded, true, GAP, MIN_WIDTH, MIN_HEIGHT, COLUMNS, ROWS);
    }

    // ---------- The table's coefficients, derived from the design constants ----------

    @Test
    void controlHeightIsHalfTheKeyUnit() {
        // Beside: the unit is the height over 5.5 rows (5 primary + the half control row).
        double besideUnit = 360 / (ROWS + .5);
        assertEquals((int) Math.round(besideUnit / 2), choose(740, 360, true).controlHeight);
        // Stacked: the unit comes from the band under the fixed boundary.
        int blockHeight = Math.max(1, 800 - 800 * 36 / 100 - GAP);
        double stackedUnit = blockHeight / (ROWS + .5);
        assertEquals((int) Math.round(stackedUnit / 2), choose(600, 800, true).controlHeight);
        // R5: the unit is width-derived, so the control height is half the width unit.
        assertEquals((int) Math.round(360.0 / COLUMNS / 2), choose(360, 920, true).controlHeight);
    }

    @Test
    void fixedBoundariesAreTheNamedShares() {
        // R2: the stack keeps 50% of the width, odd widths included. (At this height the
        // R1 boundary is 717, so these widths are all in R2.)
        for (int width : new int[] {716, 715, 700, 500}) {
            KeypadLayout plan = choose(width, 360, true);
            assertEquals(Regime.R2, plan.regime);
            assertEquals(width * 50 / 100, plan.stackWidth);
        }
        // R3 and R4: the stack keeps 36% of the height.
        for (int height : new int[] {800, 840, 901})
            assertEquals(height * 36 / 100, choose(500, height, true).stackHeight);
        assertEquals(840 * 36 / 100, choose(360, 840, true).stackHeight);
    }

    @Test
    void columnAndDigitsSpanTheirUnits() {
        double unit = 360 / (ROWS + .5);
        KeypadLayout r1 = choose(740, 360, true);
        assertEquals((int) Math.round(COLUMNS * unit), r1.primaryWidth);
        assertEquals((int) Math.round(FN_UNITS * unit), r1.functionWidth);
    }

    @Test
    void squeezedBandTakesItsShareOfTheBody() {
        // FN_UNITS of every FN_UNITS + ROWS units of the body below the control row: 2/7.
        KeypadLayout r2 = choose(716, 360, true);
        int besideControl = (int) Math.round(360 / (ROWS + .5) / 2);
        assertEquals((360 - besideControl) * FN_UNITS / (FN_UNITS + ROWS), r2.functionHeight);
        KeypadLayout r4 = choose(360, 840, true);
        int blockHeight = Math.max(1, 840 - 840 * 36 / 100 - GAP);
        int stackedControl = (int) Math.round(blockHeight / (ROWS + .5) / 2);
        assertEquals(
                (blockHeight - stackedControl) * FN_UNITS / (FN_UNITS + ROWS),
                r4.functionHeight);
    }

    @Test
    void r1BoundarySitsExactlyAtTheStackMinimum() {
        // The boundary width is the minimum plus two gaps plus the column and the digits.
        double unit = 360 / (ROWS + .5);
        int boundary = MIN_WIDTH + 2 * GAP + (int) Math.round(FN_UNITS * unit)
                + (int) Math.round(COLUMNS * unit);
        assertEquals(Regime.R2, choose(boundary - 1, 360, true).regime);
        KeypadLayout first = choose(boundary, 360, true);
        assertEquals(Regime.R1, first.regime);
        assertEquals(MIN_WIDTH, first.stackWidth);
    }

    @Test
    void r5BoundarySitsExactlyAtTheStackMinimumHeight() {
        // The boundary height is the minimum plus two gaps plus the band and the natural
        // keypad, both at the width unit.
        double widthUnit = 360.0 / COLUMNS;
        int boundary = MIN_HEIGHT + 2 * GAP + (int) Math.round(FN_UNITS * widthUnit)
                + (int) Math.round((ROWS + .5) * widthUnit);
        assertEquals(Regime.R4, choose(360, boundary - 1, true).regime);
        KeypadLayout first = choose(360, boundary, true);
        assertEquals(Regime.R5, first.regime);
        assertEquals(MIN_HEIGHT, first.stackHeight);
    }

    // ---------- Properties over the whole size grid ----------

    private static int rank(Regime regime) {
        return switch (regime) {
            case R1 -> 0;
            case R2 -> 1;
            case R3 -> 2;
            case R4 -> 3;
            case R5 -> 4;
        };
    }

    @Test
    void regimesAreMonotoneAlongRays() {
        // Widening the window never moves to a taller regime; lengthening it never moves
        // to a wider one. The regime regions are bands between the four boundary lines.
        for (int height = 240; height <= 1200; height += 40) {
            int previous = Integer.MAX_VALUE;
            for (int width = 200; width <= 1400; width += 2) {
                int rank = rank(choose(width, height, true).regime);
                assertTrue(rank <= previous, width + "x" + height);
                previous = rank;
            }
        }
        for (int width = 240; width <= 1200; width += 40) {
            int previous = Integer.MIN_VALUE;
            for (int height = 200; height <= 1400; height += 2) {
                int rank = rank(choose(width, height, true).regime);
                assertTrue(rank >= previous, width + "x" + height);
                previous = rank;
            }
        }
    }

    @Test
    void plansFillTheWindowExactly() {
        for (int width = 280; width <= 1100; width += 17)
            for (int height = 200; height <= 1000; height += 19)
                for (boolean expanded : new boolean[] {false, true}) {
                    KeypadLayout plan = choose(width, height, expanded);
                    assertTrue(plan.stackWidth > 0 && plan.stackHeight > 0);
                    assertTrue(plan.keypadWidth > 0 && plan.keypadHeight > 0);
                    assertTrue(plan.primaryWidth > 0 && plan.controlHeight > 0);
                    if (plan.besideStack)
                        assertEquals(width, plan.stackWidth + GAP + plan.keypadWidth);
                    else assertEquals(height, plan.stackHeight + GAP + plan.keypadHeight);
                }
    }

    @Test
    void fitPromisesHoldInsideEachRegime() {
        for (int width = 280; width <= 1100; width += 17)
            for (int height = 200; height <= 1000; height += 19)
                for (boolean expanded : new boolean[] {false, true}) {
                    KeypadLayout plan = choose(width, height, expanded);
                    switch (plan.regime) {
                        case R1 -> {
                            // The stack's minimum survives the cession, and the digits are
                            // exactly their natural width — width is never squeezed.
                            assertTrue(plan.expandedStackWidth >= MIN_WIDTH);
                            if (expanded) assertTrue(plan.stackWidth >= MIN_WIDTH);
                            double unit = height / (ROWS + .5);
                            assertEquals((int) Math.round(COLUMNS * unit), plan.primaryWidth);
                        }
                        case R3 -> {
                            // The digits keep at least their natural width at the band's
                            // unit; opening the column spends width, never squeezes them
                            // below it.
                            int blockHeight =
                                    Math.max(1, height - height * 36 / 100 - GAP);
                            double unit = blockHeight / (ROWS + .5);
                            assertTrue(
                                    plan.primaryWidth >= (int) Math.round(COLUMNS * unit),
                                    width + "x" + height);
                        }
                        case R5 -> {
                            // The stack keeps its minimum height even with the band open.
                            assertTrue(plan.stackHeight >= MIN_HEIGHT);
                            assertEquals(width, plan.primaryWidth);
                        }
                        default -> {}
                    }
                }
    }

    @Test
    void togglingThePanelChangesOnlyWhatTheRegimeAllows() {
        for (int width = 280; width <= 1100; width += 17)
            for (int height = 200; height <= 1000; height += 19) {
                KeypadLayout open = choose(width, height, true);
                KeypadLayout closed = choose(width, height, false);
                // The regime is chosen as if expanded, so it never flips; a closed panel
                // has no function extent, and the header's basis width is toggle-stable.
                assertEquals(open.regime, closed.regime, width + "x" + height);
                assertEquals(0, closed.functionWidth);
                assertEquals(0, closed.functionHeight);
                assertEquals(open.expandedStackWidth, closed.expandedStackWidth);
                switch (open.regime) {
                    case R1 -> {
                        // The boundary moves: the column and its gap come out of the stack
                        // and into the keypad; everything else is invariant.
                        assertEquals(open.primaryWidth, closed.primaryWidth);
                        assertEquals(open.controlHeight, closed.controlHeight);
                        assertEquals(open.keypadHeight, closed.keypadHeight);
                        assertEquals(
                                closed.stackWidth,
                                open.stackWidth + open.functionWidth + GAP);
                        assertEquals(
                                open.keypadWidth,
                                closed.keypadWidth + open.functionWidth + GAP);
                    }
                    case R2, R4 -> {
                        // The squeeze is internal: only the function extent changes.
                        assertEquals(open.stackWidth, closed.stackWidth);
                        assertEquals(open.stackHeight, closed.stackHeight);
                        assertEquals(open.keypadWidth, closed.keypadWidth);
                        assertEquals(open.keypadHeight, closed.keypadHeight);
                        assertEquals(open.primaryWidth, closed.primaryWidth);
                        assertEquals(open.controlHeight, closed.controlHeight);
                    }
                    case R3 -> {
                        // The boundary is fixed; the column spends keypad width only.
                        assertEquals(open.stackHeight, closed.stackHeight);
                        assertEquals(open.keypadHeight, closed.keypadHeight);
                        assertEquals(open.controlHeight, closed.controlHeight);
                        assertEquals(width, open.primaryWidth + open.functionWidth + GAP);
                        assertEquals(width, closed.primaryWidth);
                    }
                    case R5 -> {
                        // The boundary moves vertically: stack and keypad trade height
                        // across the one gap; widths and the control row are invariant.
                        assertEquals(open.keypadWidth, closed.keypadWidth);
                        assertEquals(open.primaryWidth, closed.primaryWidth);
                        assertEquals(open.controlHeight, closed.controlHeight);
                        assertEquals(
                                open.stackHeight + open.keypadHeight,
                                closed.stackHeight + closed.keypadHeight);
                    }
                }
            }
    }

    @Test
    void mirroredPlansAreExactMirrors() {
        int[][] sizes = {
            {740, 360}, {716, 360}, {810, 800}, {700, 840},
            {600, 800}, {500, 800}, {360, 840}, {360, 920}
        };
        for (int[] size : sizes) {
            int width = size[0], height = size[1];
            for (boolean expanded : new boolean[] {false, true}) {
                KeypadLayout plan = choose(width, height, expanded);
                KeypadLayout mirror = chooseMirrored(width, height, expanded);
                assertEquals(plan.regime, mirror.regime);
                assertEquals(plan.besideStack, mirror.besideStack);
                assertEquals(plan.stackWidth, mirror.stackWidth);
                assertEquals(plan.stackHeight, mirror.stackHeight);
                assertEquals(plan.expandedStackWidth, mirror.expandedStackWidth);
                assertEquals(plan.keypadWidth, mirror.keypadWidth);
                assertEquals(plan.keypadHeight, mirror.keypadHeight);
                assertEquals(plan.primaryWidth, mirror.primaryWidth);
                assertEquals(plan.controlHeight, mirror.controlHeight);
                assertEquals(plan.functionWidth, mirror.functionWidth);
                assertEquals(plan.functionHeight, mirror.functionHeight);
                assertEquals(plan.gap, mirror.gap);
                assertEquals(width - plan.stackX - plan.stackWidth, mirror.stackX);
                assertEquals(width - plan.keypadX - plan.keypadWidth, mirror.keypadX);
                assertEquals(
                        plan.keypadWidth - plan.primaryX - plan.primaryWidth, mirror.primaryX);
                assertEquals(
                        plan.keypadWidth - plan.functionX - plan.functionWidth, mirror.functionX);
                if (plan.functionPlacement == FunctionPlacement.LEFT)
                    assertEquals(FunctionPlacement.RIGHT, mirror.functionPlacement);
                else assertEquals(plan.functionPlacement, mirror.functionPlacement);
            }
        }
    }

    // ---------- Golden sizes: one per regime, plus the positions the views rely on ----------

    @Test
    void goldenRegimesAtTheStandardSizes() {
        assertEquals(Regime.R1, choose(740, 360, true).regime);
        assertEquals(Regime.R2, choose(810, 800, true).regime);
        assertEquals(Regime.R3, choose(700, 840, true).regime);
        assertEquals(Regime.R4, choose(360, 840, true).regime);
        assertEquals(Regime.R5, choose(360, 920, true).regime);
        // Exactly square is stacked: rotation always crosses the family boundary.
        assertFalse(choose(800, 800, true).besideStack);
        assertEquals(Regime.R3, choose(800, 800, true).regime);
    }

    @Test
    void goldenExtents() {
        KeypadLayout r1 = choose(740, 360, true);
        assertEquals(323, r1.stackWidth);
        assertEquals(466, choose(740, 360, false).stackWidth);
        assertEquals(405, choose(810, 800, true).stackWidth);
        assertEquals(302, choose(700, 840, true).stackHeight);
        assertEquals(221, choose(360, 920, true).stackHeight);
        assertEquals(495, choose(360, 920, false).keypadHeight);
    }

    @Test
    void goldenPositions() {
        KeypadLayout r1 = choose(740, 360, true);
        assertEquals(0, r1.stackX);
        assertEquals(335, r1.keypadX); // 740 - 405 of keypad
        assertEquals(0, r1.functionX);
        assertEquals(143, r1.primaryX); // 131 column + 12 gap
        KeypadLayout mirrored = chooseMirrored(740, 360, true);
        assertEquals(FunctionPlacement.RIGHT, mirrored.functionPlacement);
        assertEquals(0, mirrored.keypadX);
        assertEquals(417, mirrored.stackX); // 740 - 323 of stack
        assertEquals(0, mirrored.primaryX);
        assertEquals(274, mirrored.functionX); // 405 of keypad - 131 of column
    }
}
