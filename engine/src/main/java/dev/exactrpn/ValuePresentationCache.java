// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Formatting belongs to immutable value identities, not keypresses. Clipboard text is lazy. */
public final class ValuePresentationCache {
    private static final int MODES = FractionMode.values().length;
    private static final int SLOTS = ValueFormatter.Notation.values().length * MODES;

    private static final class Text {
        String full, compact, clipboard;
    }

    private final IdentityHashMap<UnifiedReal, Text[]> values = new IdentityHashMap<>();

    private Text text(UnifiedReal value, ValueFormatter.Notation notation, FractionMode fraction) {
        Text[] modes = values.computeIfAbsent(value, ignored -> new Text[SLOTS]);
        int index = notation.ordinal() * MODES + fraction.ordinal();
        if (modes[index] == null) modes[index] = new Text();
        return modes[index];
    }

    public String row(
            UnifiedReal value,
            boolean newest,
            ValueFormatter.Notation notation,
            FractionMode fraction) {
        Text text = text(value, notation, fraction);
        if (newest) {
            if (text.full == null)
                text.full = ValueFormatter.format(value, true, notation, fraction);
            return text.full;
        }
        if (text.compact == null)
            text.compact = ValueFormatter.format(value, false, notation, fraction);
        return text.compact;
    }

    public String clipboard(
            UnifiedReal value, ValueFormatter.Notation notation, FractionMode fraction) {
        Text text = text(value, notation, fraction);
        if (text.clipboard == null)
            text.clipboard = ValueFormatter.clipboard(value, notation, fraction);
        return text.clipboard;
    }

    public void retain(List<UnifiedReal> live) {
        Set<UnifiedReal> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(live);
        values.keySet().retainAll(identities);
    }

    public void clear() {
        values.clear();
    }
}
