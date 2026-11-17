// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/**
 * Geometry policy in pixels, independent of orientation flags and Android views.
 *
 * <p>This is the only place layout policy lives: {@link #choose} returns the complete plan and
 * the views merely execute it. Five regimes, from extremely wide to extremely tall:
 *
 * <ul>
 *   <li>R1 — stack on the left, boundary moves to accommodate the function column, keypad keeps
 *       its natural aspect (columns wide by primaryRows + ½ high, the ½ being the control row).
 *   <li>R2 — stack on the left, fixed boundary at 50%, functions squeeze into the keypad body.
 *   <li>R3 — stack above, fixed boundary at 36% of the height, functions in a column on the left
 *       of the keypad.
 *   <li>R4 — stack above, fixed boundary at 36%, functions squeeze into the keypad body.
 *   <li>R5 — stack above, boundary moves to accommodate the function band, keypad keeps its
 *       natural aspect at the full width.
 * </ul>
 *
 * <p>The stack is beside the keypad exactly when the window is wider than tall, so rotating a
 * phone always crosses the family boundary. Regime selection is always evaluated as if the
 * function panel were expanded, so toggling the panel never flips the regime; only the geometry
 * follows the actual expanded flag.
 */
public final class KeypadLayout {
    /** The five layout regimes, wide to tall. */
    public enum Regime {
        R1,
        R2,
        R3,
        R4,
        R5
    }

    /** Where the function panel sits relative to the primary keys. */
    public enum FunctionPlacement {
        /** A column on the left of the digits, below control-row level (R1, R3). */
        LEFT,
        /** A column on the right of the digits, below control-row level (mirrored R1, R3). */
        RIGHT,
        /** A band inside the keypad body, below the control row, squeezing the digits (R2, R4). */
        SQUEEZE,
        /** A band at the top of the keypad view, above the control row, ceded by the stack (R5). */
        ABOVE
    }

    /**
     * The function panel's short edge, in key units: 3f with f = ⅔, the function keys being about
     * two thirds of a normal key on their short edge. A squeezed band takes FN_UNITS / (FN_UNITS +
     * primaryRows) of the keypad body — 2/7 at five primary rows.
     */
    private static final int FN_UNITS = 2;

    public final Regime regime;
    public final boolean besideStack;
    public final FunctionPlacement functionPlacement;
    public final int stackWidth, stackHeight;
    /** The keypad view's total extent, including the R5 function band when open. */
    public final int keypadWidth, keypadHeight;

    /** Left edge of the stack within the workspace. */
    public final int stackX;
    /** Left edge of the keypad view within the workspace. */
    public final int keypadX;
    /** Left edge of the primary (digits) block within the keypad view. */
    public final int primaryX;
    /** Left edge of the function panel within the keypad view. */
    public final int functionX;
    /** Width of the primary (digits) block inside the keypad view. */
    public final int primaryWidth;
    /** Height of the control row: half a key unit of the natural keypad block. */
    public final int controlHeight;
    /** The function panel's extent in this plan; zero when the panel is closed. */
    public final int functionWidth, functionHeight;
    /** The gap the plan was computed with; the keypad view uses it inside the R5 band. */
    public final int gap;

    private KeypadLayout(
            Regime regime,
            boolean besideStack,
            FunctionPlacement functionPlacement,
            int stackWidth,
            int stackHeight,
            int keypadWidth,
            int keypadHeight,
            int primaryWidth,
            int controlHeight,
            int functionWidth,
            int functionHeight,
            int gap,
            int stackX,
            int keypadX,
            int primaryX,
            int functionX) {
        this.regime = regime;
        this.besideStack = besideStack;
        this.functionPlacement = functionPlacement;
        this.stackWidth = stackWidth;
        this.stackHeight = stackHeight;
        this.keypadWidth = keypadWidth;
        this.keypadHeight = keypadHeight;
        this.primaryWidth = primaryWidth;
        this.controlHeight = controlHeight;
        this.functionWidth = functionWidth;
        this.functionHeight = functionHeight;
        this.gap = gap;
        this.stackX = stackX;
        this.keypadX = keypadX;
        this.primaryX = primaryX;
        this.functionX = functionX;
    }

    /**
     * Builds a plan with its unmirrored horizontal positions: the stack hugs the left of the
     * workspace, the keypad hugs the right when beside the stack (and starts at 0 when stacked,
     * spanning the width), the digits hug the right of the keypad view, and a function column
     * hugs its left; a function band shares the digits' left edge.
     */
    private static KeypadLayout plan(
            Regime regime,
            boolean besideStack,
            FunctionPlacement functionPlacement,
            int width,
            int stackWidth,
            int stackHeight,
            int keypadWidth,
            int keypadHeight,
            int primaryWidth,
            int controlHeight,
            int functionWidth,
            int functionHeight,
            int gap) {
        int primaryX = keypadWidth - primaryWidth;
        int functionX = functionPlacement == FunctionPlacement.LEFT ? 0 : primaryX;
        return new KeypadLayout(
                regime,
                besideStack,
                functionPlacement,
                stackWidth,
                stackHeight,
                keypadWidth,
                keypadHeight,
                primaryWidth,
                controlHeight,
                functionWidth,
                functionHeight,
                gap,
                0,
                besideStack ? width - keypadWidth : 0,
                primaryX,
                functionX);
    }

    /** The exact horizontal mirror of this plan: same sizes and vertical geometry, mirrored x. */
    private KeypadLayout mirror(int width) {
        return new KeypadLayout(
                regime,
                besideStack,
                functionPlacement == FunctionPlacement.LEFT
                        ? FunctionPlacement.RIGHT
                        : functionPlacement,
                stackWidth,
                stackHeight,
                keypadWidth,
                keypadHeight,
                primaryWidth,
                controlHeight,
                functionWidth,
                functionHeight,
                gap,
                width - stackX - stackWidth,
                width - keypadX - keypadWidth,
                keypadWidth - primaryX - primaryWidth,
                keypadWidth - functionX - functionWidth);
    }

    /**
     * @param mirrored whether to mirror every horizontal position (the coming left-handed
     *     mode); the mirror covers block placement only — the order of buttons within a row and
     *     the digit grid itself are not mirrored, as whether a left-handed mode should mirror
     *     those too is an undecided design question
     * @param minStackWidth the stack's minimum width: room for its longest line plus labels
     * @param minStackHeight the stack's minimum height: its header plus its minimum line count
     */
    public static KeypadLayout choose(
            int width,
            int height,
            boolean expanded,
            boolean mirrored,
            int gap,
            int minStackWidth,
            int minStackHeight,
            int columns,
            int primaryRows) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        KeypadLayout plan =
                width > height
                        ? beside(width, height, expanded, gap, minStackWidth, columns, primaryRows)
                        : stacked(width, height, expanded, gap, minStackHeight, columns, primaryRows);
        return mirrored ? plan.mirror(width) : plan;
    }

    private static KeypadLayout beside(
            int width,
            int height,
            boolean expanded,
            int gap,
            int minStackWidth,
            int columns,
            int primaryRows) {
        double unit = height / (primaryRows + .5);
        int controlHeight = (int) Math.round(height / (2.0 * primaryRows + 1));
        int primaryWidth = (int) Math.round(columns * unit);
        int functionWidth = (int) Math.round(FN_UNITS * unit);
        // R1: the keypad is natural at the full height and the function column is ceded by the
        // stack, which must still keep its minimum width. The column sits inside the keypad
        // view, so an open plan crosses two gaps: workspace and column-to-digits.
        int openStackWidth = width - gap - (functionWidth + gap + primaryWidth);
        if (openStackWidth >= minStackWidth) {
            if (expanded)
                return plan(
                        Regime.R1,
                        true,
                        FunctionPlacement.LEFT,
                        width,
                        openStackWidth,
                        height,
                        functionWidth + gap + primaryWidth,
                        height,
                        primaryWidth,
                        controlHeight,
                        functionWidth,
                        height - controlHeight,
                        gap);
            return plan(
                    Regime.R1,
                    true,
                    FunctionPlacement.LEFT,
                    width,
                    width - gap - primaryWidth,
                    height,
                    primaryWidth,
                    height,
                    primaryWidth,
                    controlHeight,
                    0,
                    0,
                    gap);
        }
        // R2: fixed boundary at 50%; the functions squeeze into the keypad body.
        int stackWidth = width / 2;
        int keypadWidth = width - stackWidth - gap;
        int functionHeight =
                expanded ? (height - controlHeight) * FN_UNITS / (FN_UNITS + primaryRows) : 0;
        return plan(
                Regime.R2,
                true,
                FunctionPlacement.SQUEEZE,
                width,
                stackWidth,
                height,
                keypadWidth,
                height,
                keypadWidth,
                controlHeight,
                expanded ? keypadWidth : 0,
                functionHeight,
                gap);
    }

    private static KeypadLayout stacked(
            int width,
            int height,
            boolean expanded,
            int gap,
            int minStackHeight,
            int columns,
            int primaryRows) {
        int stackHeight = height * 36 / 100;
        int blockHeight = Math.max(1, height - stackHeight - gap);
        int controlHeight = (int) Math.round(blockHeight / (2.0 * primaryRows + 1));
        double unit = blockHeight / (primaryRows + .5);
        // R3: the fixed 36% boundary leaves a band wide enough for the function column beside a
        // natural keypad; any leftover width widens the digits.
        if ((columns + FN_UNITS) * unit + gap <= width) {
            int functionWidth = (int) Math.round(FN_UNITS * unit);
            return plan(
                    Regime.R3,
                    false,
                    FunctionPlacement.LEFT,
                    width,
                    width,
                    stackHeight,
                    width,
                    blockHeight,
                    expanded ? width - functionWidth - gap : width,
                    controlHeight,
                    expanded ? functionWidth : 0,
                    expanded ? blockHeight - controlHeight : 0,
                    gap);
        }
        // R5: the keypad is natural at the full width and the function band is ceded by the
        // stack — but only when the expanded band still leaves the stack its minimum height.
        // The band sits inside the keypad view above the control row, across one further gap.
        double widthUnit = (double) width / columns;
        int bandHeight = (int) Math.round(FN_UNITS * widthUnit);
        int naturalHeight = (int) Math.round((primaryRows + .5) * widthUnit);
        if (height - 2 * gap - bandHeight - naturalHeight >= minStackHeight) {
            int naturalControl = (int) Math.round(widthUnit / 2);
            if (expanded) {
                int keypadHeight = bandHeight + gap + naturalHeight;
                return plan(
                        Regime.R5,
                        false,
                        FunctionPlacement.ABOVE,
                        width,
                        width,
                        height - gap - keypadHeight,
                        width,
                        keypadHeight,
                        width,
                        naturalControl,
                        width,
                        bandHeight,
                        gap);
            }
            return plan(
                    Regime.R5,
                    false,
                    FunctionPlacement.ABOVE,
                    width,
                    width,
                    height - gap - naturalHeight,
                    width,
                    naturalHeight,
                    width,
                    naturalControl,
                    0,
                    0,
                    gap);
        }
        // R4: fixed boundary at 36%; the functions squeeze into the keypad body.
        int functionHeight =
                expanded ? (blockHeight - controlHeight) * FN_UNITS / (FN_UNITS + primaryRows) : 0;
        return plan(
                Regime.R4,
                false,
                FunctionPlacement.SQUEEZE,
                width,
                width,
                stackHeight,
                width,
                blockHeight,
                width,
                controlHeight,
                expanded ? width : 0,
                functionHeight,
                gap);
    }
}
