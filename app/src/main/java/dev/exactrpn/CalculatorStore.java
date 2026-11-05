// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;

/** Only the UI process can commit a result; cancelled workers never overwrite this file. */
final class CalculatorStore {
    private final AtomicFile file;
    private final File directory;
    CalculatorStore(File directory) { this.directory=directory; file=new AtomicFile(new File(directory,"calculator-state.bin")); }
    String checkpoint() throws IOException {
        if(!file.getBaseFile().exists() && !new File(file.getBaseFile()+".bak").exists()) return null;
        try(FileInputStream ignored=file.openRead()) { return file.getBaseFile().getAbsolutePath(); }
    }
    void save(String checkpoint) throws IOException {
        byte[] bytes=Files.readAllBytes(new File(checkpoint).toPath());
        FileOutputStream out=file.startWrite();
        try { out.write(bytes); file.finishWrite(out); }
        catch(IOException | RuntimeException ex) { file.failWrite(out); throw ex; }
    }
    void quarantine() throws IOException {
        if(file.getBaseFile().exists()) Files.move(file.getBaseFile().toPath(),new File(directory,"calculator-state.corrupt").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        file.delete();
    }
    void clear() { file.delete(); }
    void cleanWorkFiles(String keep) {
        File[] files=directory.listFiles((dir,name) -> name.startsWith("calculation-"));
        if(files!=null) for(File temporary:files) if(!temporary.getAbsolutePath().equals(keep)) temporary.delete();
    }
}
