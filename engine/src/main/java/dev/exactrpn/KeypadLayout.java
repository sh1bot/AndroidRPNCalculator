// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Geometry policy in pixels, independent of orientation flags and Android views. */
public final class KeypadLayout {
    public final boolean besideStack, functionsBesideDigits;
    public final int keypadWidth, keypadHeight, stackWidth, stackHeight, primaryWidth;

    private KeypadLayout(
            boolean besideStack,
            boolean functionsBesideDigits,
            int keypadWidth,
            int keypadHeight,
            int stackWidth,
            int stackHeight,
            int primaryWidth) {
        this.besideStack = besideStack;
        this.functionsBesideDigits = functionsBesideDigits;
        this.keypadWidth = keypadWidth;
        this.keypadHeight = keypadHeight;
        this.stackWidth = stackWidth;
        this.stackHeight = stackHeight;
        this.primaryWidth = primaryWidth;
    }

    public static KeypadLayout choose(
            int width,
            int height,
            boolean expanded,
            int gap,
            int keyMinimum,
            int controlHeight,
            int stackMinimum,
            int stackHeightMinimum,
            int columns,
            int primaryRows) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        int digitsMinimum = columns * keyMinimum;
        // Move beside the stack only when the vertical arrangement cannot keep useful key heights.
        boolean beside =
                width >= stackMinimum + gap + digitsMinimum
                        && height
                                < stackHeightMinimum
                                        + controlHeight
                                        + (primaryRows + (expanded ? 3 : 0)) * keyMinimum;
        if (!beside) {
            int naturalHeight = controlHeight + primaryRows * (width / columns);
            int keypadHeight =
                    Math.min(
                            naturalHeight,
                            Math.max(1, height - Math.min(stackHeightMinimum, height / 3)));
            return new KeypadLayout(
                    false,
                    false,
                    width,
                    keypadHeight,
                    width,
                    Math.max(1, height - keypadHeight),
                    width);
        }
        int primary =
                Math.max(
                        digitsMinimum,
                        Math.min(
                                (columns + 2) * keyMinimum,
                                (height - controlHeight) * columns / primaryRows));
        primary = Math.min(primary, width - stackMinimum - gap);
        int functionWidth = 3 * keyMinimum;
        boolean functionsBeside =
                expanded && width >= stackMinimum + primary + functionWidth + 2 * gap;
        int keypadWidth = primary + (functionsBeside ? functionWidth + gap : 0);
        return new KeypadLayout(
                true,
                functionsBeside,
                keypadWidth,
                height,
                width - keypadWidth - gap,
                height,
                primary);
    }
}
