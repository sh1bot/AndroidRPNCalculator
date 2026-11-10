// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;

/** User-facing numerical radix and digit alphabet. */
public enum RadixMode {
    DECIMAL(10, 3),
    HEX(16, 4),
    DOZENAL(12, 3);
    public final int radix, grouping;

    RadixMode(int radix, int grouping) {
        this.radix = radix;
        this.grouping = grouping;
    }

    public RadixMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String digits(BigInteger value) {
        return digits(value.toString(radix).toUpperCase(java.util.Locale.ROOT));
    }

    public String digits(String canonical) {
        if (this != DOZENAL) return canonical.toUpperCase(java.util.Locale.ROOT);
        return canonical
                .replace('A', '\u218a')
                .replace('a', '\u218a')
                .replace('B', '\u218b')
                .replace('b', '\u218b');
    }

    public char canonicalDigit(char glyph) {
        if (glyph == '\u218a') return 'A';
        if (glyph == '\u218b') return 'B';
        return Character.toUpperCase(glyph);
    }

    public int digit(char glyph) {
        char canonical = canonicalDigit(glyph);
        int value = Character.digit(canonical, radix);
        return value;
    }

    public static RadixMode of(int radix) {
        for (RadixMode mode : values()) if (mode.radix == radix) return mode;
        throw new IllegalArgumentException("Unsupported display radix");
    }
}
