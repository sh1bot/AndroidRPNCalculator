// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

public final class CalculationLimits {
    private CalculationLimits() {}

    public static final int TRANSFER_BYTES = 256 * 1024;
    public static final int SAVED_BYTES = TRANSFER_BYTES - 1024;
    // Leave room for a complete UTF-8 entry when the UI saves an unfinished draft.
    public static final int ARITHMETIC_BYTES = SAVED_BYTES - 3 * EntryEditor.MAX_ENTRY_LENGTH - 256;

    public static void require(long length, int limit) {
        if (length > limit)
            throw CalculationError.invalid(CalculationError.Code.TRANSFER_TOO_LARGE);
    }
}
