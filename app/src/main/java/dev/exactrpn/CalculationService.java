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
    private final java.util.Map<String, LocaleNumbers> profiles = new java.util.HashMap<>();

    private LocaleNumbers profile(Locale locale, NumberMode mode) {
        String key = locale.toLanguageTag() + ":" + mode.name();
        if (profiles.size() > 12) profiles.clear();
        return profiles.computeIfAbsent(key, ignored -> new LocaleNumbers(locale, mode));
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
                                    Bundle request = new Bundle(message.getData());
                                    Messenger reply = message.replyTo;
                                    worker.execute(() -> evaluate(request, reply));
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

    private void evaluate(Bundle request, Messenger client) {
        Bundle response = new Bundle();
        MemoryPayload payload = null;
        boolean readOnly = false;
        try {
            Command command = Command.parse(request.getString("key"));
            checkAccess();
            loadCheckpoint(request, response);
            readOnly = command.kind == Command.Kind.COPY_ROWS;
            Bundle display;
            if (readOnly) {
                display = new Bundle();
                display.putString("copyText", copyRows(request, command));
            } else {
                display = arithmeticResult(request);
            }
            checkAccess();
            payload = MemoryPayload.create(DisplayResult.encode(display));
            payload.put(response, "payload");
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
        result.arg1 = request.getInt("requestId");
        result.arg2 = request.getInt("generation");
        result.setData(response);
        try {
            client.send(result);
        } catch (RemoteException ignored) {
        } finally {
            MemoryPayload.discard(request, "input");
            if (payload != null)
                try {
                    payload.close();
                } catch (IOException ignored) {
                }
        }
    }

    private Bundle arithmeticResult(Bundle request) throws IOException {
        Evaluation evaluation = new Evaluation(request);
        byte[] entryState = evaluation.candidate.detachEntryEditor().save();
        byte[] state = evaluation.candidate.save();
        Bundle display = evaluation.presentation();
        String id = UUID.randomUUID().toString();
        display.putString("checkpoint", id);
        display.putByteArray("state", state);
        display.putByteArray("entryState", entryState);
        cachedId = id;
        cachedStackId = evaluation.stackId;
        return display;
    }

    private String copyRows(Bundle request, Command command) {
        ValueFormatter.Notation notation =
                ValueFormatter.Notation.valueOf(request.getString("notation"));
        FractionMode fraction = FractionMode.fromId(request.getInt("fractionMode"));
        java.util.StringJoiner result = new java.util.StringJoiner("\n");
        for (UnifiedReal value : cached.selectedValues(command.rows()))
            result.add(texts.clipboard(value, notation, fraction));
        return result.toString();
    }

    private void loadCheckpoint(Bundle request, Bundle response) throws IOException {
        checkAccess();
        String input = request.getString("inputId");
        String session = request.getString("sessionId");
        if (cached == null
                || !Objects.equals(input, cachedId)
                || !Objects.equals(session, cachedSession)) {
            try {
                cached =
                        input == null
                                ? new RpnCalculator()
                                : RpnCalculator.load(MemoryPayload.take(request, "input"));
            } catch (IOException ex) {
                response.putBoolean("invalidCheckpoint", true);
                throw new IOException(
                        "Saved calculation is damaged or from an unsupported version", ex);
            }
            if (input == null)
                cached.setAngleUnit(RpnCalculator.AngleUnit.valueOf(request.getString("angle")));
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

        Evaluation(Bundle request) {
            Command key = Command.parse(request.getString("key"));
            List<UnifiedReal> before = cached.values();
            candidate = cached;
            byte[] draft = request.getByteArray("entryDraft");
            if (request.getBoolean("entryBranch")) candidate.discardRedo();
            if (draft != null) {
                try {
                    candidate.useEntryEditor(RpnCalculator.load(draft));
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Invalid entry draft", ex);
                }
            }
            Locale locale = Locale.forLanguageTag(request.getString("locale"));
            numberMode = NumberMode.fromId(request.getInt("numberMode"));
            fractionMode = FractionMode.fromId(request.getInt("fractionMode"));
            notation = ValueFormatter.Notation.valueOf(request.getString("notation"));
            LocaleNumbers local = profile(locale, NumberMode.LOCALE);
            LocaleNumbers format = profile(locale, numberMode);
            candidate.setInputProfile(format.profile());
            if (key.is(Command.RESTORE) || key.is(Command.CHECKPOINT)) {
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
                    key.apply(candidate, local.profile(), format.profile());
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
            texts.retain(values);
            ArrayList<String> rows = new ArrayList<>(values.size());
            ArrayList<String> compact = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                UnifiedReal value = values.get(i);
                boolean newest = i == values.size() - 1;
                rows.add(texts.row(value, newest, notation, fractionMode));
                compact.add(
                        texts.row(
                                value,
                                false,
                                notation,
                                newest ? FractionMode.DECIMAL : fractionMode));
            }
            Bundle display = new Bundle();
            display.putString("operationError", operationError);
            display.putString("stackId", stackId);
            display.putStringArrayList("rows", rows);
            display.putStringArrayList("compactRows", compact);
            display.putString("entry", candidate.entry());
            display.putString("problem", candidate.entryProblem());
            display.putBoolean("editing", candidate.isEditing());
            display.putBoolean("raw", candidate.rawEntry());
            display.putInt("start", candidate.selectionStart());
            display.putInt("end", candidate.selectionEnd());
            display.putString("angle", candidate.angleUnit().name());
            display.putString("notation", notation.name());
            display.putInt("numberMode", numberMode.id);
            display.putInt("fractionMode", fractionMode.id);
            return display;
        }
    }
}
