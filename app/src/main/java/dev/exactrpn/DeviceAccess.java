// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.KeyguardManager;
import android.content.Context;

/** Guard persistent calculation access; clipboard permissions are left to Android. */
final class DeviceAccess {
    static final class Locked extends IllegalStateException {}

    static boolean locked(Context context) {
        KeyguardManager manager = context.getSystemService(KeyguardManager.class);
        return manager == null || manager.isDeviceLocked() || manager.isKeyguardLocked();
    }

    static void requireUnlocked(Context context) {
        if (locked(context)) throw new Locked();
    }
}
