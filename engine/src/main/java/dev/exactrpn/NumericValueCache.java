// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.IdentityHashMap;
import java.util.List;

/** Cache the default numerical read independently of presentation modes. */
public final class NumericValueCache {
    private final IdentityHashMap<UnifiedReal, NumericValue> values = new IdentityHashMap<>();

    public NumericValue read(UnifiedReal value) {
        return values.computeIfAbsent(value, v -> NumericEvaluator.read(v, NumericRequest.DEFAULT));
    }

    public NumericValue read(UnifiedReal value, NumericRequest request) {
        return request.limit.equals(NumericRequest.DEFAULT.limit)
                        && request.base == NumericRequest.DEFAULT.base
                        && request.exactBytes == NumericRequest.DEFAULT.exactBytes
                        && !request.requireRational
                ? read(value)
                : NumericEvaluator.read(value, request);
    }

    public void retain(List<UnifiedReal> live) {
        IdentityHashMap<UnifiedReal, Boolean> identities = new IdentityHashMap<>();
        for (UnifiedReal value : live) identities.put(value, true);
        values.keySet().retainAll(identities.keySet());
    }

    public void clear() {
        values.clear();
    }
}
