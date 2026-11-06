// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class StackSelectionTest {
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
        fail("Timed out waiting for stack UI at " + new Throwable().getStackTrace()[1]);
    }

    static CalculatorSession session(MainActivity activity) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("session");
            field.setAccessible(true);
            return (CalculatorSession) field.get(activity);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    static void finish(MainActivity activity) throws InterruptedException {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        CalculatorSession session = session(activity);
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
        assertTrue(
                "Activity worker must finish before the next test",
                session.awaitClosed(10, TimeUnit.SECONDS));
    }

    private View tagged(MainActivity activity, String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
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

    private void choose(String text) {
        onView(withText(text)).inRoot(isPlatformPopup()).perform(click());
    }

    @Test
    public void selectedSumAppendsTheResultAndKeepsSelection() throws Exception {
        Context context = instrumentation.getTargetContext();
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context, LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = session(activity);
        try {
            await(() -> !session.busy);
            for (String key : new String[] {"1", "ENTER", "2", "ENTER", "3", "ENTER"}) {
                instrumentation.runOnMainSync(() -> session.enqueue(Command.parse(key)));
                await(() -> !session.busy);
            }
            await(() -> tagged(activity, "stack-label:2") != null);
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-label:0").performLongClick());
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-checkbox:2").performClick());
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:2").performLongClick());
            choose("Σ");
            await(() -> !session.busy && tagged(activity, "stack-value:3") != null);
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                java.util.Arrays.asList("1", "2", "3", "4"), session.display.rows);
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0, 2)),
                                session.selectedRows);
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:0")).isChecked());
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:2")).isChecked());
                        assertFalse(((CheckBox) tagged(activity, "stack-checkbox:3")).isChecked());
                    });
            RpnCalculator restored =
                    RpnCalculator.load(Files.readAllBytes(new File(session.checkpoint).toPath()));
            assertEquals("4", restored.values().get(3).bigIntegerValue().toString());
            restored.key("UNDO");
            assertEquals(3, restored.values().size());
        } finally {
            finish(activity);
        }
    }

    @Test
    public void pickCopiesOnlyTheTargetRowsAndKeepsOriginalSelection() throws Exception {
        Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        File seed = File.createTempFile("pick-test-", ".state", context.getNoBackupFilesDir());
        RpnCalculator calculator = new RpnCalculator();
        for (String key : new String[] {"1", "ENTER", "2", "ENTER", "3", "ENTER"})
            calculator.key(key);
        Files.write(seed.toPath(), calculator.save());
        store.save(seed.getAbsolutePath());
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = session(activity);
        try {
            await(() -> !session.busy && tagged(activity, "stack-label:2") != null);
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-label:0").performLongClick());
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-checkbox:2").performClick());
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:1").performLongClick());
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                View.INVISIBLE,
                                tagged(activity, "stack-checkbox:0").getVisibility());
                        assertFalse(tagged(activity, "stack-row:0").isSelected());
                        assertTrue(tagged(activity, "stack-row:1").isSelected());
                        assertFalse(tagged(activity, "stack-row:2").isSelected());
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0, 2)),
                                session.selectedRows);
                    });
            androidx.test.espresso.Espresso.pressBack();
            await(() -> tagged(activity, "stack-checkbox:0").getVisibility() == View.VISIBLE);
            instrumentation.runOnMainSync(
                    () -> {
                        assertTrue(tagged(activity, "stack-row:0").isSelected());
                        assertFalse(tagged(activity, "stack-row:1").isSelected());
                        assertTrue(tagged(activity, "stack-row:2").isSelected());
                        tagged(activity, "stack-value:1").performLongClick();
                    });
            choose("Pick");
            await(() -> !session.busy && tagged(activity, "stack-value:3") != null);
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                java.util.Arrays.asList("1", "2", "3", "2"), session.display.rows);
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0, 2)),
                                session.selectedRows);
                        assertFalse(((CheckBox) tagged(activity, "stack-checkbox:3")).isChecked());
                        tagged(activity, "stack-value:2").performLongClick();
                    });
            choose("Pick");
            await(
                    () ->
                            !session.busy
                                    && tagged(activity, "stack-value:5") != null
                                    && button(activity.getWindow().getDecorView(), "Undo") != null);
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                java.util.Arrays.asList("1", "2", "3", "2", "1", "3"),
                                session.display.rows);
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0, 2)),
                                session.selectedRows);
                        assertFalse(((CheckBox) tagged(activity, "stack-checkbox:4")).isChecked());
                        assertFalse(((CheckBox) tagged(activity, "stack-checkbox:5")).isChecked());
                        button(activity.getWindow().getDecorView(), "Undo").performClick();
                    });
            await(
                    () ->
                            !session.busy
                                    && tagged(activity, "stack-value:5") == null
                                    && tagged(activity, "stack-value:3") != null);
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                java.util.Arrays.asList("1", "2", "3", "2"), session.display.rows);
                        assertNull(tagged(activity, "stack-checkbox:0"));
                    });
        } finally {
            finish(activity);
            store.clear();
            store.cleanWorkFiles(null);
            seed.delete();
        }
    }

    @Test
    public void selectionCopyDropAndUndoOperateOnWholeSelection() throws Exception {
        Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        File seed = File.createTempFile("selection-test-", ".state", context.getNoBackupFilesDir());
        RpnCalculator calculator = new RpnCalculator();
        for (String key : new String[] {"1", "ENTER", "2", "ENTER", "3", "ENTER"})
            calculator.key(key);
        Files.write(seed.toPath(), calculator.save());
        store.save(seed.getAbsolutePath());
        MainActivity activity =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalculatorSession session = session(activity);
        try {
            await(() -> !session.busy && tagged(activity, "stack-label:2") != null);
            // A mode change must not invalidate listeners on otherwise unchanged stack rows.
            instrumentation.runOnMainSync(
                    () -> {
                        Button angle = null;
                        for (String label : new String[] {"RAD", "DEG", "GRAD"}) {
                            angle = button(activity.getWindow().getDecorView(), label);
                            if (angle != null) break;
                        }
                        assertNotNull(angle);
                        angle.performClick();
                    });
            await(() -> !session.busy);
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-label:0").performLongClick());
            await(() -> tagged(activity, "stack-checkbox:2") != null);
            instrumentation.runOnMainSync(
                    () -> {
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:0")).isChecked());
                        assertFalse(((CheckBox) tagged(activity, "stack-checkbox:1")).isChecked());
                        tagged(activity, "stack-checkbox:2").performClick();
                    });
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            // Unselected row acts alone, without disturbing the existing selection.
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:1").performLongClick());
            choose("Copy");
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "2"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:2").performLongClick());
            choose("Copy");
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "1\n3"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:1").performLongClick());
            choose("Drop");
            await(
                    () ->
                            !session.busy
                                    && tagged(activity, "stack-value:2") == null
                                    && tagged(activity, "stack-value:1") != null
                                    && ((TextView) tagged(activity, "stack-value:1"))
                                            .getText()
                                            .toString()
                                            .equals("3"));
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0, 1)),
                                session.selectedRows);
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:0")).isChecked());
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:1")).isChecked());
                        tagged(activity, "stack-value:1").performLongClick();
                    });
            choose("Copy");
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "1\n3"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:1").performLongClick());
            choose("Drop");
            await(
                    () ->
                            !session.busy
                                    && tagged(activity, "stack-value:0") == null
                                    && button(activity.getWindow().getDecorView(), "Undo") != null);
            instrumentation.runOnMainSync(
                    () -> button(activity.getWindow().getDecorView(), "Undo").performClick());
            await(() -> !session.busy && tagged(activity, "stack-value:1") != null);
            // Deleting a row before the sole selected value must not select its replacement by
            // index.
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-label:1").performLongClick());
            instrumentation.runOnMainSync(
                    () -> tagged(activity, "stack-value:0").performLongClick());
            choose("Drop");
            await(
                    () ->
                            !session.busy
                                    && tagged(activity, "stack-value:1") == null
                                    && tagged(activity, "stack-value:0") != null
                                    && ((TextView) tagged(activity, "stack-value:0"))
                                            .getText()
                                            .toString()
                                            .equals("3"));
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(
                                new java.util.TreeSet<>(java.util.Arrays.asList(0)),
                                session.selectedRows);
                        assertTrue(((CheckBox) tagged(activity, "stack-checkbox:0")).isChecked());
                        tagged(activity, "stack-value:0").performLongClick();
                    });
            choose("Copy");
            await(
                    () ->
                            clipboard.getPrimaryClip() != null
                                    && "3"
                                            .contentEquals(
                                                    clipboard
                                                            .getPrimaryClip()
                                                            .getItemAt(0)
                                                            .getText()));
            instrumentation.runOnMainSync(
                    () -> {
                        activity.onBackPressed();
                        assertNull(tagged(activity, "stack-checkbox:0"));
                        assertFalse(activity.isFinishing());
                        tagged(activity, "stack-value:0").performLongClick();
                    });
            choose("Drop");
            await(() -> !session.busy && tagged(activity, "stack-value:0") == null);
        } finally {
            finish(activity);
            store.clear();
            store.cleanWorkFiles(null);
            seed.delete();
        }
    }
}
