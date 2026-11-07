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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Retained controller. Accepted snapshots remain safe to save during unfinished arithmetic. */
final class CalculatorSession {
    volatile CalculationSnapshot checkpoint;
    private RemoteCalculator remote;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closing;
    private String persistedCheckpoint;
    private final Object persistenceLock = new Object();
    volatile Display display;
    volatile String error = "";
    volatile long errorRevision;
    // A keypress dismisses notices from every earlier input, including unfinished requests.
    private long inputGeneration;

    private static final class PendingKey {
        final Command command;
        final long generation;
        final java.util.function.Consumer<String> copy;

        PendingKey(Command command, long generation, java.util.function.Consumer<String> copy) {
            this.command = command;
            this.generation = generation;
            this.copy = copy;
        }
    }

    private final Deque<PendingKey> pending = new ArrayDeque<>();
    final SortedSet<Integer> selectedRows = new TreeSet<>();
    volatile boolean busy, cancelRequested, background;
    private CalculatorStore store;
    final SessionPolicy policy;
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
        if (remote == null) {
            if (!policy.temporary) {
                store = new CalculatorStore(context.getNoBackupFilesDir(), this::canUseSavedState);
                store.removeLegacyWorkFiles();
            }
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
        enqueue(command, null);
    }

    void copyRows(int[] rows, java.util.function.Consumer<String> copy) {
        String indices =
                java.util.Arrays.stream(rows)
                        .mapToObj(Integer::toString)
                        .collect(java.util.stream.Collectors.joining(","));
        enqueue(Command.parse("COPY_ROWS:" + indices), copy);
    }

    private void enqueue(Command command, java.util.function.Consumer<String> copy) {
        if (closing) return;
        if (!policy.temporary && DeviceAccess.locked(context)) {
            if (command.is(Command.RESTORE)) restorePending = true;
            return;
        }
        if (command.is(Command.CHECKPOINT)) {
            saveForLifecycle();
            return;
        }
        boolean start;
        synchronized (this) {
            pending.addLast(new PendingKey(command, inputGeneration, copy));
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
            long generation;
            java.util.function.Consumer<String> copy;
            synchronized (this) {
                if (cancelRequested) pending.clear();
                if (pending.isEmpty()) {
                    calculatingThread = null;
                    busy = false;
                    Thread.interrupted();
                    key = null;
                    generation = inputGeneration;
                    copy = null;
                } else {
                    PendingKey input = pending.removeFirst();
                    key = input.command;
                    generation = input.generation;
                    copy = input.copy;
                    calculatingThread = Thread.currentThread();
                }
            }
            if (key == null) {
                if (!closing && background) persistSession(generation);
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
                        byte[] saved = store.load();
                        checkpoint =
                                saved == null
                                        ? null
                                        : new CalculationSnapshot(
                                                UUID.randomUUID().toString(), saved, false);
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Could not restore saved calculator", ex);
                    }
                }
                Bundle request = new Bundle();
                request.putString("key", key.wire);
                CalculationSnapshot input = checkpoint;
                if (input != null) input.input(request);
                request.putString("locale", localeTag);
                request.putString("angle", display.angleUnit.name());
                request.putString("notation", notation.name());
                request.putInt("numberMode", numberMode.id);
                request.putInt("fractionMode", fractionMode.id);
                Bundle result = remote.evaluate(request);
                if (key.kind == Command.Kind.COPY_ROWS) {
                    if (copy != null) handler.post(() -> copy.accept(result.getString("copyText")));
                    continue;
                }
                Display nextDisplay = new Display(result);
                publishResult(result, nextDisplay, key, generation);
                if (background) persistSession(generation);
            } catch (RuntimeException ex) {
                if (ex instanceof DeviceAccess.Locked
                        || (!policy.temporary
                                && (DeviceAccess.locked(context)
                                        || (restorePending && key.is(Command.RESTORE))))) {
                    if (key.is(Command.RESTORE)) restorePending = true;
                    setError(generation, context.getString(R.string.unlock_to_resume));
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
                    setError(
                            generation,
                            cancelRequested || ex instanceof com.hp.creals.CR.AbortedException
                                    ? context.getString(R.string.calculation_cancelled)
                                    : key.is(Command.RESTORE)
                                            ? context.getString(R.string.restore_failed)
                                            : context.getString(
                                                    R.string.error_prefix,
                                                    ErrorMessages.describe(context, ex)));
                }
                synchronized (this) {
                    pending.removeIf(input -> input.generation <= generation);
                }
            } finally {
                Thread.interrupted();
            }
        }
    }

    private void publishResult(Bundle result, Display nextDisplay, Command key, long generation) {
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
                        checkpoint =
                                new CalculationSnapshot(
                                        result.getString("checkpoint"),
                                        result.getByteArray("state"),
                                        nextDisplay.rows.isEmpty() && nextDisplay.entry.isEmpty());
                        notation = nextDisplay.notation;
                        numberMode = nextDisplay.numberMode;
                        fractionMode = nextDisplay.fractionMode;
                        display = nextDisplay;
                        String operationError = result.getString("operationError", "");
                        if (!key.is(Command.CHECKPOINT))
                            setError(
                                    generation,
                                    operationError.isEmpty()
                                            ? ""
                                            : context.getString(
                                                    R.string.error_prefix, operationError));
                        if (!operationError.isEmpty())
                            pending.removeIf(input -> input.generation <= generation);
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

    void saveForLifecycle() {
        persistSession(inputGeneration);
    }

    private void persistSession(long generation) {
        if (!canUseSavedState() || !loaded) return;
        // onStop must finish saving the last accepted state before returning. Arithmetic never
        // holds this lock; background completion and stop cannot reorder durable writes.
        synchronized (persistenceLock) {
            CalculationSnapshot accepted = checkpoint;
            if (accepted == null) return;
            try {
                if (background && !busy && accepted.empty) {
                    store.clear();
                    synchronized (this) {
                        if (checkpoint == accepted) checkpoint = null;
                    }
                    persistedCheckpoint = null;
                } else if (!accepted.id.equals(persistedCheckpoint)) {
                    store.save(accepted.bytes);
                    persistedCheckpoint = accepted.id;
                }
            } catch (java.io.IOException | RuntimeException ex) {
                android.util.Log.e("ExactRPN", "Could not save calculator", ex);
                setError(generation, context.getString(R.string.save_failed));
            }
        }
    }

    synchronized void clearError() {
        inputGeneration++;
        setError(inputGeneration, "");
    }

    private synchronized void setError(long generation, String message) {
        // Completion order must never let an older result reverse a newer dismissal.
        if (generation != inputGeneration) return;
        error = message;
        errorRevision++;
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
        closing = true;
        cancel();
        remote.close();
        saveForLifecycle();
        worker.shutdown();
    }
}
