// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Display modes in their user-facing cycle order. */
public enum FractionMode {
    DECIMAL,
    MIXED,
    IMPROPER;

    public FractionMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
