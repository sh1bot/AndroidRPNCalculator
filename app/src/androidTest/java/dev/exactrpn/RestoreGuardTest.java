// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public final class RestoreGuardTest {
    private File directory() throws Exception {
        return Files.createTempDirectory(
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getCacheDir()
                                .toPath(),
                        "restore-test-")
                .toFile();
    }

    private void remove(File directory) {
        for (File file : directory.listFiles()) file.delete();
        directory.delete();
    }

    @Test
    public void interruptedRestoresAcrossInstancesQuarantineOnlyTheRepeatedSnapshot()
            throws Exception {
        File directory = directory();
        byte[] saved = new RpnCalculator().save();
        try {
            CalculatorStore store = new CalculatorStore(directory);
            store.save(saved);
            assertArrayEquals(saved, store.beginRestore());
            assertArrayEquals(saved, new CalculatorStore(directory).beginRestore());
            try {
                new CalculatorStore(directory).beginRestore();
                fail("A third interrupted restore must be blocked");
            } catch (java.io.IOException expected) {
            }
            assertNull(store.checkpoint());
            assertArrayEquals(
                    saved,
                    Files.readAllBytes(new File(directory, "calculator-state.corrupt").toPath()));
            assertNull(store.beginRestore());
        } finally {
            remove(directory);
        }
    }

    @Test
    public void successfulOrCancelledRestoresAndSavesResetTheGuard() throws Exception {
        File directory = directory();
        try {
            CalculatorStore store = new CalculatorStore(directory);
            RpnCalculator calculator = new RpnCalculator();
            store.save(calculator.save());
            for (int i = 0; i < 5; i++) {
                store.beginRestore();
                store.finishRestore();
            }
            store.beginRestore();
            store.beginRestore();
            calculator.key("7");
            byte[] changed = calculator.save();
            store.save(changed);
            assertArrayEquals(changed, store.beginRestore());
            store.finishRestore();
            assertArrayEquals(changed, store.beginRestore());
            store.clear();
            assertFalse(new File(directory, "calculator-restore.bin").exists());
        } finally {
            remove(directory);
        }
    }

    @Test
    public void oversizedSnapshotIsRejectedWithoutAllocatingItsContents() throws Exception {
        File directory = directory();
        try {
            File saved = new File(directory, "calculator-state.bin");
            try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(saved, "rw")) {
                file.setLength((long) CalculationLimits.SAVED_BYTES + 1);
            }
            try {
                new CalculatorStore(directory).beginRestore();
                fail("Oversized snapshot unexpectedly loaded");
            } catch (IllegalArgumentException expected) {
                assertEquals(
                        CalculationError.Code.TRANSFER_TOO_LARGE,
                        ((CalculationError) expected).code());
            }
        } finally {
            remove(directory);
        }
    }
}
