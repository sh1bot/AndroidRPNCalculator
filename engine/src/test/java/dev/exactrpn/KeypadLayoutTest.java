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

    private static void assertSameGeometry(KeypadLayout first, KeypadLayout second) {
        assertEquals(first.stackWidth, second.stackWidth);
        assertEquals(first.stackHeight, second.stackHeight);
        assertEquals(first.keypadWidth, second.keypadWidth);
        assertEquals(first.keypadHeight, second.keypadHeight);
        assertEquals(first.stackX, second.stackX);
        assertEquals(first.keypadX, second.keypadX);
    }

    // ---------- The table's coefficients, derived from the design constants ----------

    @Test
    void keypadExtentsFollowTheKeyUnit() {
        // Beside: the unit is the height over 5.5 rows (5 primary + the half control row),
        // and a closed keypad is exactly the digits' natural width at that unit.
        double besideUnit = 360 / (ROWS + .5);
        KeypadLayout besideClosed = choose(740, 360, false);
        assertEquals(360, besideClosed.keypadHeight);
        assertEquals((int) Math.round(COLUMNS * besideUnit), besideClosed.keypadWidth);
        // Stacked: the keypad takes the whole band under the fixed boundary.
        int blockHeight = Math.max(1, 800 - 800 * 36 / 100 - GAP);
        assertEquals(blockHeight, choose(600, 800, true).keypadHeight);
        // R5: the unit is width-derived; the closed keypad is the natural height at that
        // unit, and opening it adds the FN_UNITS band plus the gap.
        double widthUnit = 360.0 / COLUMNS;
        assertEquals(
                (int) Math.round((ROWS + .5) * widthUnit), choose(360, 920, false).keypadHeight);
        assertEquals(
                (int) Math.round(FN_UNITS * widthUnit) + GAP,
                choose(360, 920, true).keypadHeight - choose(360, 920, false).keypadHeight);
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
        // In R1 the keypad's width decomposes into the digits and, when open, the column
        // plus its gap — each spanning its units of the beside unit.
        double unit = 360 / (ROWS + .5);
        KeypadLayout closed = choose(740, 360, false);
        KeypadLayout open = choose(740, 360, true);
        assertEquals((int) Math.round(COLUMNS * unit), closed.keypadWidth);
        assertEquals(
                (int) Math.round(FN_UNITS * unit), open.keypadWidth - closed.keypadWidth - GAP);
    }

    @Test
    void squeezeRegimesIgnoreThePanelState() {
        // In R2 and R4 the band squeezes inside the keypad, so the plan the workspace
        // applies is exactly the same open and closed; only the keypad's internal
        // subdivision follows the panel state.
        for (int[] size : new int[][] {{716, 360}, {360, 840}}) {
            KeypadLayout open = choose(size[0], size[1], true);
            KeypadLayout closed = choose(size[0], size[1], false);
            assertTrue(
                    open.regime == Regime.R2 || open.regime == Regime.R4,
                    size[0] + "x" + size[1]);
            assertEquals(open.regime, closed.regime);
            assertSameGeometry(open, closed);
        }
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
                            // exactly their natural width — width is never squeezed: the
                            // closed keypad is the digits alone at the beside unit.
                            assertTrue(plan.expandedStackWidth >= MIN_WIDTH);
                            if (expanded) assertTrue(plan.stackWidth >= MIN_WIDTH);
                            double unit = height / (ROWS + .5);
                            assertEquals(
                                    (int) Math.round(COLUMNS * unit),
                                    choose(width, height, false).keypadWidth);
                        }
                        case R3 -> {
                            // The boundary is the fixed share, and the keypad takes the
                            // whole band under it.
                            int blockHeight =
                                    Math.max(1, height - height * 36 / 100 - GAP);
                            assertEquals(height * 36 / 100, plan.stackHeight);
                            assertEquals(blockHeight, plan.keypadHeight);
                        }
                        case R5 -> {
                            // The stack keeps its minimum height even with the band open,
                            // and the keypad spans the full width.
                            assertTrue(plan.stackHeight >= MIN_HEIGHT);
                            assertEquals(width, plan.keypadWidth);
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
                // The regime is chosen as if expanded, so it never flips, and the
                // header's basis width is toggle-stable.
                assertEquals(open.regime, closed.regime, width + "x" + height);
                assertEquals(open.expandedStackWidth, closed.expandedStackWidth);
                switch (open.regime) {
                    case R1 -> {
                        // The boundary moves: the column and its gap come out of the stack
                        // and into the keypad; the cession is conserved and everything
                        // else is invariant.
                        assertEquals(open.keypadHeight, closed.keypadHeight);
                        assertEquals(
                                closed.stackWidth - open.stackWidth,
                                open.keypadWidth - closed.keypadWidth);
                        assertTrue(open.keypadWidth > closed.keypadWidth);
                    }
                    case R2, R4 -> {
                        // The squeeze is internal to the keypad: the plan is unchanged.
                        assertSameGeometry(open, closed);
                    }
                    case R3 -> {
                        // The boundary is fixed; the column spends keypad width only, so
                        // every extent the plan carries is invariant.
                        assertSameGeometry(open, closed);
                    }
                    case R5 -> {
                        // The boundary moves vertically: stack and keypad trade height
                        // across the one gap; the widths are invariant.
                        assertEquals(open.keypadWidth, closed.keypadWidth);
                        assertEquals(open.stackWidth, closed.stackWidth);
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
                assertEquals(plan.gap, mirror.gap);
                assertEquals(width - plan.stackX - plan.stackWidth, mirror.stackX);
                assertEquals(width - plan.keypadX - plan.keypadWidth, mirror.keypadX);
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
        KeypadLayout mirrored = chooseMirrored(740, 360, true);
        assertEquals(FunctionPlacement.RIGHT, mirrored.functionPlacement);
        assertEquals(0, mirrored.keypadX);
        assertEquals(417, mirrored.stackX); // 740 - 323 of stack
    }
}
