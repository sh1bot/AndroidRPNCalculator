// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.android.calculator2.UnifiedReal;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Arithmetic runs in :calculation, so Cancel can terminate even non-interruptible work. */
public class CalculationService extends Service {
    static final int HELLO = 1, EVALUATE = 2;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RpnCalculator cached;
    private String cachedId, cachedSession, cachedStackId;
    private final NumericValueCache numbers = new NumericValueCache();
    private final Messenger messenger =
            new Messenger(
                    new Handler(
                            Looper.getMainLooper(),
                            message -> {
                                if (message.what == HELLO) {
                                    Message reply = Message.obtain(null, HELLO);
                                    reply.arg1 = android.os.Process.myPid();
                                    reply.arg2 = message.arg2;
                                    try {
                                        message.replyTo.send(reply);
                                    } catch (RemoteException ignored) {
                                    }
                                } else if (message.what == EVALUATE) {
                                    byte[] request = message.getData().getByteArray("payload");
                                    Messenger reply = message.replyTo;
                                    int id = message.arg1, generation = message.arg2;
                                    worker.execute(() -> evaluate(request, id, generation, reply));
                                }
                                return true;
                            }));

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    // Idle clients unbind normally. Killing an idle service on every close can trigger
    // Android's crash/restart backoff; Cancel already kills a worker with unfinished work.
    @Override
    public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void evaluate(byte[] encoded, int requestId, int generation, Messenger client) {
        Bundle response = new Bundle();
        boolean readOnly = true;
        try {
            checkAccess();
            CalculationRequest request = CalculationRequest.decode(encoded);
            Command command = request.command;
            readOnly = false;
            loadCheckpoint(request, response);
            readOnly = command.kind == Command.Kind.READ_VALUES;
            byte[] display;
            if (readOnly) {
                DisplayResult result = new DisplayResult();
                result.readOnly = true;
                result.stackId = cachedStackId;
                for (UnifiedReal value : cached.selectedValues(command.rows()))
                    result.values.add(NumericEvaluator.read(value, request.numbers));
                display = DisplayResult.encode(result, null);
            } else {
                display = arithmeticResult(request);
            }
            checkAccess();
            response.putByteArray("payload", display);
        } catch (OutOfMemoryError ex) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        } catch (IOException | RuntimeException | StackOverflowError ex) {
            boolean intact = readOnly && !(ex instanceof DeviceAccess.Locked);
            response.putBoolean("cacheIntact", intact);
            if (!intact) {
                // Mutating requests must reload the last accepted state after a failure.
                cached = null;
                numbers.clear();
            }
            if (ex instanceof DeviceAccess.Locked) response.putBoolean("deviceLocked", true);
            else {
                response.putString("errorCode", ErrorCodes.code(ex).name());
                response.putInt("errorArgument", ErrorCodes.argument(ex));
            }
        }
        Message result = Message.obtain(null, EVALUATE);
        result.arg1 = requestId;
        result.arg2 = generation;
        result.setData(response);
        try {
            client.send(result);
        } catch (RemoteException ignored) {
        }
    }

    private byte[] arithmeticResult(CalculationRequest request) throws IOException {
        Evaluation evaluation = new Evaluation(request);
        EntryEditor entry = evaluation.candidate.detachEntryEditor();
        byte[] state = evaluation.candidate.save();
        DisplayResult display = evaluation.presentation();
        String id = UUID.randomUUID().toString();
        display.editorDisposition =
                evaluation.keepEditor(entry)
                        ? DisplayResult.EditorDisposition.KEEP
                        : DisplayResult.EditorDisposition.RESTORE;
        display.checkpoint = id;
        display.state = state;
        byte[] encoded = DisplayResult.encode(display, entry);
        cachedId = id;
        cachedStackId = evaluation.stackId;
        return encoded;
    }

    private void loadCheckpoint(CalculationRequest request, Bundle response) throws IOException {
        checkAccess();
        String input = request.inputId;
        String session = request.sessionId;
        if (cached == null
                || !Objects.equals(input, cachedId)
                || !Objects.equals(session, cachedSession)) {
            try {
                cached =
                        input == null
                                ? new RpnCalculator()
                                : RpnCalculator.load(Transfer.require(request.input));
            } catch (IOException ex) {
                response.putBoolean("invalidCheckpoint", true);
                throw new IOException(
                        "Saved calculation is damaged or from an unsupported version", ex);
            }
            if (input == null) cached.setAngleUnit(request.angle);
            cachedId = input;
            cachedSession = session;
            cachedStackId = UUID.randomUUID().toString();
            numbers.clear();
        }
    }

    protected boolean temporaryService() {
        return false;
    }

    private void checkAccess() {
        if (!temporaryService()) DeviceAccess.requireUnlocked(this);
    }

    private final class Evaluation {
        final RpnCalculator candidate;
        final String stackId;
        final Command command;
        final NumericRequest precision;
        final EntryEditor.State beforeEntry;
        final boolean sameStack;
        CalculationError.Code operationError;
        int errorArgument;

        Evaluation(CalculationRequest request) {
            Command key = request.command;
            command = key;
            precision = request.numbers;
            List<UnifiedReal> before = cached.values();
            candidate = cached;
            byte[] draft = request.entryDraft;
            if (request.entryBranch) candidate.discardRedo();
            if (draft != null) {
                try {
                    candidate.useEntryEditor(EntryEditor.load(draft));
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Invalid entry draft", ex);
                }
            }
            beforeEntry = candidate.entryState();
            if (!key.is(Command.RESTORE)) {
                try {
                    key.apply(candidate);
                } catch (IllegalArgumentException | ArithmeticException ex) {
                    // The engine may have committed valid entry before the operator failed.
                    // Publish that state together with the error, rather than losing the entry.
                    operationError = ErrorCodes.code(ex);
                    errorArgument = ErrorCodes.argument(ex);
                }
            }
            sameStack = candidate.sameStack(before);
            stackId = sameStack ? cachedStackId : UUID.randomUUID().toString();
        }

        boolean keepEditor(EntryEditor returned) {
            if (command.is("ANGLE")) return true;
            return !command.is(Command.RESTORE)
                    && !command.is(Command.UNDO)
                    && !command.is(Command.REDO)
                    && sameStack
                    && beforeEntry.same(returned);
        }

        DisplayResult presentation() {
            List<UnifiedReal> values = candidate.values();
            numbers.retain(values);
            DisplayResult display = new DisplayResult();
            for (UnifiedReal value : values) display.values.add(numbers.read(value, precision));
            display.operationError = operationError;
            display.errorArgument = errorArgument;
            display.stackId = stackId;
            display.angle = candidate.angleUnit();
            return display;
        }
    }
}
