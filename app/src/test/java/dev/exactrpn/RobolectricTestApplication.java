// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowProcess;

/**
 * Application for the JVM (Robolectric) runs of the instrumentation suite,
 * selected by robolectric.properties in src/test/resources. Never used on a
 * device, where the tests keep the real application and processes.
 *
 * <p>Robolectric collapses the manifest's separate calculation processes into
 * this one, but its bindService delivers a null binder unless the service is
 * pre-registered, so both calculation services are built in-process here and
 * their real binders registered. It also reports pid 0 for this process,
 * which RemoteCalculator reads as a failed HELLO handshake after which it
 * never sends another request, so the sandbox is given a nonzero pid.
 */
public final class RobolectricTestApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ShadowProcess.setPid(4242);
        register(CalculationService.class);
        register(LockedCalculationService.class);
    }

    private <T extends Service> void register(Class<T> service) {
        T instance = Robolectric.buildService(service).create().get();
        Shadows.shadowOf(this)
                .setComponentNameAndServiceForBindService(
                        new ComponentName(this, service),
                        instance.onBind(new Intent(this, service)));
    }
}
