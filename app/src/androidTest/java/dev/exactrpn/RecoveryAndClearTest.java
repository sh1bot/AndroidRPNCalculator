// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class RecoveryAndClearTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private interface Condition {
        boolean ready();
    }

    private void await(Condition condition) {
        long until = SystemClock.elapsedRealtime() + 15000;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> ready.set(condition.ready()));
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < until);
        fail("Timed out waiting for recovery/entry UI");
    }

    private Button button(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button found = button(((ViewGroup) view).getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }

    @Test
    public void invalidSnapshotsRecoverAndRepeatedCeCannotClearStack() throws Exception {
        Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir(), () -> true);
        File seed = File.createTempFile("recovery-test-", ".state", context.getNoBackupFilesDir());
        File corrupt = new File(context.getNoBackupFilesDir(), "calculator-state.corrupt");
        try {
            for (byte[] bad : new byte[][] {{1}, {0, 0, 0, 0}}) {
                Files.write(seed.toPath(), bad);
                store.save(Files.readAllBytes(seed.toPath()));
                MainActivity activity =
                        (MainActivity)
                                instrumentation.startActivitySync(
                                        new Intent(context, MainActivity.class)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                CalculatorSession session = StackSelectionTest.session(activity);
                View root = activity.getWindow().getDecorView();
                try {
                    await(
                            () ->
                                    !session.busy
                                            && session.checkpoint == null
                                            && session.error.contains("restore"));
                    assertNull(StoreTestSupport.checkpoint(store));
                    assertArrayEquals(bad, Files.readAllBytes(corrupt.toPath()));
                    instrumentation.runOnMainSync(() -> button(root, "9").performClick());
                    await(
                            () ->
                                    !session.busy
                                            && session.display.entry.equals("9")
                                            && button(root, "CE") != null);
                    instrumentation.runOnMainSync(() -> button(root, "Enter").performClick());
                    await(
                            () ->
                                    !session.busy
                                            && !session.display.editing
                                            && session.display.rows.size() == 1);
                    instrumentation.runOnMainSync(() -> button(root, "7").performClick());
                    await(
                            () ->
                                    !session.busy
                                            && session.display.entry.equals("7")
                                            && button(root, "CE") != null);
                    instrumentation.runOnMainSync(
                            () -> {
                                button(root, "CE").performClick();
                                button(root, "CE").performClick();
                            });
                    await(
                            () ->
                                    !session.busy
                                            && session.display.editing
                                            && session.display.entry.isEmpty());
                    assertEquals(java.util.Arrays.asList("9"), session.display.rows);
                    CalculationSnapshot beforePaste = session.checkpoint;
                    instrumentation.runOnMainSync(
                            () -> {
                                ClipboardManager clipboard =
                                        (ClipboardManager)
                                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                                char[] large = new char[4097];
                                java.util.Arrays.fill(large, '1');
                                clipboard.setPrimaryClip(
                                        ClipData.newPlainText("too long", new String(large)));
                                button(root, "Enter").performLongClick();
                                assertFalse(session.busy);
                                assertEquals(beforePaste, session.checkpoint);
                            });
                    instrumentation.runOnMainSync(() -> button(root, "Enter").performClick());
                    await(
                            () ->
                                    !session.busy
                                            && !session.display.editing
                                            && button(root, "AC") != null);
                    instrumentation.runOnMainSync(() -> button(root, "AC").performClick());
                    await(() -> !session.busy && session.display.rows.isEmpty());
                } finally {
                    StackSelectionTest.finish(activity);
                    store.clear();
                    corrupt.delete();
                }
            }
        } finally {
            seed.delete();
        }
    }
}
