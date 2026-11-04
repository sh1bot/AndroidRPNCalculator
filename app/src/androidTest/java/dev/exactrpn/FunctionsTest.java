// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class FunctionsTest {
    private Bundle request(String key,String input,int fractionMode) {
        Bundle request=new Bundle(); request.putString("key",key); request.putString("input",input);
        request.putString("angle","RAD"); request.putString("notation","SCIENTIFIC");
        request.putString("locale","en-US"); request.putInt("fractionMode",fractionMode); return request;
    }
    @Test public void mixedDisplayCopyAndFactorisationThroughRemoteProcess() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote=new RemoteCalculator(context); ExecutorService worker=Executors.newSingleThreadExecutor();
        File seed=File.createTempFile("functions-test-",".state",context.getNoBackupFilesDir());
        CalculatorStore store=new CalculatorStore(context.getNoBackupFilesDir());
        try {
            RpnCalculator calculator=new RpnCalculator(); for(String key:new String[]{"7","ENTER","3","÷"}) calculator.key(key);
            Files.write(seed.toPath(),calculator.save());
            Bundle improper=worker.submit(() -> remote.evaluate(request("RESTORE",seed.getAbsolutePath(),1))).get(15,TimeUnit.SECONDS);
            assertEquals("7/3",improper.getStringArrayList("rows").get(0));
            Bundle mixed=worker.submit(() -> remote.evaluate(request("FRACTION",improper.getString("checkpoint"),1))).get(15,TimeUnit.SECONDS);
            assertEquals(2,mixed.getInt("fractionMode"));
            assertEquals("2 1/3",mixed.getStringArrayList("rows").get(0));
            assertEquals("2 1/3",mixed.getStringArrayList("clipboard").get(0));
            assertEquals("2\u202f1\u202f/\u202f3",new LocaleNumbers(Locale.US,true).stack("2 1/3",0).toString());
            assertTrue(RpnCalculator.load(Files.readAllBytes(new File(mixed.getString("checkpoint")).toPath())).values().get(0).multiply(new com.android.calculator2.UnifiedReal(3)).definitelyEquals(new com.android.calculator2.UnifiedReal(7)));
            calculator=new RpnCalculator(); calculator.paste("84"); Files.write(seed.toPath(),calculator.save());
            Bundle factored=worker.submit(() -> remote.evaluate(request("Factor",seed.getAbsolutePath(),0))).get(15,TimeUnit.SECONDS);
            assertEquals(Arrays.asList("2","2","3","7"),factored.getStringArrayList("rows"));
            Bundle undone=worker.submit(() -> remote.evaluate(request("UNDO",factored.getString("checkpoint"),0))).get(15,TimeUnit.SECONDS);
            assertEquals("84",undone.getString("entry")); assertTrue(undone.getStringArrayList("rows").isEmpty());
        } finally { remote.close(); worker.shutdownNow(); store.cleanWorkFiles(null); seed.delete(); }
    }
    @Test public void inverseButtonsSendTheDisplayedFunction() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext(); AtomicReference<String> command=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            CalculatorKeypad keypad=new CalculatorKeypad(context,command::set,true,expanded -> {});
            keypad.setInverse(true);
            for(String[] example:new String[][]{{"asin","asin"},{"acos","acos"},{"atan","atan"},{"eˣ","eˣ"},{"x²","x²"},{"ˣ√y","root"},{"mod","mod"}}) {
                Button button=find(keypad,example[0]); assertNotNull(example[0],button); button.performClick(); assertEquals(example[1],command.get());
            }
            keypad.setInverse(false); Button sine=find(keypad,"sin"); assertNotNull(sine); sine.performClick(); assertEquals("sin",command.get());
        });
    }
    private Button find(View view,String text) {
        if(view instanceof Button && ((Button)view).getText().toString().equals(text)) return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button result=find(((ViewGroup)view).getChildAt(i),text); if(result!=null) return result;
        }
        return null;
    }
}
