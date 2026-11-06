// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class LockScreenTest {
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
        fail("Timed out waiting for lock-screen test at " + new Throwable().getStackTrace()[1]);
    }

    private String shell(String command) throws Exception {
        try (InputStream input =
                new ParcelFileDescriptor.AutoCloseInputStream(
                        instrumentation.getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) out.write(bytes, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private LockedCalculatorActivity open(Context context) {
        // startActivitySync can wait indefinitely for a task/lock-screen transition.
        // Start asynchronously, then require a resumed, usable activity with a bounded wait.
        AtomicReference<LockedCalculatorActivity> shown = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () ->
                        context.startActivity(
                                new Intent(context, LockedCalculatorActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        await(
                () -> {
                    for (Activity activity :
                            ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(Stage.RESUMED))
                        if (activity instanceof LockedCalculatorActivity) {
                            shown.set((LockedCalculatorActivity) activity);
                            return true;
                        }
                    return false;
                });
        return shown.get();
    }

    private void enter(MainActivity activity, String key) {
        instrumentation.runOnMainSync(
                () -> activity.getWindow().getDecorView().findViewWithTag(key).performClick());
        CalculatorSession session = StackSelectionTest.session(activity);
        await(() -> !session.busy);
    }

    private File seed(Context context, CalculatorStore store) throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.paste("99");
        calculator.key("ENTER");
        File seed = File.createTempFile("locked-test-", ".state", context.getNoBackupFilesDir());
        Files.write(seed.toPath(), calculator.save());
        store.save(seed.getAbsolutePath());
        return seed;
    }

    private FileObserver watch(Context context, AtomicInteger accesses) {
        FileObserver observer =
                new FileObserver(
                        context.getNoBackupFilesDir().getAbsolutePath(), FileObserver.ALL_EVENTS) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null && path.startsWith("calculator-state"))
                            accesses.incrementAndGet();
                    }
                };
        observer.startWatching();
        return observer;
    }

    @Test
    public void temporaryActivityNeverRestoresOrChangesTheSavedCalculation() throws Exception {
        Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        File seed = seed(context, store), saved = new File(store.checkpoint());
        byte[] before = Files.readAllBytes(saved.toPath());
        AtomicInteger accesses = new AtomicInteger();
        FileObserver observer = watch(context, accesses);
        LockedCalculatorActivity activity = null;
        try {
            activity = open(context);
            CalculatorSession session = StackSelectionTest.session(activity);
            await(() -> !session.busy);
            assertTrue(session.policy.temporary);
            assertFalse(session.canUseSavedState());
            assertTrue(session.display.rows.isEmpty());
            enter(activity, "7");
            enter(activity, "ENTER");
            assertEquals(Arrays.asList("7"), session.display.rows);
            assertTrue(
                    new File(session.checkpoint)
                            .getCanonicalPath()
                            .startsWith(context.getCacheDir().getCanonicalPath() + File.separator));
            enter(activity, "CLEAR");
            enter(activity, "UNDO");
            assertEquals(Arrays.asList("7"), session.display.rows);
            File temporary = new File(session.checkpoint);
            StackSelectionTest.finish(activity);
            activity = null;
            assertFalse(temporary.exists());
            activity = open(context);
            CalculatorSession reopened = StackSelectionTest.session(activity);
            await(() -> !reopened.busy);
            assertTrue(reopened.display.rows.isEmpty());
            StackSelectionTest.finish(activity);
            activity = null;
            assertEquals("Temporary sessions must not touch saved files", 0, accesses.get());
        } finally {
            if (activity != null) StackSelectionTest.finish(activity);
            observer.stopWatching();
            assertArrayEquals(before, Files.readAllBytes(saved.toPath()));
            store.clear();
            store.cleanWorkFiles(null);
            seed.delete();
        }
    }

    @Test
    public void lockedWorkerRejectsAnyNormalCheckpointBeforeReadingIt() throws Exception {
        Context context = instrumentation.getTargetContext();
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        File seed = seed(context, store);
        String saved = store.checkpoint();
        AtomicInteger accesses = new AtomicInteger();
        FileObserver observer = watch(context, accesses);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        RemoteCalculator remote = new RemoteCalculator(context, true);
        try {
            Bundle request = new Bundle();
            request.putString("workspace", UUID.randomUUID().toString());
            request.putString("key", "RESTORE");
            request.putString("input", saved);
            request.putString("locale", "en-US");
            request.putString("angle", "RAD");
            request.putString("notation", "SCIENTIFIC");
            try {
                worker.submit(() -> remote.evaluate(request)).get(15, TimeUnit.SECONDS);
                fail("Saved checkpoint accepted by temporary worker");
            } catch (java.util.concurrent.ExecutionException ex) {
                assertTrue(ex.getCause() instanceof RemoteCalculator.UserFailure);
            }
            assertEquals(0, accesses.get());
        } finally {
            remote.close();
            worker.shutdownNow();
            observer.stopWatching();
            store.clear();
            store.cleanWorkFiles(null);
            seed.delete();
        }
    }

    @Test(timeout = 90000)
    public void secureLockThenUnlockKeepsTheSameTemporarySession() throws Exception {
        Context context = instrumentation.getTargetContext();
        KeyguardManager manager = context.getSystemService(KeyguardManager.class);
        assumeTrue(
                "Credential transition test is only for disposable emulators",
                Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"));
        assumeFalse("Do not alter an existing credential", manager.isDeviceSecure());
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        File seed = seed(context, store), saved = new File(store.checkpoint());
        byte[] before = Files.readAllBytes(saved.toPath());
        AtomicInteger accesses = new AtomicInteger();
        FileObserver observer = watch(context, accesses);
        LockedCalculatorActivity activity = null;
        boolean credential = false;
        try {
            shell("locksettings set-pin 1234");
            credential = true;
            await(manager::isDeviceSecure);
            // Wake from the same shell invocation, so the test process need not resume
            // itself after putting the emulator to sleep. The credential remains locked.
            shell("input keyevent KEYCODE_SLEEP; input keyevent KEYCODE_WAKEUP");
            await(manager::isDeviceLocked);
            activity = open(context);
            CalculatorSession session = StackSelectionTest.session(activity);
            await(() -> !session.busy);
            assertTrue(DeviceAccess.locked(context));
            assertTrue(session.display.rows.isEmpty());
            CalculatorStore guarded =
                    new CalculatorStore(
                            context.getNoBackupFilesDir(), () -> !DeviceAccess.locked(context));
            try {
                guarded.checkpoint();
                fail("Saved state read allowed while locked");
            } catch (DeviceAccess.Locked expected) {
            }
            RemoteCalculator normalWorker = new RemoteCalculator(context);
            ExecutorService blockedWorker = Executors.newSingleThreadExecutor();
            try {
                Bundle blocked = new Bundle();
                blocked.putString("key", "RESTORE");
                blocked.putString("input", saved.getAbsolutePath());
                try {
                    blockedWorker
                            .submit(() -> normalWorker.evaluate(blocked))
                            .get(15, TimeUnit.SECONDS);
                    fail("Normal worker read allowed while locked");
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertTrue(ex.getCause() instanceof DeviceAccess.Locked);
                }
            } finally {
                normalWorker.close();
                blockedWorker.shutdownNow();
            }
            enter(activity, "7");
            enter(activity, "ENTER");
            assertEquals(Arrays.asList("7"), session.display.rows);
            assertFalse(session.canUseSavedState());
            shell("locksettings clear --old 1234");
            credential = false;
            shell("wm dismiss-keyguard");
            await(() -> !DeviceAccess.locked(context));
            assertFalse(
                    "Unlock must not promote the temporary session", session.canUseSavedState());
            LockedCalculatorActivity shown = activity;
            instrumentation.runOnMainSync(
                    () ->
                            assertEquals(
                                    "TEMP",
                                    ((TextView)
                                                    shown.getWindow()
                                                            .getDecorView()
                                                            .findViewWithTag("calculation-status"))
                                            .getText()
                                            .toString()));
            enter(activity, "2");
            enter(activity, "ENTER");
            assertEquals(Arrays.asList("7", "2"), session.display.rows);
            StackSelectionTest.finish(activity);
            activity = null;
            assertEquals("Neither lock nor unlock may touch saved files", 0, accesses.get());
        } finally {
            if (credential) shell("locksettings clear --old 1234");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            if (activity != null) StackSelectionTest.finish(activity);
            observer.stopWatching();
            assertArrayEquals(before, Files.readAllBytes(saved.toPath()));
            store.clear();
            store.cleanWorkFiles(null);
            seed.delete();
        }
    }

    @Test
    public void tileTargetsAndManifestKeepTheNormalAndTemporaryTasksSeparate() throws Exception {
        Context context = instrumentation.getTargetContext();
        PackageManager manager = context.getPackageManager();
        assertEquals(
                LockedCalculatorActivity.class.getName(),
                CalculatorTileService.launchIntent(context, true).getComponent().getClassName());
        assertEquals(
                MainActivity.class.getName(),
                CalculatorTileService.launchIntent(context, false).getComponent().getClassName());
        ServiceInfo tile =
                manager.getServiceInfo(new ComponentName(context, CalculatorTileService.class), 0);
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.permission);
        ServiceInfo normal =
                manager.getServiceInfo(new ComponentName(context, CalculationService.class), 0);
        ServiceInfo temporary =
                manager.getServiceInfo(
                        new ComponentName(context, LockedCalculationService.class), 0);
        assertFalse(normal.processName.equals(temporary.processName));
        ActivityInfo activity =
                manager.getActivityInfo(
                        new ComponentName(context, LockedCalculatorActivity.class), 0);
        assertFalse(activity.exported);
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activity.launchMode);
    }
}
