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

    /** Two interrupted attempts at the same snapshot are enough; preserve it for recovery. */
    byte[] beginRestore() throws IOException {
        if (checkpoint() == null) {
            finishRestore();
            return null;
        }
        byte[] fingerprint;
        try {
            checkSize(file.getBaseFile().length());
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (FileInputStream in = file.openRead()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted())
                        throw new com.hp.creals.CR.AbortedException();
                    digest.update(buffer, 0, count);
                }
            }
            fingerprint = digest.digest();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        int attempts = 0;
        try {
            byte[] previous = restoreAttempt.readFully();
            if (previous.length == 33
                    && previous[32] > 0
                    && previous[32] <= 2
                    && java.util.Arrays.equals(fingerprint, java.util.Arrays.copyOf(previous, 32)))
                attempts = previous[32] & 255;
        } catch (IOException absentOrDamaged) {
            // A missing/damaged attempt record must not prevent recovery of a valid calculation.
        }
        if (attempts >= 2) {
            quarantine();
            throw new IOException("Saved calculation repeatedly failed to restore");
        }
        checkAccess();
        FileOutputStream out = restoreAttempt.startWrite();
        try {
            out.write(fingerprint);
            out.write(attempts + 1);
            checkAccess();
            restoreAttempt.finishWrite(out);
        } catch (IOException | RuntimeException ex) {
            restoreAttempt.failWrite(out);
            throw ex;
        }
        // Record the attempt before allocating the snapshot: an OOM here must count too.
        return load();
    }

    private static void checkSize(long length) throws IOException {
        if (length > MemoryPayload.MAX_LENGTH)
            throw new IOException("Saved calculation is too large");
    }

    void finishRestore() {
        checkAccess();
        restoreAttempt.delete();
    }

    void save(byte[] bytes) throws IOException {
        checkAccess();
        FileOutputStream out = file.startWrite();
        try {
            out.write(bytes);
            checkAccess();
            file.finishWrite(out);
            finishRestore();
        } catch (IOException | RuntimeException ex) {
            file.failWrite(out);
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

    void removeLegacyWorkFiles() {
        if (!allowed.getAsBoolean()) return;
        File[] files = directory.listFiles((dir, name) -> name.startsWith("calculation-"));
        if (files != null) for (File temporary : files) temporary.delete();
    }

    private void checkAccess() {
        if (!allowed.getAsBoolean()) throw new DeviceAccess.Locked();
    }
}
