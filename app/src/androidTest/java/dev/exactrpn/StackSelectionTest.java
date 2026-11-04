// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.Instrumentation;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.action.ViewActions.click;

@RunWith(AndroidJUnit4.class)
public final class StackSelectionTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private interface Condition { boolean ready(); }
    private void await(Condition condition) {
        long until=SystemClock.elapsedRealtime()+15000;
        do {
            AtomicBoolean ready=new AtomicBoolean(); instrumentation.runOnMainSync(() -> ready.set(condition.ready()));
            if(ready.get()) return; SystemClock.sleep(25);
        } while(SystemClock.elapsedRealtime()<until);
        fail("Timed out waiting for stack UI");
    }
    static MainActivity.Session session(MainActivity activity) {
        try {
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("session"); field.setAccessible(true);
            return (MainActivity.Session)field.get(activity);
        } catch(ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    static void finish(MainActivity activity) throws InterruptedException {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        MainActivity.Session session=session(activity);
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        assertTrue("Activity worker must finish before the next test",session.worker.awaitTermination(10,TimeUnit.SECONDS));
    }
    private View tagged(MainActivity activity,String tag) { return activity.getWindow().getDecorView().findViewWithTag(tag); }
    private Button button(View view,String text) {
        if(view instanceof Button && ((Button)view).getText().toString().equals(text)) return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button found=button(((ViewGroup)view).getChildAt(i),text); if(found!=null) return found;
        }
        return null;
    }
    private void choose(String text) {
        onView(withText(text)).inRoot(isPlatformPopup()).perform(click());
    }
    @Test public void selectionCopyDropAndUndoOperateOnWholeSelection() throws Exception {
        Context context=instrumentation.getTargetContext(); CalculatorStore store=new CalculatorStore(context.getNoBackupFilesDir());
        File seed=File.createTempFile("selection-test-",".state",context.getNoBackupFilesDir());
        RpnCalculator calculator=new RpnCalculator(); for(String key:new String[]{"1","ENTER","2","ENTER","3","ENTER"}) calculator.key(key);
        Files.write(seed.toPath(),calculator.save()); store.save(seed.getAbsolutePath());
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        MainActivity.Session session=session(activity);
        try {
            await(() -> !session.busy && tagged(activity,"stack-label:2")!=null);
            // A mode change must not invalidate listeners on otherwise unchanged stack rows.
            instrumentation.runOnMainSync(() -> {
                Button angle=null; for(String label:new String[]{"RAD","DEG","GRAD"}) { angle=button(activity.getWindow().getDecorView(),label); if(angle!=null) break; }
                assertNotNull(angle); angle.performClick();
            });
            await(() -> !session.busy);
            instrumentation.runOnMainSync(() -> tagged(activity,"stack-label:0").performLongClick());
            await(() -> tagged(activity,"stack-checkbox:2")!=null);
            instrumentation.runOnMainSync(() -> {
                assertTrue(((CheckBox)tagged(activity,"stack-checkbox:0")).isChecked());
                assertFalse(((CheckBox)tagged(activity,"stack-checkbox:1")).isChecked());
                tagged(activity,"stack-checkbox:2").performClick();
            });
            instrumentation.runOnMainSync(() -> tagged(activity,"stack-value:1").performLongClick()); choose("Copy");
            ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            await(() -> clipboard.getPrimaryClip()!=null && "1\n3".contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()));
            instrumentation.runOnMainSync(() -> tagged(activity,"stack-value:1").performLongClick()); choose("Drop");
            await(() -> !session.busy && tagged(activity,"stack-value:1")==null && tagged(activity,"stack-value:0")!=null && ((TextView)tagged(activity,"stack-value:0")).getText().toString().equals("2") && button(activity.getWindow().getDecorView(),"Undo")!=null);
            instrumentation.runOnMainSync(() -> { assertNull(tagged(activity,"stack-checkbox:0")); button(activity.getWindow().getDecorView(),"Undo").performClick(); });
            await(() -> !session.busy && tagged(activity,"stack-value:2")!=null);
            instrumentation.runOnMainSync(() -> tagged(activity,"stack-value:1").performLongClick()); choose("Copy");
            await(() -> clipboard.getPrimaryClip()!=null && "2".contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()));
            instrumentation.runOnMainSync(() -> tagged(activity,"stack-value:1").performLongClick()); choose("Drop");
            await(() -> !session.busy && tagged(activity,"stack-value:2")==null && tagged(activity,"stack-value:1")!=null && ((TextView)tagged(activity,"stack-value:1")).getText().toString().equals("3"));
            instrumentation.runOnMainSync(() -> {
                tagged(activity,"stack-label:0").performLongClick(); assertNotNull(tagged(activity,"stack-checkbox:0"));
                activity.onBackPressed(); assertNull(tagged(activity,"stack-checkbox:0")); assertFalse(activity.isFinishing());
            });
        } finally { finish(activity); store.clear(); store.cleanWorkFiles(null); seed.delete(); }
    }
}
