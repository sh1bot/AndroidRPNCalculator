// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** A session's persistence class never changes when the device unlocks. */
public final class SessionPolicy {
    public final boolean temporary;

    public SessionPolicy(boolean temporary) {
        this.temporary = temporary;
    }

    public boolean canUseSavedState(boolean locked) {
        return !temporary && !locked;
    }
}
