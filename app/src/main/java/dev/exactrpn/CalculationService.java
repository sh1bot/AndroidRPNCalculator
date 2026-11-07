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
    private static final java.util.regex.Pattern WORKSPACE =
            java.util.regex.Pattern.compile("[a-f0-9-]{36}");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RpnCalculator cached;
    private String cachedPath, cachedStackId;
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
        File snapshot = null, presentation = null;
        try {
            checkAccess();
            File directory = workDirectory(request);
            loadCheckpoint(request, response, directory);
            if (Command.parse(request.getString("key")).kind == Command.Kind.COPY_ROWS) {
                String text = copyRows(request);
                checkAccess();
                presentation = File.createTempFile("calculation-", ".copy", directory);
                Files.write(
                        presentation.toPath(),
                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                response.putString("copyFile", presentation.getAbsolutePath());
            } else {
                Evaluation evaluation = new Evaluation(request);
                checkAccess();
                snapshot = File.createTempFile("calculation-", ".state", directory);
                byte[] state = evaluation.candidate.save();
                checkAccess();
                Files.write(snapshot.toPath(), state);
                Bundle display = evaluation.presentation(snapshot);
                // Only paths cross Binder; large stacks must not hit Binder's transaction-size
                // limit.
                checkAccess();
                presentation = File.createTempFile("calculation-", ".display", directory);
                DisplayResult.write(presentation, display);
                response.putString("presentation", presentation.getAbsolutePath());
                cached = evaluation.candidate;
                cachedPath = snapshot.getAbsolutePath();
                cachedStackId = evaluation.stackId;
            }
        } catch (OutOfMemoryError ex) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        } catch (IOException | RuntimeException | StackOverflowError ex) {
            // In-place evaluation is safe only after its snapshot and display are accepted.
            // Unexpected failures reload the last accepted checkpoint on the next request.
            cached = null;
            texts.clear();
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

    private String copyRows(Bundle request) {
        Command command = Command.parse(request.getString("key"));
        ValueFormatter.Notation notation =
                ValueFormatter.Notation.valueOf(request.getString("notation"));
        FractionMode fraction = FractionMode.fromId(request.getInt("fractionMode"));
        java.util.StringJoiner result = new java.util.StringJoiner("\n");
        for (UnifiedReal value : cached.selectedValues(command.rows()))
            result.add(texts.clipboard(value, notation, fraction));
        return result.toString();
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
        if (id == null || !WORKSPACE.matcher(id).matches())
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
            List<UnifiedReal> before = cached.values();
            candidate = cached;
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

        Bundle presentation(File snapshot) {
            List<UnifiedReal> values = candidate.values();
            texts.retain(values);
            ArrayList<String> rows = new ArrayList<>(values.size());
            ArrayList<String> compact = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                UnifiedReal value = values.get(i);
                boolean newest = !candidate.isEditing() && i == values.size() - 1;
                rows.add(texts.row(value, newest, notation, fractionMode));
                compact.add(texts.row(value, false, notation, fractionMode));
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
            display.putString("checkpoint", snapshot.getAbsolutePath());
            return display;
        }
    }
}
