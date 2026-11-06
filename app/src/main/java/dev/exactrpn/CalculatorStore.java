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
    private final AtomicFile file;
    private final File directory;
    private final BooleanSupplier allowed;

    CalculatorStore(File directory) {
        this(directory, () -> true);
    }

    CalculatorStore(File directory, BooleanSupplier allowed) {
        this.allowed = allowed;
        this.directory = directory;
        file = new AtomicFile(new File(directory, "calculator-state.bin"));
    }

    String checkpoint() throws IOException {
        checkAccess();
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists())
            return null;
        try (FileInputStream ignored = file.openRead()) {
            return file.getBaseFile().getAbsolutePath();
        }
    }

    void save(String checkpoint) throws IOException {
        checkAccess();
        byte[] bytes = Files.readAllBytes(new File(checkpoint).toPath());
        checkAccess();
        FileOutputStream out = file.startWrite();
        try {
            out.write(bytes);
            checkAccess();
            file.finishWrite(out);
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
    }

    void clear() {
        if (!allowed.getAsBoolean()) return;
        file.delete();
    }

    void cleanWorkFiles(String keep) {
        if (!allowed.getAsBoolean()) return;
        File[] files = directory.listFiles((dir, name) -> name.startsWith("calculation-"));
        if (files != null)
            for (File temporary : files)
                if (!temporary.getAbsolutePath().equals(keep)) temporary.delete();
    }

    private void checkAccess() {
        if (!allowed.getAsBoolean()) throw new DeviceAccess.Locked();
    }
}
