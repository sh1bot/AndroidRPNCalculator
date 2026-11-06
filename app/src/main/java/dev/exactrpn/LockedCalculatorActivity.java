// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Always temporary, even if authentication happens before or during this activity's lifetime. */
public final class LockedCalculatorActivity extends MainActivity {
    @Override
    public void onCreate(android.os.Bundle state) {
        if (android.os.Build.VERSION.SDK_INT < 27)
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        super.onCreate(state);
    }

    @Override
    protected boolean temporaryLaunch() {
        return true;
    }
}
