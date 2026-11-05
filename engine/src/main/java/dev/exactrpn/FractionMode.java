// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** IDs are a persistence contract; the user-facing cycle has its own order. */
public enum FractionMode {
    DECIMAL(0),
    MIXED(2),
    IMPROPER(1);
    public final int id;

    FractionMode(int id) {
        this.id = id;
    }

    public FractionMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static FractionMode fromId(int id) {
        for (FractionMode mode : values()) if (mode.id == id) return mode;
        return DECIMAL;
    }
}
