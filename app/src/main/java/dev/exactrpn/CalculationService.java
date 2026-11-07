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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private String cachedPath, cachedStackId;
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

    private boolean sameStack(RpnCalculator a, RpnCalculator b) {
        List<UnifiedReal> left = a.values(), right = b.values();
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (left.get(i) != right.get(i)) return false;
        return true;
    }

    private void evaluate(Bundle request, Messenger client) {
        Bundle response = new Bundle();
        File snapshot = null, presentation = null;
        try {
            checkAccess();
            File directory = workDirectory(request);
            loadCheckpoint(request, response, directory);
            Evaluation evaluation = new Evaluation(request);
            checkAccess();
            snapshot = File.createTempFile("calculation-", ".state", directory);
            byte[] state = evaluation.candidate.save();
            checkAccess();
            Files.write(snapshot.toPath(), state);
            Bundle display = evaluation.presentation(snapshot);
            // Only paths cross Binder; large stacks must not hit Binder's transaction-size limit.
            checkAccess();
            presentation = File.createTempFile("calculation-", ".display", directory);
            DisplayResult.write(presentation, display);
            response.putString("presentation", presentation.getAbsolutePath());
            cached = evaluation.candidate;
            cachedPath = snapshot.getAbsolutePath();
            cachedStackId = evaluation.stackId;
        } catch (OutOfMemoryError ex) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        } catch (IOException | RuntimeException | StackOverflowError ex) {
            if (snapshot != null) snapshot.delete();
            if (presentation != null) presentation.delete();
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
        }
    }

    private void loadCheckpoint(Bundle request, Bundle response, File directory)
            throws IOException {
        String input = request.getString("input");
        if (input != null
                && !new File(input)
                        .getCanonicalFile()
                        .getParentFile()
                        .equals(directory.getCanonicalFile()))
            throw new IOException("Checkpoint outside calculation workspace");
        if (temporaryService()
                && input != null
                && !new File(input).getName().startsWith("calculation-"))
            throw new IOException("Temporary sessions cannot restore saved calculations");
        checkAccess();
        if (cached == null || !Objects.equals(input, cachedPath)) {
            try {
                cached =
                        input == null
                                ? new RpnCalculator()
                                : RpnCalculator.load(Files.readAllBytes(new File(input).toPath()));
            } catch (IOException ex) {
                response.putBoolean("invalidCheckpoint", true);
                throw new IOException(
                        "Saved calculation is damaged or from an unsupported version", ex);
            }
            if (input == null)
                cached.setAngleUnit(RpnCalculator.AngleUnit.valueOf(request.getString("angle")));
            cachedPath = input;
            cachedStackId = UUID.randomUUID().toString();
        }
    }

    protected boolean temporaryService() {
        return false;
    }

    private void checkAccess() {
        if (!temporaryService()) DeviceAccess.requireUnlocked(this);
    }

    private File workDirectory(Bundle request) throws IOException {
        if (!temporaryService()) return getNoBackupFilesDir();
        String id = request.getString("workspace");
        if (id == null || !id.matches("[a-f0-9-]{36}"))
            throw new IOException("Invalid temporary workspace");
        File directory = new File(getCacheDir(), "locked-calculations/" + id);
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not create temporary workspace");
        return directory;
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
            candidate = cached.copy();
            Locale locale = Locale.forLanguageTag(request.getString("locale"));
            numberMode = NumberMode.fromId(request.getInt("numberMode"));
            fractionMode = FractionMode.fromId(request.getInt("fractionMode"));
            notation = ValueFormatter.Notation.valueOf(request.getString("notation"));
            LocaleNumbers local = new LocaleNumbers(locale);
            LocaleNumbers format = new LocaleNumbers(locale, numberMode);
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
            stackId = sameStack(cached, candidate) ? cachedStackId : UUID.randomUUID().toString();
        }

        Bundle presentation(File snapshot) {
            ArrayList<String> rows =
                    new ArrayList<>(candidate.formattedValues(notation, fractionMode));
            ArrayList<String> copies = new ArrayList<>(), compact = new ArrayList<>();
            for (UnifiedReal value : candidate.values()) {
                copies.add(ValueFormatter.clipboard(value, notation, fractionMode));
                compact.add(ValueFormatter.format(value, false, notation, fractionMode));
            }
            Bundle display = new Bundle();
            display.putString("operationError", operationError);
            display.putString("stackId", stackId);
            display.putStringArrayList("rows", rows);
            display.putStringArrayList("clipboard", copies);
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
            display.putString("checkpoint", snapshot.getAbsolutePath());
            return display;
        }
    }
}
