// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class TauPreferenceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private MainActivity open(Context context) {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        long until = SystemClock.elapsedRealtime() + 15000;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> ready.set(!session.busy));
            if (ready.get()) return activity;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < until);
        fail("Calculator did not finish restoring");
        return activity;
    }

    @Test
    public void constantPreferenceSurvivesANewActivityAndSession() throws Exception {
        Context context = instrumentation.getTargetContext();
        MainActivity activity = open(context);
        SharedPreferences preferences = activity.getPreferences(0);
        boolean original = preferences.getBoolean("tauConstant", false);
        try {
            MainActivity first = activity;
            CalculatorSession oldSession = StackSelectionTest.session(first);
            instrumentation.runOnMainSync(
                    () -> {
                        Button constant = first.getWindow().getDecorView().findViewWithTag("π");
                        CalculationSnapshot checkpoint = oldSession.checkpoint;
                        constant.performLongClick();
                        assertEquals(original ? "π" : "τ", constant.getText().toString());
                        assertEquals(!original, preferences.getBoolean("tauConstant", false));
                        assertEquals(checkpoint, oldSession.checkpoint);
                    });
            StackSelectionTest.finish(activity);
            activity = null;
            activity = open(context);
            MainActivity reopened = activity;
            assertNotSame(oldSession, StackSelectionTest.session(reopened));
            instrumentation.runOnMainSync(
                    () -> {
                        Button constant = reopened.getWindow().getDecorView().findViewWithTag("π");
                        assertEquals(original ? "π" : "τ", constant.getText().toString());
                        reopened.getWindow()
                                .getDecorView()
                                .findViewWithTag("COMPLEMENTS")
                                .performClick();
                        assertEquals(original ? "π" : "τ", constant.getText().toString());
                    });
        } finally {
            if (activity != null) StackSelectionTest.finish(activity);
            preferences.edit().putBoolean("tauConstant", original).commit();
        }
    }
}
