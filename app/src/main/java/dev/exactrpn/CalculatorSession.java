// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

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
    // Entry editing stays on the UI thread.
    private EntryEditor editor;
    private Display arithmeticDisplay;
    private long editRevision;
    private boolean editorDirty, localBranch;
    private final java.util.Map<NumberMode, NumberText.Profile> inputProfiles =
            new java.util.EnumMap<>(NumberMode.class);
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
    final boolean temporary;
    private volatile boolean restorePending;
    boolean tauConstant;
    private volatile boolean loaded;
    boolean scientificExpanded, inverse;
    volatile long busySince;
    private volatile Thread calculatingThread;
    private volatile Runnable listener;

    CalculatorSession(
            ValueFormatter.Notation n,
            RpnCalculator.AngleUnit angle,
            NumberMode numberMode,
            FractionMode fractionMode,
            RadixMode radixMode,
            boolean temporary) {
        this.temporary = temporary;
        display = new Display(n, angle, numberMode, fractionMode, radixMode);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile String localeTag;
    private volatile android.content.Context context;

    void attach(android.content.Context context) {
        this.context = context.getApplicationContext();
        inputProfiles.clear();
        localeTag = context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        if (remote == null) {
            if (!temporary) {
                store = new CalculatorStore(context.getNoBackupFilesDir(), this::canUseSavedState);
            }
            remote = new RemoteCalculator(context, temporary);
        }
    }

    void setListener(Runnable listener) {
        this.listener = listener;
    }

    private void changed() {
        Runnable current = listener;
        if (current != null) handler.post(current);
    }

    boolean canUseSavedState() {
        return !temporary && !DeviceAccess.locked(context);
    }

    void onForeground() {
        if (restorePending && !busy && canUseSavedState()) {
            restorePending = false;
            enqueue(Command.key(Command.RESTORE));
        }
    }

    InteractionMode mode(Display snapshot) {
        return snapshot.editing
                ? InteractionMode.ENTRY
                : selectedRows.isEmpty() ? InteractionMode.STACK : InteractionMode.SELECT;
    }

    void enqueue(Command command) {
        enqueue(command, null);
    }

    void copyRows(int[] rows, java.util.function.Consumer<String> copy) {
        enqueue(Command.rows(Command.Kind.READ_VALUES, rows), copy);
    }

    private void enqueue(Command command, java.util.function.Consumer<String> copy) {
        if (closing) return;
        if (!temporary && DeviceAccess.locked(context)) {
            if (command.is(Command.RESTORE)) restorePending = true;
            return;
        }
        if (!busy
                && Looper.myLooper() == Looper.getMainLooper()
                && (editLocally(command, inputGeneration)
                        || changeFormat(command, inputGeneration))) return;
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
        if (start) changed();
        if (start) worker.submit(this::drainKeys);
    }

    private NumberText.Profile inputProfile(NumberMode mode) {
        return inputProfiles.computeIfAbsent(
                mode,
                m ->
                        new LocaleNumbers(
                                        java.util.Locale.forLanguageTag(localeTag),
                                        m,
                                        display.radixMode)
                                .profile());
    }

    private boolean editLocally(Command key, long generation) {
        if (editor == null) editor = new EntryEditor();
        boolean editing = editor.isEditing();
        String wire = key.wire;
        boolean local =
                key.isEntryUpdate()
                        || Command.cursorDirection(wire) != null
                        || EntryEditor.isEntryKey(wire)
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
                                        || Command.cursorDirection(wire) != null)
                        || key.is(Command.UNDO) && editor.undoCount() > 0
                        || key.is(Command.REDO) && editor.redoCount() > 0;
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
            String before = editor.entry();
            boolean wasEditing = editor.isEditing(), wasRaw = editor.rawEntry();
            int beforeStart = editor.selectionStart(), beforeEnd = editor.selectionEnd();
            int undos = editor.undoCount(), redos = editor.redoCount();
            editor.setInputProfile(inputProfile(display.numberMode));
            key.apply(editor, inputProfile(NumberMode.LOCALE), inputProfile(display.numberMode));
            boolean changed =
                    !before.equals(editor.entry())
                            || wasEditing != editor.isEditing()
                            || wasRaw != editor.rawEntry()
                            || beforeStart != editor.selectionStart()
                            || beforeEnd != editor.selectionEnd()
                            || undos != editor.undoCount()
                            || redos != editor.redoCount();
            if (!changed) {
                setError(generation, "");
                return true;
            }
            if (checkpoint == null) {
                try {
                    checkpoint =
                            new CalculationSnapshot(
                                    UUID.randomUUID().toString(), new RpnCalculator().save());
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            if (!key.is(Command.UNDO) && !key.is(Command.REDO))
                localBranch = true;
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

    private byte[] completedDraft(Command key) throws java.io.IOException {
        if (editor == null) return null;
        return (key.is(Command.UNDO) || key.is(Command.REDO) ? editor : editor.prepared()).save();
    }

    private synchronized PendingKey nextKey() {
        if (cancelRequested) pending.clear();
        if (!pending.isEmpty()) {
            calculatingThread = Thread.currentThread();
            return pending.removeFirst();
        }
        calculatingThread = null;
        busy = operationPending = false;
        Thread.interrupted();
        changed();
        return null;
    }

    private void drainKeys() {
        PendingKey input;
        while ((input = nextKey()) != null) processKey(input);
        if (!closing && background) saveForLifecycle();
    }

    private void processKey(PendingKey input) {
        Command key = input.command;
        long generation = input.generation;
        try {
            checkCancelled();
            if (!temporary && DeviceAccess.locked(context)) {
                if (key.is(Command.RESTORE)) restorePending = true;
                synchronized (this) {
                    pending.clear();
                }
                return;
            }
            if (onMain(() -> editLocally(key, generation))) return;
            if (changeRadix(key, generation)) return;
            if (changeFormat(key, generation)) return;
            Display settings = display;
            DisplayResult result = remote.evaluate(request(key));
            if (key.kind == Command.Kind.READ_VALUES) {
                java.util.StringJoiner text = new java.util.StringJoiner("\n");
                for (NumericValue value : result.values)
                    text.add(
                            ValueFormatter.clipboard(
                                    value, settings.notation, settings.fractionMode));
                CalculationLimits.require(text.length(), CalculationLimits.TRANSFER_BYTES);
                if (input.copy != null) handler.post(() -> input.copy.accept(text.toString()));
                return;
            }
            EntryEditor incoming;
            try {
                incoming = EntryEditor.load(result.entryState);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Invalid entry response", ex);
            }
            publishResult(
                    result, new Display(result, incoming, settings), incoming, key, generation);
            if (key.is(Command.RESTORE) && !temporary) acknowledgeRestore();
            if (background) saveForLifecycle();
        } catch (RuntimeException ex) {
            handleFailure(key, generation, ex);
        } finally {
            Thread.interrupted();
        }
    }

    private boolean changeFormat(Command key, long generation) {
        if (!key.is("FORMAT")
                && !key.is("NUMBER_FORMAT")
                && !key.is("FRACTION")
                && !key.is("ANGLE")) return false;
        Display current = display;
        ValueFormatter.Notation notation =
                key.is("FORMAT")
                        ? current.notation == ValueFormatter.Notation.SCIENTIFIC
                                ? ValueFormatter.Notation.ENGINEERING
                                : ValueFormatter.Notation.SCIENTIFIC
                        : current.notation;
        EntryEditor entry = onMain(() -> editor == null ? new EntryEditor() : editor.copy());
        Display next =
                new Display(
                        current,
                        key.is("ANGLE")
                                ? RpnCalculator.AngleUnit.values()[
                                        (current.angleUnit.ordinal() + 1)
                                                % RpnCalculator.AngleUnit.values().length]
                                : current.angleUnit,
                        notation,
                        key.is("NUMBER_FORMAT") ? current.numberMode.next() : current.numberMode,
                        key.is("FRACTION") ? current.fractionMode.next() : current.fractionMode,
                        entry);
        onMain(
                () -> {
                    arithmeticDisplay = next;
                    display = new Display(next, editor == null ? entry : editor);
                    setError(generation, "");
                    return null;
                });
        return true;
    }

    private boolean changeRadix(Command key, long generation) {
        if (!key.is("RADIX")) return false;
        Display current = display;
        if (current.editing) {
            setError(
                    generation,
                    context.getString(
                            R.string.error_prefix, context.getString(R.string.finish_entry_base)));
            return true;
        }
        RadixMode radix = current.radixMode.next();
        EntryEditor entry = onMain(() -> editor == null ? new EntryEditor() : editor.copy());
        Display next;
        if (current.values.isEmpty()) {
            next =
                    new Display(
                            current,
                            current.notation,
                            current.numberMode,
                            current.fractionMode,
                            radix,
                            entry);
        } else {
            int[] rows = new int[current.values.size()];
            for (int i = 0; i < rows.length; i++) rows[i] = i;
            CalculationRequest request = request(Command.rows(Command.Kind.READ_VALUES, rows));
            request.numbers = NumericRequest.display(radix);
            DisplayResult result = remote.evaluate(request);
            Display settings =
                    new Display(
                            current,
                            current.notation,
                            current.numberMode,
                            current.fractionMode,
                            radix,
                            entry);
            next = new Display(result, entry, settings);
        }
        Display accepted = next;
        onMain(
                () -> {
                    inputProfiles.clear();
                    arithmeticDisplay = accepted;
                    display = new Display(accepted, editor == null ? entry : editor);
                    setError(generation, "");
                    return null;
                });
        return true;
    }

    private CalculationRequest request(Command key) {
        Command action =
                key.kind == Command.Kind.PASTE_RAW
                        ? Command.pushValues(
                                ClipboardNumbers.parse(key.text, inputProfile(NumberMode.LOCALE)))
                        : key.forEntry(editor != null && editor.isEditing());
        CalculationRequest request = new CalculationRequest(action);
        request.numbers =
                key.kind == Command.Kind.READ_VALUES
                        ? NumericRequest.precise(display.radixMode)
                        : NumericRequest.display(display.radixMode);
        if (!key.is(Command.RESTORE)
                && !key.isDisplayOnly()
                && key.kind != Command.Kind.READ_VALUES) {
            request.entryDraft = onMain(() -> completedDraft(key));
            request.entryBranch = localBranch;
        }
        CalculationSnapshot input = checkpoint;
        if (key.is(Command.RESTORE) && !temporary) {
            try {
                byte[] saved = store.beginRestore();
                input =
                        saved == null
                                ? null
                                : new CalculationSnapshot(UUID.randomUUID().toString(), saved);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Could not restore saved calculator", ex);
            }
        }
        if (input != null) input.input(request);
        request.angle = display.angleUnit;
        return request;
    }

    private void acknowledgeRestore() {
        // Keep the marker through initial layout too, so early UI crashes count as failed starts.
        handler.postDelayed(
                () -> {
                    if (!closing && loaded && canUseSavedState()) endRestore(false);
                },
                2000);
    }

    private void endRestore(boolean quarantine) {
        try {
            if (quarantine) store.quarantine();
            else store.finishRestore();
        } catch (java.io.IOException | DeviceAccess.Locked ex) {
            android.util.Log.e("ExactRPN", "Could not finish restore bookkeeping", ex);
        }
    }

    private void handleFailure(Command key, long generation, RuntimeException ex) {
        boolean oversized =
                ex instanceof CalculationError
                        && ((CalculationError) ex).code()
                                == CalculationError.Code.TRANSFER_TOO_LARGE;
        if (ex instanceof DeviceAccess.Locked
                || (!temporary
                        && (DeviceAccess.locked(context)
                                || (restorePending && key.is(Command.RESTORE))))) {
            if (key.is(Command.RESTORE)) restorePending = true;
            setError(generation, context.getString(R.string.unlock_to_resume));
        } else {

            if (key.is(Command.RESTORE)) {
                checkpoint = null;
                remote.cancel();
                if (!temporary
                        && (ex instanceof RemoteCalculator.InvalidCheckpoint
                                || cancelRequested
                                || ex instanceof com.hp.creals.CR.AbortedException
                                || oversized))
                    endRestore(ex instanceof RemoteCalculator.InvalidCheckpoint);
            }
            setError(
                    generation,
                    cancelRequested || ex instanceof com.hp.creals.CR.AbortedException
                            ? context.getString(R.string.calculation_cancelled)
                            : key.is(Command.RESTORE) && !oversized
                                    ? context.getString(R.string.restore_failed)
                                    : context.getString(
                                            R.string.error_prefix,
                                            ErrorMessages.describe(context, ex)));
        }
        synchronized (this) {
            pending.removeIf(input -> input.generation <= generation);
        }
    }

    private void publishResult(
            DisplayResult result,
            Display nextDisplay,
            EntryEditor incoming,
            Command key,
            long generation) {
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
                        checkpoint = new CalculationSnapshot(result.checkpoint, result.state);
                        if (editor == null
                                || result.editorDisposition
                                        == DisplayResult.EditorDisposition.RESTORE) {
                            editor = incoming;
                            editorDirty = editor.undoCount() > 0 || editor.redoCount() > 0;
                            localBranch = false;
                            editRevision++;
                        }
                        arithmeticDisplay = nextDisplay;
                        display = new Display(nextDisplay, editor);
                        String operationError =
                                result.operationError == null
                                        ? ""
                                        : ErrorMessages.fromCode(
                                                context,
                                                result.operationError,
                                                result.errorArgument);
                        setError(
                                generation,
                                operationError.isEmpty()
                                        ? ""
                                        : context.getString(R.string.error_prefix, operationError));
                        if (!operationError.isEmpty())
                            pending.removeIf(input -> input.generation <= generation);
                        loaded = true;
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
                                ? RpnCalculator.saveWithEntry(accepted.bytes, editor, localBranch)
                                : accepted.bytes);
                persistedCheckpoint = version;
            }
        } catch (java.io.IOException | RuntimeException ex) {
            android.util.Log.e("ExactRPN", "Could not save calculator", ex);
            setError(generation, context.getString(R.string.save_failed));
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
        changed();
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
        changed();
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
