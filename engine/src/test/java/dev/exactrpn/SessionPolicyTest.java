// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SessionPolicyTest {
    @Test
    void temporarySessionNeverAcquiresPersistentStateAccess() {
        SessionPolicy policy = new SessionPolicy(true);
        for (boolean locked : new boolean[] {true, false, true, false})
            assertFalse(policy.canUseSavedState(locked));
        assertTrue(policy.temporary);
    }

    @Test
    void ordinarySessionCannotAccessSavedStateWhileLocked() {
        SessionPolicy policy = new SessionPolicy(false);
        assertTrue(policy.canUseSavedState(false));
        assertFalse(policy.canUseSavedState(true));
        assertTrue(policy.canUseSavedState(false));
    }
}
