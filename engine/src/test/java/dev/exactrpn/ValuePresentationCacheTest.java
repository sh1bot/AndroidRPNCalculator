// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.util.List;

final class ValuePresentationCacheTest {
    @Test
    void cachedRowsKeepPrecisionModesAndValueIdentitySeparate() {
        ValuePresentationCache cache = new ValuePresentationCache();
        UnifiedReal root = UnifiedReal.TWO.sqrt();
        ValueFormatter.Notation sci = ValueFormatter.Notation.SCIENTIFIC;
        String row = cache.row(root, false, sci, FractionMode.DECIMAL);
        assertSame(row, cache.row(root, false, sci, FractionMode.DECIMAL));
        cache.retain(List.of(root));
        assertSame(row, cache.row(root, false, sci, FractionMode.DECIMAL));
        assertEquals(
                ValueFormatter.clipboard(root, sci),
                cache.clipboard(root, sci, FractionMode.DECIMAL));
        UnifiedReal third = UnifiedReal.ONE.divide(new UnifiedReal(3));
        assertEquals("1/3", cache.row(third, true, sci, FractionMode.IMPROPER));
        assertEquals("1/3", cache.clipboard(third, sci, FractionMode.IMPROPER));
        assertNotEquals("1/3", cache.row(third, true, sci, FractionMode.DECIMAL));
        UnifiedReal huge = RpnCalculator.parse("12345678901234567890");
        assertEquals("12345678901234567890", cache.row(huge, true, sci, FractionMode.DECIMAL));
        assertTrue(cache.row(huge, false, sci, FractionMode.DECIMAL).contains("e"));
        assertEquals(
                ValueFormatter.format(huge, false, ValueFormatter.Notation.ENGINEERING),
                cache.row(huge, false, ValueFormatter.Notation.ENGINEERING, FractionMode.DECIMAL));
    }

    @Test
    void removedValuesAreReleasedButLiveRowsRemainCached() {
        ValuePresentationCache cache = new ValuePresentationCache();
        UnifiedReal a = UnifiedReal.TWO.sqrt(), b = new UnifiedReal(3).sqrt();
        ValueFormatter.Notation sci = ValueFormatter.Notation.SCIENTIFIC;
        String first = cache.row(a, false, sci, FractionMode.DECIMAL);
        String second = cache.row(b, false, sci, FractionMode.DECIMAL);
        cache.retain(List.of(a));
        assertSame(first, cache.row(a, false, sci, FractionMode.DECIMAL));
        assertEquals(second, cache.row(b, false, sci, FractionMode.DECIMAL));
        cache.clear();
        assertEquals(first, cache.row(a, false, sci, FractionMode.DECIMAL));
    }
}
