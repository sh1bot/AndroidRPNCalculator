// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Stable persisted IDs; cycling order need not match storage order. */
public final class FractionMode {
    public static final int DECIMAL = 0;
    public static final int IMPROPER = 1;
    public static final int MIXED = 2;

    private FractionMode() {}

    public static int next(int mode) {
        switch (mode) {
            case DECIMAL: return MIXED;
            case MIXED: return IMPROPER;
            default: return DECIMAL;
        }
    }
}
