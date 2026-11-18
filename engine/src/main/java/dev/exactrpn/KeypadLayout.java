// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/**
 * Geometry policy in pixels, independent of orientation flags and Android views.
 *
 * <p>This is the only place layout policy lives: {@link #choose} returns the workspace
 * geometry — the stack and keypad rectangles and their positions, the regime, and where the
 * function panel sits — and the workspace merely applies it. The plan also carries the keypad's
 * internal subdivision (the control-row height, digit width and function-panel extent), so the
 * Android view does not independently reproduce the geometry policy. Five regimes, from extremely wide
 * to extremely tall:
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
    private final boolean expanded;
    private final int columns, primaryRows;
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
     * two thirds of a normal key on their short edge. The R1/R3 column and the R5 band are
     * FN_UNITS units at the family's key unit; a squeezed band takes FN_UNITS / (FN_UNITS +
     * primaryRows) of the keypad body — 2/7 at five primary rows — which the keypad applies
     * when it derives the band from its measured height.
     */
    private static final int FN_UNITS = 2;

    /** The beside family's fixed boundary: the stack's share of the width in R2. */
    private static final int BESIDE_STACK_PERCENT = 50;

    /** The stacked family's fixed boundary: the stack's share of the height in R3 and R4. */
    private static final int STACKED_STACK_PERCENT = 36;

    public final Regime regime;
    public final boolean besideStack;
    public final FunctionPlacement functionPlacement;
    public final int stackWidth, stackHeight;
    /**
     * The stack width this plan would have with the function panel expanded. Equal to {@link
     * #stackWidth} in every regime except R1, where opening the column narrows the stack; the
     * mode header sizes itself from this width so its buttons do not resize on every toggle.
     */
    public final int expandedStackWidth;
    /** The keypad view's total extent, including the R5 function band when open. */
    public final int keypadWidth, keypadHeight;
    /** The keypad's internal subdivision at this plan's measured extent. */
    public final int digitsWidth, controlHeight, functionWidth, functionHeight;

    /** Left edge of the stack within the workspace. */
    public final int stackX;
    /** Left edge of the keypad view within the workspace. */
    public final int keypadX;
    /** The gap the plan was computed with; the keypad view uses it inside the R5 band. */
    public final int gap;

    private KeypadLayout(
            Regime regime,
            boolean besideStack,
            FunctionPlacement functionPlacement,
            int stackWidth,
            int stackHeight,
            int expandedStackWidth,
            int keypadWidth,
            int keypadHeight,
            int gap,
            int stackX,
            int keypadX,
            boolean expanded,
            int columns,
            int primaryRows) {
        this.regime = regime;
        this.besideStack = besideStack;
        this.functionPlacement = functionPlacement;
        this.stackWidth = stackWidth;
        this.stackHeight = stackHeight;
        this.expandedStackWidth = expandedStackWidth;
        this.keypadWidth = keypadWidth;
        this.keypadHeight = keypadHeight;
        this.gap = gap;
        this.stackX = stackX;
        this.keypadX = keypadX;
        this.expanded = expanded;
        this.columns = columns;
        this.primaryRows = primaryRows;
        if (functionPlacement == FunctionPlacement.ABOVE) {
            double unit = (double) keypadWidth / columns;
            controlHeight = (int) Math.round(unit / 2);
            functionWidth = expanded ? keypadWidth : 0;
            functionHeight = expanded ? (int) Math.round(FN_UNITS * unit) : 0;
            digitsWidth = keypadWidth;
        } else {
            double unit = keypadHeight / (primaryRows + .5);
            controlHeight = (int) Math.round(unit / 2);
            if (functionPlacement == FunctionPlacement.LEFT
                    || functionPlacement == FunctionPlacement.RIGHT) {
                int columnWidth = (int) Math.round(FN_UNITS * unit);
                functionWidth = expanded ? columnWidth : 0;
                functionHeight = expanded ? keypadHeight - controlHeight : 0;
                digitsWidth =
                        besideStack
                                ? (int) Math.round(columns * unit)
                                : expanded ? keypadWidth - columnWidth - gap : keypadWidth;
            } else {
                functionWidth = expanded ? keypadWidth : 0;
                functionHeight =
                        expanded
                                ? (keypadHeight - controlHeight) * FN_UNITS
                                        / (FN_UNITS + primaryRows)
                                : 0;
                digitsWidth = keypadWidth;
            }
        }
    }

    /**
     * Builds a plan with its unmirrored horizontal positions: the stack hugs the left of the
     * workspace, and the keypad hugs the right when beside the stack (and starts at 0 when
     * stacked, spanning the width).
     */
    private static KeypadLayout plan(
            Regime regime,
            boolean besideStack,
            FunctionPlacement functionPlacement,
            int width,
            int stackWidth,
            int stackHeight,
            int expandedStackWidth,
            int keypadWidth,
            int keypadHeight,
            int gap,
            boolean expanded,
            int columns,
            int primaryRows) {
        return new KeypadLayout(
                regime,
                besideStack,
                functionPlacement,
                stackWidth,
                stackHeight,
                expandedStackWidth,
                keypadWidth,
                keypadHeight,
                gap,
                0,
                besideStack ? width - keypadWidth : 0,
                expanded,
                columns,
                primaryRows);
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
                expandedStackWidth,
                keypadWidth,
                keypadHeight,
                gap,
                width - stackX - stackWidth,
                width - keypadX - keypadWidth,
                expanded,
                columns,
                primaryRows);
    }

    /**
     * @param mirrored whether to mirror every horizontal position (the left-handed mode);
     *     the mirror covers block placement only — the order of buttons within a row and the
     *     digit grid itself are not mirrored, as whether a left-handed mode should mirror those
     *     too is an undecided design question
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
                assemble(
                        width,
                        height,
                        expanded,
                        gap,
                        minStackWidth,
                        minStackHeight,
                        columns,
                        primaryRows);
        return mirrored ? plan.mirror(width) : plan;
    }

    /**
     * The whole policy as one table. Each family first derives its shared intermediates from
     * the design constants (the key unit, the natural digit width at {@code columns} units,
     * the function column at FN_UNITS units); each regime is then a single row of forms over
     * those intermediates, and the one {@code plan(...)} call at the end is the only
     * construction site. Construction also derives the keypad's internal extents from the
     * resulting measured rectangle.
     * Whether the panel is open is data within a row — the boundary extents select their
     * open or closed form — never a separate construction.
     *
     * <p>The payer cascade picks the row: beside the stack, the stack pays for the column if
     * ceding it leaves the stack its minimum width (R1), else the keypad pays with its height
     * at the fixed boundary (R2); above the keypad, nobody pays if the band fits the column
     * beside natural digits (R3), else the stack pays its height if the natural keypad plus
     * its band leaves the stack its minimum (R5), else the keypad pays (R4). Every fit test
     * is evaluated as if the panel were expanded, so toggling never flips the regime.
     */
    private static KeypadLayout assemble(
            int width,
            int height,
            boolean expanded,
            int gap,
            int minStackWidth,
            int minStackHeight,
            int columns,
            int primaryRows) {
        Regime regime;
        boolean besideStack;
        FunctionPlacement placement;
        int stackWidth, stackHeight, expandedStackWidth, keypadWidth, keypadHeight;
        if (width > height) {
            besideStack = true;
            // Beside family: the keypad is natural at the full height.
            double unit = height / (primaryRows + .5);
            int naturalWidth = (int) Math.round(columns * unit);
            int columnWidth = (int) Math.round(FN_UNITS * unit);
            // R1's fit test: the ceded column crosses two gaps (workspace, then
            // column-to-digits), and what remains must keep the stack's minimum width.
            int openStackWidth = width - gap - (columnWidth + gap + naturalWidth);
            if (openStackWidth >= minStackWidth) {
                regime = Regime.R1;
                placement = FunctionPlacement.LEFT;
                stackWidth = expanded ? openStackWidth : width - gap - naturalWidth;
                stackHeight = height;
                expandedStackWidth = openStackWidth;
                keypadWidth = expanded ? columnWidth + gap + naturalWidth : naturalWidth;
                keypadHeight = height;
            } else {
                regime = Regime.R2;
                placement = FunctionPlacement.SQUEEZE;
                stackWidth = width * BESIDE_STACK_PERCENT / 100;
                stackHeight = height;
                expandedStackWidth = stackWidth;
                keypadWidth = width - stackWidth - gap;
                keypadHeight = height;
            }
        } else {
            besideStack = false;
            // Stacked family: the fixed boundary gives the stack its share of the height and
            // the keypad the band below it; R5 moves the boundary instead.
            stackWidth = width;
            expandedStackWidth = width;
            keypadWidth = width;
            int fixedStackHeight = height * STACKED_STACK_PERCENT / 100;
            int blockHeight = Math.max(1, height - fixedStackHeight - gap);
            double unit = blockHeight / (primaryRows + .5);
            if ((columns + FN_UNITS) * unit + gap <= width) {
                // R3: the band fits the column beside natural digits; leftover width widens
                // them. The keypad's height is the block height, so it recovers the unit
                // from its own height when it derives the column.
                regime = Regime.R3;
                placement = FunctionPlacement.LEFT;
                stackHeight = fixedStackHeight;
                keypadHeight = blockHeight;
            } else {
                // R5's intermediates are width-derived: the keypad natural at the full width.
                double widthUnit = (double) width / columns;
                int bandHeight = (int) Math.round(FN_UNITS * widthUnit);
                int naturalHeight = (int) Math.round((primaryRows + .5) * widthUnit);
                if (height - 2 * gap - bandHeight - naturalHeight >= minStackHeight) {
                    regime = Regime.R5;
                    placement = FunctionPlacement.ABOVE;
                    keypadHeight = expanded ? bandHeight + gap + naturalHeight : naturalHeight;
                    stackHeight = height - gap - keypadHeight;
                } else {
                    regime = Regime.R4;
                    placement = FunctionPlacement.SQUEEZE;
                    stackHeight = fixedStackHeight;
                    keypadHeight = blockHeight;
                }
            }
        }
        return plan(
                regime,
                besideStack,
                placement,
                width,
                stackWidth,
                stackHeight,
                expandedStackWidth,
                keypadWidth,
                keypadHeight,
                gap,
                expanded,
                columns,
                primaryRows);
    }
}
