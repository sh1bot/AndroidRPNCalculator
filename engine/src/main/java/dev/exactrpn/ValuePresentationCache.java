// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Cache immutable values only for the active display mode. Clipboard formatting is on demand. */
public final class ValuePresentationCache {
    private static final class Text {
        String full, compact;
    }

    private final IdentityHashMap<UnifiedReal, Text> values = new IdentityHashMap<>();
    private ValueFormatter.Notation notation;
    private FractionMode fraction;
    private UnifiedReal top;
    private String compactTop;

    public void setMode(ValueFormatter.Notation notation, FractionMode fraction) {
        if (this.notation == notation && this.fraction == fraction) return;
        clear();
        this.notation = notation;
        this.fraction = fraction;
    }

    public String row(UnifiedReal value, boolean newest) {
        Text text = values.computeIfAbsent(value, ignored -> new Text());
        if (newest) {
            if (text.full == null)
                text.full = ValueFormatter.format(value, true, notation, fraction);
            return text.full;
        }
        if (text.compact == null)
            text.compact = ValueFormatter.format(value, false, notation, fraction);
        return text.compact;
    }

    public String compactTop(UnifiedReal value) {
        if (top != value) {
            top = value;
            compactTop = ValueFormatter.format(value, false, notation, FractionMode.DECIMAL);
        }
        return compactTop;
    }

    public void retain(List<UnifiedReal> live) {
        Set<UnifiedReal> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(live);
        values.keySet().retainAll(identities);
        if (!identities.contains(top)) {
            top = null;
            compactTop = null;
        }
    }

    public void clear() {
        values.clear();
        top = null;
        compactTop = null;
    }
}
