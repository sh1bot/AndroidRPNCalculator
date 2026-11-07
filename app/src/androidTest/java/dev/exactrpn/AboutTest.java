// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.withSubstring;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AboutTest {
    @Test
    public void menuShowsOfflineCreditsAndLicensesWithRepositoryLink() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            onView(withTagValue(is("overflow-menu"))).perform(click());
            onView(withText("Help")).inRoot(isPlatformPopup()).perform(click());
            onView(withTagValue(is("document-text")))
                    .check(matches(withSubstring("Your first calculation")));
            pressBack();
            onView(withTagValue(is("overflow-menu"))).perform(click());
            onView(withText("Licenses")).inRoot(isPlatformPopup()).perform(click());
            onView(withTagValue(is("document-text")))
                    .check(matches(withSubstring("TERMS AND CONDITIONS")));
            assertTrue(AboutDialog.read(activity, "licenses.html.gz").contains("Hans-J. Boehm"));
            assertTrue(AboutDialog.read(activity, "licenses.html.gz").contains("Silicon Graphics"));
            pressBack();
        } finally {
            StackSelectionTest.finish(activity);
        }
    }
}
