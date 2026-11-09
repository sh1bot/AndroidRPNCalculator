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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final ValuePresentationCache texts = new ValuePresentationCache();
    private Locale profileLocale;
    private LocaleNumbers localeNumbers;

    private LocaleNumbers profile(Locale locale) {
        if (!locale.equals(profileLocale)) {
            profileLocale = locale;
            localeNumbers = new LocaleNumbers(locale, NumberMode.LOCALE);
        }
        return localeNumbers;
    }

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
            Command command = request.command();
            readOnly = false;
            loadCheckpoint(request, response);
            readOnly = command.kind == Command.Kind.COPY_ROWS;
            byte[] display;
            if (readOnly) {
                Bundle copy = new Bundle();
                copy.putString("copyText", copyRows(request, command));
                display = DisplayResult.encode(copy, null);
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
                texts.clear();
            }
            if (ex instanceof DeviceAccess.Locked) response.putBoolean("deviceLocked", true);
            else if (ex instanceof CalculationError) {
                CalculationError failure = (CalculationError) ex;
                response.putString("errorCode", failure.code().name());
                response.putInt("errorArgument", failure.argument());
            } else response.putString("error", ErrorMessages.describe(this, ex));
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
        Bundle display = evaluation.presentation();
        String id = UUID.randomUUID().toString();
        display.putString("checkpoint", id);
        display.putByteArray("state", state);
        byte[] encoded = DisplayResult.encode(display, entry);
        cachedId = id;
        cachedStackId = evaluation.stackId;
        return encoded;
    }

    private String copyRows(CalculationRequest request, Command command) {
        ValueFormatter.Notation notation = request.notation;
        FractionMode fraction = request.fractionMode;
        java.util.StringJoiner result = new java.util.StringJoiner("\n");
        for (UnifiedReal value : cached.selectedValues(command.rows()))
            result.add(ValueFormatter.clipboard(value, notation, fraction));
        return result.toString();
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
            texts.clear();
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
        ValueFormatter.Notation notation;
        NumberMode numberMode;
        FractionMode fractionMode;
        String operationError = "";

        Evaluation(CalculationRequest request) {
            Command key = request.command();
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
            Locale locale = Locale.forLanguageTag(request.locale);
            numberMode = request.numberMode;
            fractionMode = request.fractionMode;
            notation = request.notation;
            LocaleNumbers local = profile(locale);
            if (key.is(Command.RESTORE)) {
                /* No arithmetic operation. */
            } else if (key.is("FORMAT"))
                notation =
                        notation == ValueFormatter.Notation.SCIENTIFIC
                                ? ValueFormatter.Notation.ENGINEERING
                                : ValueFormatter.Notation.SCIENTIFIC;
            else if (key.is("NUMBER_FORMAT")) numberMode = numberMode.next();
            else if (key.is("FRACTION")) fractionMode = fractionMode.next();
            else {
                try {
                    key.apply(candidate, local.profile());
                } catch (IllegalArgumentException | ArithmeticException ex) {
                    // The engine may have committed valid entry before the operator failed.
                    // Publish that state together with the error, rather than losing the entry.
                    operationError = ErrorMessages.describe(CalculationService.this, ex);
                }
            }
            stackId = candidate.sameStack(before) ? cachedStackId : UUID.randomUUID().toString();
        }

        Bundle presentation() {
            List<UnifiedReal> values = candidate.values();
            texts.setMode(notation, fractionMode);
            texts.retain(values);
            ArrayList<String> rows = new ArrayList<>(values.size());
            String compactTop = "";
            for (int i = 0; i < values.size(); i++) {
                UnifiedReal value = values.get(i);
                boolean newest = i == values.size() - 1;
                rows.add(texts.row(value, newest));
                if (newest) compactTop = texts.compactTop(value);
            }
            Bundle display = new Bundle();
            display.putString("operationError", operationError);
            display.putString("stackId", stackId);
            display.putStringArrayList("rows", rows);
            display.putString("compactTop", compactTop);
            display.putString("angle", candidate.angleUnit().name());
            display.putString("notation", notation.name());
            display.putInt("numberMode", numberMode.ordinal());
            display.putInt("fractionMode", fractionMode.ordinal());
            return display;
        }
    }
}
