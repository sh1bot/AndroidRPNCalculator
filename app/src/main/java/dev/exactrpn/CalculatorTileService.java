// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** User-selected Quick Settings entry point; never requests dismissal of the keyguard. */
public final class CalculatorTileService extends TileService {
    static Intent launchIntent(Context context, boolean locked) {
        return new Intent(context, locked ? LockedCalculatorActivity.class : MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.app_name));
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        boolean locked = isLocked() || DeviceAccess.locked(this);
        Intent intent = launchIntent(this, locked);
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions options = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 35)
                options.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            PendingIntent launch =
                    PendingIntent.getActivity(
                            this,
                            locked ? 1 : 0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                            options.toBundle());
            startActivityAndCollapse(launch);
        } else startActivityAndCollapse(intent);
    }
}
