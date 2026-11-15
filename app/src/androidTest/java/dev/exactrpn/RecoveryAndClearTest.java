// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.UiTestSupport.await;
import static dev.exactrpn.UiTestSupport.findButton;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public final class RecoveryAndClearTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

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
                    await("Timed out waiting for recovery/entry UI",
                            () ->
                                    !session.busy
                                            && session.checkpoint == null
                                            && session.error.contains("restore"));
                    assertNull(StoreTestSupport.checkpoint(store));
                    assertArrayEquals(bad, Files.readAllBytes(corrupt.toPath()));
                    instrumentation.runOnMainSync(
                            () -> findButton(root, "9").performClick());
                    await("Timed out waiting for recovery/entry UI",
                            () ->
                                    !session.busy
                                            && session.display.entry.equals("9")
                                            && findButton(root, "CE") != null);
                    instrumentation.runOnMainSync(
                            () -> findButton(root, "Enter").performClick());
                    await("Timed out waiting for recovery/entry UI",
                            () ->
                                    !session.busy
                                            && !session.display.editing
                                            && session.display.rows.size() == 1);
                    instrumentation.runOnMainSync(
                            () -> findButton(root, "7").performClick());
                    await("Timed out waiting for recovery/entry UI",
                            () ->
                                    !session.busy
                                            && session.display.entry.equals("7")
                                            && findButton(root, "CE") != null);
                    instrumentation.runOnMainSync(
                            () -> {
                                findButton(root, "CE").performClick();
                                findButton(root, "CE").performClick();
                            });
                    await("Timed out waiting for recovery/entry UI",
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
                                findButton(root, "Enter").performLongClick();
                                assertFalse(session.busy);
                                assertEquals(beforePaste, session.checkpoint);
                            });
                    instrumentation.runOnMainSync(
                            () -> findButton(root, "Enter").performClick());
                    await("Timed out waiting for recovery/entry UI",
                            () ->
                                    !session.busy
                                            && !session.display.editing
                                            && findButton(root, "AC") != null);
                    instrumentation.runOnMainSync(
                            () -> findButton(root, "AC").performClick());
                    await(
                            "Timed out waiting for recovery/entry UI",
                            () -> !session.busy && session.display.rows.isEmpty());
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
