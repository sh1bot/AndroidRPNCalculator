// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.SortedSet;
import java.util.TreeSet;
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
    private boolean loaded;
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
            store = new CalculatorStore(context.getNoBackupFilesDir());
            remote = new RemoteCalculator(context);
        }
    }

    void enqueue(Command command) {
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
                if (key.is(Command.RESTORE)) {
                    try {
                        checkpoint = store.checkpoint();
                        store.cleanWorkFiles(checkpoint);
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Could not restore saved calculator", ex);
                    }
                }
                Bundle request = new Bundle();
                request.putString("key", key.wire);
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
                if (key.is(Command.RESTORE)) {
                    checkpoint = null;
                    remote.cancel();
                    if (ex instanceof RemoteCalculator.InvalidCheckpoint) {
                        try {
                            store.quarantine();
                        } catch (java.io.IOException backupError) {
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
        remote.close();
        worker.shutdown();
    }
}
