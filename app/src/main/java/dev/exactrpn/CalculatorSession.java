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
    // This calculator is an entry editor only: its stack and expression map stay empty.
    private RpnCalculator editor;
    private Display arithmeticDisplay;
    private long editRevision;
    private boolean editorDirty;
    private final java.util.Map<NumberMode, NumberText.Profile> inputProfiles =
            new java.util.EnumMap<>(NumberMode.class);
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
    volatile boolean busy, cancelRequested, background, operationPending;
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
        inputProfiles.clear();
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
        if (!busy
                && Looper.myLooper() == Looper.getMainLooper()
                && editLocally(command, inputGeneration)) return;
        boolean start;
        synchronized (this) {
            pending.addLast(new PendingKey(command, inputGeneration, copy));
            if (Operation.find(command.wire) != null
                    || command.is("Factor")
                    || command.kind == Command.Kind.UNARY_ROWS
                    || command.kind == Command.Kind.SUM_ROWS
                    || command.kind == Command.Kind.PRODUCT_ROWS) operationPending = true;
            start = !busy;
            if (start) {
                cancelRequested = false;
                busySince = SystemClock.elapsedRealtime();
                busy = true;
            }
        }
        if (start) worker.submit(this::drainKeys);
    }

    private NumberText.Profile inputProfile(NumberMode mode) {
        return inputProfiles.computeIfAbsent(
                mode,
                m -> new LocaleNumbers(java.util.Locale.forLanguageTag(localeTag), m).profile());
    }

    private static boolean cursorCommand(String wire) {
        String direction = wire.startsWith("SELECT_") ? wire.substring(7) : wire;
        return direction.equals("LEFT")
                || direction.equals("RIGHT")
                || direction.equals("HOME")
                || direction.equals("END");
    }

    private boolean editLocally(Command key, long generation) {
        if (editor == null) editor = new RpnCalculator();
        boolean editing = editor.isEditing();
        String wire = key.wire;
        boolean local =
                key.isEntryUpdate()
                        || RpnCalculator.isEntryKey(wire)
                        || key.is("CE")
                        || key.is("CANCEL_ENTRY")
                        || key.is("HOLD_DECIMAL")
                        || editing
                                && (key.is("BACK")
                                        || key.is("DROP")
                                        || key.is("DELETE")
                                        || key.is("HOLD_DROP")
                                        || key.is("SMART_CLEAR")
                                        || key.is("±")
                                        || cursorCommand(wire))
                        || key.is(Command.UNDO) && editor.undoCount() > 0
                        || key.is(Command.REDO)
                                && (editor.redoCount() > 0 || editor.undoCount() > 0);
        if (editing && key.kind == Command.Kind.HOLD_DIGIT) local = true;
        if (key.kind == Command.Kind.PASTE_RAW) {
            long lines =
                    java.util.Arrays.stream(key.text.split("\\R", -1))
                            .filter(line -> !line.trim().isEmpty())
                            .count();
            local = lines < 2;
        }
        if (!local) return false;
        try {
            editor.setInputProfile(inputProfile(numberMode));
            key.apply(editor, inputProfile(NumberMode.LOCALE), inputProfile(numberMode));
            if (checkpoint == null) {
                try {
                    checkpoint =
                            new CalculationSnapshot(
                                    UUID.randomUUID().toString(), new RpnCalculator().save(), true);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            loaded = true;
            editorDirty = true;
            editRevision++;
            display = new Display(arithmeticDisplay == null ? display : arithmeticDisplay, editor);
            setError(generation, "");
        } catch (RuntimeException ex) {
            setError(
                    generation,
                    context.getString(R.string.error_prefix, ErrorMessages.describe(context, ex)));
        }
        return true;
    }

    private <T> T onMain(java.util.concurrent.Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else handler.post(task);
        try {
            return task.get();
        } catch (InterruptedException ex) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new com.hp.creals.CR.AbortedException();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException) throw (RuntimeException) ex.getCause();
            throw new IllegalStateException(ex.getCause());
        }
    }

    private static boolean displayOnly(Command key) {
        return key.is("FORMAT") || key.is("NUMBER_FORMAT") || key.is("FRACTION") || key.is("ANGLE");
    }

    private byte[] completedDraft() throws java.io.IOException {
        if (editor == null) return null;
        return editor.entryForCommit().save();
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
                    operationPending = false;
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
                if (!closing && background) saveForLifecycle();
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
                Command command = key;
                long revision = generation;
                if (onMain(() -> editLocally(command, revision))) continue;
                Bundle request = new Bundle();
                if (!key.is(Command.RESTORE)
                        && !displayOnly(key)
                        && key.kind != Command.Kind.COPY_ROWS)
                    request.putByteArray("entryDraft", onMain(this::completedDraft));
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
                if (background) saveForLifecycle();
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
                        boolean keepEdits =
                                editor != null
                                        && (displayOnly(key)
                                                || nextDisplay.editing
                                                        && editor.isEditing()
                                                        && nextDisplay.entry.equals(editor.entry())
                                                        && nextDisplay.raw == editor.rawEntry()
                                                        && nextDisplay.start
                                                                == editor.selectionStart()
                                                        && nextDisplay.end
                                                                == editor.selectionEnd());
                        if (!keepEdits) {
                            try {
                                editor = RpnCalculator.load(result.getByteArray("entryState"));
                            } catch (java.io.IOException ex) {
                                throw new IllegalStateException("Invalid entry response", ex);
                            }
                            editorDirty = editor.undoCount() > 0 || editor.redoCount() > 0;
                            editRevision++;
                        }
                        arithmeticDisplay = nextDisplay;
                        display = new Display(nextDisplay, editor);
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
        onMain(
                () -> {
                    accept.run();
                    return null;
                });
    }

    void saveForLifecycle() {
        onMain(
                () -> {
                    persistSession(inputGeneration);
                    return null;
                });
    }

    private void persistSession(long generation) {
        if (!canUseSavedState() || !loaded) return;
        // onStop must finish saving the last accepted state before returning. Arithmetic never
        // holds this lock; background completion and stop cannot reorder durable writes.
        synchronized (persistenceLock) {
            CalculationSnapshot accepted = checkpoint;
            if (accepted == null) return;
            try {
                String version = accepted.id + ":" + editRevision;
                boolean empty = display.rows.isEmpty() && display.entry.isEmpty();
                if (background && !busy && empty) {
                    store.clear();
                    synchronized (this) {
                        if (checkpoint == accepted) checkpoint = null;
                    }
                    persistedCheckpoint = null;
                    if (editor != null) editor.clearUndo();
                } else if (!version.equals(persistedCheckpoint)) {
                    store.save(
                            editorDirty
                                    ? RpnCalculator.saveWithEntry(accepted.bytes, editor)
                                    : accepted.bytes);
                    persistedCheckpoint = version;
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
