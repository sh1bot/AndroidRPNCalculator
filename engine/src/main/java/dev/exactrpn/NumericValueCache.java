// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Cache the default numerical read independently of presentation modes. */
public final class NumericValueCache {
    private final Map<NumericRequest, IdentityHashMap<UnifiedReal, NumericValue>> values =
            new HashMap<>();

    public NumericValue read(UnifiedReal value) {
        return read(value, NumericRequest.DEFAULT);
    }

    public NumericValue read(UnifiedReal value, NumericRequest request) {
        // Precise reads are rare copy/export operations. Do not retain their potentially large
        // exact-rational payloads in the display cache.
        if (request.requireRational) return NumericEvaluator.read(value, request);
        return values.computeIfAbsent(request, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(value, v -> NumericEvaluator.read(v, request));
    }

    public void retain(List<UnifiedReal> live) {
        IdentityHashMap<UnifiedReal, Boolean> identities = new IdentityHashMap<>();
        for (UnifiedReal value : live) identities.put(value, true);
        for (IdentityHashMap<UnifiedReal, NumericValue> cache : values.values())
            cache.keySet().retainAll(identities.keySet());
    }

    public void clear() {
        values.clear();
    }
}
