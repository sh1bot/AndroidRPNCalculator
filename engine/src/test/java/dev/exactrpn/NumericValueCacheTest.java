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
        NumericValue first = cache.read(a, NumericRequest.DEFAULT);
        NumericValue second = cache.read(b, NumericRequest.DEFAULT);
        cache.retain(List.of(a));
        assertSame(first, cache.read(a, NumericRequest.DEFAULT));
        assertNotSame(second, cache.read(b, NumericRequest.DEFAULT));
        assertEquals(second.num, cache.read(b, NumericRequest.DEFAULT).num);
        cache.clear();
        assertNotSame(first, cache.read(a, NumericRequest.DEFAULT));
    }
}
