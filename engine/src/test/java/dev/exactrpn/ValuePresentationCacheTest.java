// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.util.List;

final class ValuePresentationCacheTest {
    @Test
    void activeModeKeepsLiveRowsAndReformatsAfterModeChanges() {
        ValuePresentationCache cache = new ValuePresentationCache();
        var sci = ValueFormatter.Notation.SCIENTIFIC;
        UnifiedReal third = UnifiedReal.ONE.divide(new UnifiedReal(3));
        cache.setMode(sci, FractionMode.IMPROPER);
        String fraction = cache.row(third, true);
        assertEquals("1/3", fraction);
        assertNotEquals(fraction, cache.compactTop(third));
        assertSame(fraction, cache.row(third, true));
        cache.retain(List.of(third));
        assertSame(fraction, cache.row(third, true));
        cache.setMode(sci, FractionMode.DECIMAL);
        assertNotEquals(fraction, cache.row(third, true));
        cache.setMode(sci, FractionMode.IMPROPER);
        assertEquals(fraction, cache.row(third, true));
    }

    @Test
    void removedValuesAreReleasedButLiveRowsRemainCached() {
        ValuePresentationCache cache = new ValuePresentationCache();
        cache.setMode(ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL);
        UnifiedReal a = UnifiedReal.TWO.sqrt(), b = new UnifiedReal(3).sqrt();
        String first = cache.row(a, false), second = cache.row(b, false);
        cache.retain(List.of(a));
        assertSame(first, cache.row(a, false));
        assertEquals(second, cache.row(b, false));
        cache.clear();
        assertEquals(first, cache.row(a, false));
    }
}
