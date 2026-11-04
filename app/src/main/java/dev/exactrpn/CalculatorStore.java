// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.util.AtomicFile;
import java.io.*;

/** App-private, atomic checkpoint. Lives outside Android's cloud-backup directory. */
final class CalculatorStore {
    private final AtomicFile file;
    CalculatorStore(File directory) { file=new AtomicFile(new File(directory,"calculator-state.bin")); }
    RpnCalculator load() throws IOException {
        if(!file.getBaseFile().exists() && !new File(file.getBaseFile()+".bak").exists()) return null;
        return RpnCalculator.load(file.readFully());
    }
    void save(RpnCalculator calculator,boolean discardEmpty) throws IOException {
        if(discardEmpty && calculator.isEmpty()) { calculator.clearUndo(); file.delete(); return; }
        byte[] bytes=calculator.save();
        FileOutputStream out=file.startWrite();
        try { out.write(bytes); file.finishWrite(out); }
        catch(IOException | RuntimeException ex) { file.failWrite(out); throw ex; }
    }
}
