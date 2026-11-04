// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CancellationTest {
    private Bundle request(String key,String input) {
        Bundle request=new Bundle(); request.putString("key",key); request.putString("input",input);
        request.putString("angle","RAD"); request.putString("notation","SCIENTIFIC");
        request.putString("locale","en-US"); return request;
    }
    @Test public void cancelStopsWorkerAndPreservesCheckpoint() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote=new RemoteCalculator(context);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CalculatorStore store=new CalculatorStore(context.getNoBackupFilesDir());
        File seed=File.createTempFile("cancel-test-",".state",context.getNoBackupFilesDir());
        try {
            // Equal expressions whose equality the constructive-real engine cannot establish.
            RpnCalculator calculator=new RpnCalculator();
            for(String key:new String[]{"π","e","+","e","π","+","−"}) calculator.key(key);
            assertFalse(calculator.values().get(0).definitelyZero());
            Files.write(seed.toPath(),calculator.save()); store.save(seed.getAbsolutePath());
            Bundle initial=worker.submit(() -> remote.evaluate(request("RESTORE",store.checkpoint()))).get(15,TimeUnit.SECONDS);
            String accepted=initial.getString("checkpoint"); store.save(accepted);
            byte[] committed=Files.readAllBytes(new File(store.checkpoint()).toPath());
            Future<Bundle> stuck=worker.submit(() -> remote.evaluate(request("1/x",accepted)));
            SystemClock.sleep(300);
            assertFalse("Reciprocal of unknown zero should still be calculating",stuck.isDone());
            long started=SystemClock.elapsedRealtime(); remote.cancel();
            try { stuck.get(2,TimeUnit.SECONDS); fail("Cancelled request returned a result"); }
            catch(ExecutionException | CancellationException expected) {}
            assertTrue("Cancel must release the caller promptly",SystemClock.elapsedRealtime()-started<2000);
            assertArrayEquals("Cancelled work cannot replace committed storage",committed,Files.readAllBytes(new File(store.checkpoint()).toPath()));
            Bundle cleared=worker.submit(() -> remote.evaluate(request("CLEAR",accepted))).get(15,TimeUnit.SECONDS);
            assertTrue("A fresh worker must accept new operations",cleared.getStringArrayList("rows").isEmpty());
            Bundle undone=worker.submit(() -> remote.evaluate(request("UNDO",cleared.getString("checkpoint")))).get(15,TimeUnit.SECONDS);
            assertEquals("Cancellation preserves the old stack and undo",1,undone.getStringArrayList("rows").size());
        } finally {
            remote.close(); worker.shutdownNow(); store.clear(); store.cleanWorkFiles(null); seed.delete();
        }
    }
}
