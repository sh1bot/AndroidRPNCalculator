// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

import java.util.List;

final class NumericValueCacheTest {
    @Test
    void keepsLiveValuesWithoutDependingOnPresentation() {
        NumericValueCache cache = new NumericValueCache();
        UnifiedReal a = UnifiedReal.TWO.sqrt(), b = new UnifiedReal(3).sqrt();
        NumericValue first = cache.read(a), second = cache.read(b);
        cache.retain(List.of(a));
        assertSame(first, cache.read(a));
        assertNotSame(second, cache.read(b));
        assertEquals(second.num, cache.read(b).num);
        cache.clear();
        assertNotSame(first, cache.read(a));
    }
}
