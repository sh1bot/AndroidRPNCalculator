// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class WindowInsetsTest {
    @Test
    public void cameraAndStatusBarClearanceIsAppliedOnlyOnce() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(
                    () -> {
                        float density = context.getResources().getDisplayMetrics().density;
                        int status = Math.round(24 * density), cutout = Math.round(40 * density);
                        ViewGroup content = activity.findViewById(android.R.id.content);
                        ViewGroup screen = (ViewGroup) content.getChildAt(0);
                        LinearLayout root = (LinearLayout) screen.getChildAt(0);
                        int[] location = new int[2];
                        root.getLocationOnScreen(location);
                        assertEquals(
                                "Decor must not inset content before app padding", 0, location[1]);
                        WindowInsets insets =
                                Build.VERSION.SDK_INT < 29
                                        ? root.getRootWindowInsets()
                                                .replaceSystemWindowInsets(0, status, 0, status)
                                        : new WindowInsets.Builder()
                                                .setSystemWindowInsets(
                                                        Insets.of(0, status, 0, status))
                                                .setDisplayCutout(
                                                        new DisplayCutout(
                                                                new Rect(0, cutout, 0, 0),
                                                                Collections.emptyList()))
                                                .build();
                        int expectedTop = Build.VERSION.SDK_INT < 29 ? status : cutout;
                        root.dispatchApplyWindowInsets(insets);
                        assertEquals(
                                "System clearance includes the status bar",
                                expectedTop + Math.round(4 * density),
                                root.getPaddingTop());
                        assertEquals(
                                "Navigation bar clearance is applied once",
                                status + Math.round(12 * density),
                                root.getPaddingBottom());
                        root.dispatchApplyWindowInsets(insets);
                        assertEquals(
                                "Repeated inset dispatch must not accumulate padding",
                                expectedTop + Math.round(4 * density),
                                root.getPaddingTop());
                    });
        } finally {
            StackSelectionTest.finish(activity);
        }
    }
}
