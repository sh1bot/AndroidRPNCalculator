// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

/** Only the UI process can commit a result; cancelled workers never overwrite this file. */
final class CalculatorStore {
    private final AtomicFile file, restoreAttempt;
    private final File directory;
    private final BooleanSupplier allowed;

    CalculatorStore(File directory) {
        this(directory, () -> true);
    }

    CalculatorStore(File directory, BooleanSupplier allowed) {
        this.allowed = allowed;
        this.directory = directory;
        file = new AtomicFile(new File(directory, "calculator-state.bin"));
        restoreAttempt = new AtomicFile(new File(directory, "calculator-restore.bin"));
    }

    String checkpoint() throws IOException {
        checkAccess();
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists())
            return null;
        try (FileInputStream ignored = file.openRead()) {
            return file.getBaseFile().getAbsolutePath();
        }
    }

    byte[] load() throws IOException {
        checkAccess();
        if (checkpoint() == null) return null;
        checkSize(file.getBaseFile().length());
        try (FileInputStream in = file.openRead();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                checkSize((long) out.size() + count);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    /** Mutations reset this counter; two interrupted starts preserve the file for recovery. */
    byte[] beginRestore() throws IOException {
        if (checkpoint() == null) {
            finishRestore();
            return null;
        }
        int attempts = 0;
        try {
            byte[] record = restoreAttempt.readFully();
            if (record.length == 1 && record[0] >= 0 && record[0] <= 2) attempts = record[0];
        } catch (IOException absentOrDamaged) {
        }
        if (attempts >= 2) {
            quarantine();
            throw new IOException("Saved calculation repeatedly failed to restore");
        }
        write(restoreAttempt, new byte[] {(byte) (attempts + 1)});
        return load();
    }

    private static void checkSize(long length) {
        CalculationLimits.require(length, CalculationLimits.SAVED_BYTES);
    }

    void finishRestore() {
        checkAccess();
        restoreAttempt.delete();
    }

    void save(byte[] bytes) throws IOException {
        checkSize(bytes.length);
        write(file, bytes);
        finishRestore();
    }

    private void write(AtomicFile target, byte[] bytes) throws IOException {
        checkAccess();
        FileOutputStream out = target.startWrite();
        try {
            out.write(bytes);
            checkAccess();
            target.finishWrite(out);
        } catch (IOException | RuntimeException ex) {
            target.failWrite(out);
            throw ex;
        }
    }

    void quarantine() throws IOException {
        checkAccess();
        if (file.getBaseFile().exists())
            Files.move(
                    file.getBaseFile().toPath(),
                    new File(directory, "calculator-state.corrupt").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        file.delete();
        finishRestore();
    }

    void clear() {
        if (!allowed.getAsBoolean()) return;
        file.delete();
        restoreAttempt.delete();
    }

    private void checkAccess() {
        if (!allowed.getAsBoolean()) throw new DeviceAccess.Locked();
    }
}
