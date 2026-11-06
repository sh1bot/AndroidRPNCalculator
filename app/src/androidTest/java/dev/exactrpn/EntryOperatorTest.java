// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class EntryOperatorTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private void key(CalculatorSession session, String key) {
        instrumentation.runOnMainSync(() -> session.enqueue(Command.parse(key)));
        long until = SystemClock.elapsedRealtime() + 15000;
        while (session.busy && SystemClock.elapsedRealtime() < until) SystemClock.sleep(25);
        assertFalse("Calculation timed out", session.busy);
    }

    private void awaitUi(BooleanSupplier condition) {
        long until = SystemClock.elapsedRealtime() + 10000;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < until);
        fail("Timed out waiting for error notice");
    }

    @Test
    public void repeatedUnderflowShowsTheNoticeAgainAfterItExpires() throws Exception {
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(
                                                instrumentation.getTargetContext(),
                                                LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = StackSelectionTest.session(activity);
        try {
            TextView notice =
                    activity.getWindow().getDecorView().findViewWithTag("calculation-notice");
            key(session, "1");
            key(session, "+");
            awaitUi(() -> notice.getVisibility() == View.VISIBLE);
            String message = session.error;
            long firstError = session.errorRevision;
            awaitUi(() -> notice.getVisibility() == View.GONE);
            // No intervening edit: this failure starts with the same value already on the stack.
            key(session, "+");
            assertEquals(message, session.error);
            assertTrue(session.errorRevision > firstError);
            awaitUi(() -> notice.getVisibility() == View.VISIBLE);
            instrumentation.runOnMainSync(() -> assertEquals(message, notice.getText().toString()));
            assertEquals(Arrays.asList("1"), session.display.rows);
            assertFalse(session.display.editing);
        } finally {
            StackSelectionTest.finish(activity);
        }
    }

    @Test
    public void failedOperatorsPublishAndCheckpointCommittedEntryWithTheirError() throws Exception {
        CalculatorSession session =
                new CalculatorSession(
                        ValueFormatter.Notation.SCIENTIFIC,
                        RpnCalculator.AngleUnit.RAD,
                        NumberMode.DOT,
                        FractionMode.DECIMAL,
                        true);
        session.attach(instrumentation.getTargetContext());
        try {
            key(session, "1");
            key(session, "+");
            assertFalse(session.display.editing);
            assertEquals(Arrays.asList("1"), session.display.rows);
            assertEquals("Error: Enter another value first, then the operator", session.error);
            RpnCalculator saved =
                    RpnCalculator.load(Files.readAllBytes(new File(session.checkpoint).toPath()));
            assertFalse(saved.isEditing());
            assertEquals("1", saved.values().get(0).bigIntegerValue().toString());
            key(session, "0");
            key(session, "÷");
            assertFalse(session.display.editing);
            assertEquals(Arrays.asList("1", "0"), session.display.rows);
            assertFalse(session.error.isEmpty());
            key(session, "UNDO");
            assertTrue(session.display.editing);
            assertEquals("0", session.display.entry);
            assertEquals(Arrays.asList("1"), session.display.rows);
            key(session, "CE");
            key(session, "PASTE:1,234");
            key(session, "+");
            assertTrue(session.display.editing);
            assertEquals("1,234", session.display.entry);
            assertEquals(Arrays.asList("1"), session.display.rows);
            assertFalse(session.error.isEmpty());
        } finally {
            session.close();
            assertTrue(session.awaitClosed(15, TimeUnit.SECONDS));
        }
    }
}
