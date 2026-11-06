// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class EntryOperatorTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private void key(CalculatorSession session, String key) {
        instrumentation.runOnMainSync(() -> session.enqueue(Command.parse(key)));
        long until = SystemClock.elapsedRealtime() + 15000;
        while (session.busy && SystemClock.elapsedRealtime() < until) SystemClock.sleep(25);
        assertFalse("Calculation timed out", session.busy);
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
