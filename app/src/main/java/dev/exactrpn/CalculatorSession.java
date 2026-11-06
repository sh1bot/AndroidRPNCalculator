// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Retained calculation controller. Serial worker owns persistence; UI owns row selection. */
final class CalculatorSession {
    volatile String checkpoint;
    private RemoteCalculator remote;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    volatile Display display;
    volatile String error = "";
    private final Deque<Command> pending = new ArrayDeque<>();
    final SortedSet<Integer> selectedRows = new TreeSet<>();
    volatile boolean busy, cancelRequested, background;
    private CalculatorStore store;
    final SessionPolicy policy;
    private final String workspace;
    private File temporaryDirectory;
    private volatile boolean restorePending;
    boolean tauConstant;
    private volatile boolean loaded;
    boolean scientificExpanded, inverse;
    volatile long busySince;
    private volatile Thread calculatingThread;
    private ValueFormatter.Notation notation;
    private NumberMode numberMode;
    private FractionMode fractionMode;

    CalculatorSession(
            ValueFormatter.Notation n,
            RpnCalculator.AngleUnit angle,
            NumberMode numberMode,
            FractionMode fractionMode) {
        this(n, angle, numberMode, fractionMode, false);
    }

    CalculatorSession(
            ValueFormatter.Notation n,
            RpnCalculator.AngleUnit angle,
            NumberMode numberMode,
            FractionMode fractionMode,
            boolean temporary) {
        policy = new SessionPolicy(temporary);
        workspace = temporary ? UUID.randomUUID().toString() : "";
        notation = n;
        this.numberMode = numberMode;
        this.fractionMode = fractionMode;
        display = new Display(n, angle, numberMode, fractionMode);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile String localeTag;
    private volatile android.content.Context context;

    void attach(android.content.Context context) {
        this.context = context.getApplicationContext();
        localeTag = context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        if (store == null) {
            File directory;
            if (policy.temporary) {
                directory = new File(context.getCacheDir(), "locked-calculations/" + workspace);
                if (!directory.isDirectory() && !directory.mkdirs())
                    throw new IllegalStateException("Could not create temporary workspace");
                temporaryDirectory = directory;
            } else directory = context.getNoBackupFilesDir();
            store = new CalculatorStore(directory, () -> policy.temporary || canUseSavedState());
            remote = new RemoteCalculator(context, policy.temporary);
        }
    }

    boolean canUseSavedState() {
        return !policy.temporary && policy.canUseSavedState(DeviceAccess.locked(context));
    }

    void onForeground() {
        if (restorePending && !busy && canUseSavedState()) {
            restorePending = false;
            enqueue(Command.parse(Command.RESTORE));
        }
    }

    void enqueue(Command command) {
        if (!policy.temporary && DeviceAccess.locked(context)) {
            if (command.is(Command.RESTORE)) restorePending = true;
            return;
        }
        boolean start;
        synchronized (this) {
            pending.addLast(command);
            start = !busy;
            if (start) {
                cancelRequested = false;
                busySince = SystemClock.elapsedRealtime();
                busy = true;
            }
        }
        if (start) worker.submit(this::drainKeys);
    }

    private void drainKeys() {
        while (true) {
            Command key;
            synchronized (this) {
                if (cancelRequested) pending.clear();
                if (pending.isEmpty()) {
                    calculatingThread = null;
                    busy = false;
                    Thread.interrupted();
                    key = null;
                } else {
                    key = pending.removeFirst();
                    calculatingThread = Thread.currentThread();
                }
            }
            if (key == null) {
                persistSession(true);
                return;
            }
            try {
                checkCancelled();
                if (!policy.temporary && DeviceAccess.locked(context)) {
                    if (key.is(Command.RESTORE)) restorePending = true;
                    synchronized (this) {
                        pending.clear();
                    }
                    continue;
                }
                if (key.is(Command.RESTORE) && !policy.temporary) {
                    try {
                        checkpoint = store.checkpoint();
                        store.cleanWorkFiles(checkpoint);
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Could not restore saved calculator", ex);
                    }
                }
                Bundle request = new Bundle();
                request.putString("key", key.wire);
                request.putString("workspace", workspace);
                request.putString("input", checkpoint);
                request.putString("locale", localeTag);
                request.putString("angle", display.angleUnit.name());
                request.putString("notation", notation.name());
                request.putInt("numberMode", numberMode.id);
                request.putInt("fractionMode", fractionMode.id);
                Bundle result = remote.evaluate(request);
                Display nextDisplay = new Display(result);
                publishResult(result, nextDisplay, key);
                persistSession(false);
            } catch (RuntimeException ex) {
                if (ex instanceof DeviceAccess.Locked
                        || (!policy.temporary
                                && (DeviceAccess.locked(context)
                                        || (restorePending && key.is(Command.RESTORE))))) {
                    if (key.is(Command.RESTORE)) restorePending = true;
                    error = context.getString(R.string.unlock_to_resume);
                } else {

                    if (key.is(Command.RESTORE)) {
                        checkpoint = null;
                        remote.cancel();
                        if (ex instanceof RemoteCalculator.InvalidCheckpoint) {
                            try {
                                store.quarantine();
                            } catch (java.io.IOException | DeviceAccess.Locked backupError) {
                                android.util.Log.e(
                                        "ExactRPN", "Could not quarantine checkpoint", backupError);
                            }
                        }
                    }
                    error =
                            cancelRequested || ex instanceof com.hp.creals.CR.AbortedException
                                    ? context.getString(R.string.calculation_cancelled)
                                    : key.is(Command.RESTORE)
                                            ? context.getString(R.string.restore_failed)
                                            : context.getString(
                                                    R.string.error_prefix,
                                                    ErrorMessages.describe(context, ex));
                }
                synchronized (this) {
                    pending.clear();
                }
            } finally {
                Thread.interrupted();
            }
        }
    }

    private void publishResult(Bundle result, Display nextDisplay, Command key) {
        Runnable accept =
                () -> {
                    synchronized (this) {
                        checkCancelled();
                        if (key.kind == Command.Kind.DROP_ROWS) {
                            SortedSet<Integer> removed = new TreeSet<>();
                            for (int index : key.rows()) removed.add(index);
                            SortedSet<Integer> remaining = new TreeSet<>();
                            for (int index : selectedRows)
                                if (!removed.contains(index))
                                    remaining.add(index - removed.headSet(index).size());
                            selectedRows.clear();
                            selectedRows.addAll(remaining);
                        }
                        checkpoint = result.getString("checkpoint");
                        notation = nextDisplay.notation;
                        numberMode = nextDisplay.numberMode;
                        fractionMode = nextDisplay.fractionMode;
                        display = nextDisplay;
                        error = "";
                        if (!key.is(Command.CHECKPOINT)) loaded = true;
                    }
                };
        if (key.kind != Command.Kind.DROP_ROWS) {
            accept.run();
            return;
        }
        // Publish selection remapping and the new rows together on the UI thread, before
        // another queued result can overtake this one. Failed/cancelled drops leave selection
        // intact.
        FutureTask<Void> commit = new FutureTask<>(accept, null);
        handler.post(commit);
        try {
            commit.get();
        } catch (InterruptedException ex) {
            commit.cancel(false);
            Thread.currentThread().interrupt();
            throw new com.hp.creals.CR.AbortedException();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException) throw (RuntimeException) ex.getCause();
            throw new IllegalStateException("Could not update stack selection", ex.getCause());
        }
    }

    private void persistSession(boolean idle) {
        // Temporary checkpoints are worker transport only, never a saved calculation.
        if (policy.temporary) {
            store.cleanWorkFiles(checkpoint);
            return;
        }
        if (!canUseSavedState()) return;
        // Called only by the serial worker, after a committed operation or when its queue drains.
        if (!loaded) {
            store.cleanWorkFiles(checkpoint);
            return;
        }
        try {
            if (idle && background && display.rows.isEmpty() && display.entry.isEmpty()) {
                checkpoint = null;
                store.clear();
            } else if (checkpoint != null) store.save(checkpoint);
            store.cleanWorkFiles(checkpoint);
        } catch (java.io.IOException | RuntimeException ex) {
            android.util.Log.e("ExactRPN", "Could not save calculator", ex);
            error = context.getString(R.string.save_failed);
        }
    }

    private void checkCancelled() {
        if (cancelRequested || Thread.currentThread().isInterrupted())
            throw new com.hp.creals.CR.AbortedException();
    }

    void pauseForLock() {
        if (!loaded) restorePending = true;
        cancel();
    }

    void cancel() {
        synchronized (this) {
            if (!busy) return;
            cancelRequested = true;
            pending.clear();
            remote.cancel();
            if (calculatingThread != null) calculatingThread.interrupt();
        }
    }

    boolean awaitClosed(long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }

    void close() {
        cancel();
        remote.close();
        if (policy.temporary)
            worker.submit(
                    () -> {
                        store.cleanWorkFiles(null);
                        if (temporaryDirectory != null) temporaryDirectory.delete();
                    });
        worker.shutdown();
    }
}
