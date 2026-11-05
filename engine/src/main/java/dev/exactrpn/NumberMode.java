// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Locale display, computer decimal dot, or locale decimal without digit grouping. */
public enum NumberMode {
    LOCALE(0),
    DOT(1),
    UNGROUPED(2);
    public final int id;

    NumberMode(int id) {
        this.id = id;
    }

    public NumberMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static NumberMode fromId(int id) {
        for (NumberMode mode : values()) if (mode.id == id) return mode;
        return LOCALE;
    }
}
