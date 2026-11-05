// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import com.android.calculator2.UnifiedReal;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Arithmetic runs in :calculation, so Cancel can terminate even non-interruptible work. */
public final class CalculationService extends Service {
    static final int HELLO=1, EVALUATE=2;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private RpnCalculator cached;
    private String cachedPath, cachedStackId;
    private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper(),message -> {
        if(message.what==HELLO) {
            Message reply=Message.obtain(null,HELLO); reply.arg1=android.os.Process.myPid(); reply.arg2=message.arg2;
            try { message.replyTo.send(reply); } catch(RemoteException ignored) {}
        } else if(message.what==EVALUATE) {
            Bundle request=new Bundle(message.getData()); Messenger reply=message.replyTo;
            worker.execute(() -> evaluate(request,reply));
        }
        return true;
    }));
    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }
    @Override public boolean onUnbind(Intent intent) {
        // Do not leave an abandoned arithmetic thread consuming CPU after its client disappears.
        android.os.Process.killProcess(android.os.Process.myPid()); return false;
    }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    private boolean sameStack(RpnCalculator a,RpnCalculator b) {
        List<UnifiedReal> left=a.values(),right=b.values();
        if(left.size()!=right.size()) return false;
        for(int i=0;i<left.size();i++) if(left.get(i)!=right.get(i)) return false;
        return true;
    }
    private void evaluate(Bundle request,Messenger client) {
        Bundle response=new Bundle();
        File snapshot=null, presentation=null;
        try {
            String input=request.getString("input"), key=request.getString("key");
            if(cached==null || !Objects.equals(input,cachedPath)) {
                try { cached=input==null ? new RpnCalculator() : RpnCalculator.load(Files.readAllBytes(new File(input).toPath())); }
                catch(IOException ex) {
                    response.putBoolean("invalidCheckpoint",true);
                    throw new IOException("Saved calculation is damaged or from an unsupported version",ex);
                }
                if(input==null) cached.setAngleUnit(RpnCalculator.AngleUnit.valueOf(request.getString("angle")));
                cachedPath=input; cachedStackId=UUID.randomUUID().toString();
            }
            RpnCalculator candidate=cached.copy();
            Locale locale=Locale.forLanguageTag(request.getString("locale"));
            int numberMode=request.getInt("numberMode"), fractionMode=request.getInt("fractionMode");
            ValueFormatter.Notation notation=ValueFormatter.Notation.valueOf(request.getString("notation"));
            LocaleNumbers local=new LocaleNumbers(locale);
            LocaleNumbers format=numberMode==1 ? new LocaleNumbers(locale,true) : numberMode==2 ? new LocaleNumbers(locale,false,false) : local;
            candidate.setInputProfile(format.profile());
            if(key.equals("RESTORE") || key.equals("CHECKPOINT")) { /* No arithmetic operation. */ }
            else if(key.equals("FORMAT")) notation=notation==ValueFormatter.Notation.SCIENTIFIC ? ValueFormatter.Notation.ENGINEERING : ValueFormatter.Notation.SCIENTIFIC;
            else if(key.equals("NUMBER_FORMAT")) numberMode=(numberMode+1)%3;
            else if(key.equals("FRACTION")) fractionMode=FractionMode.next(fractionMode);
            else if(key.startsWith("DROP_ROWS:") || key.startsWith("PICK_ROWS:")) {
                String[] parts=key.substring(10).split(","); int[] rows=new int[parts.length];
                for(int i=0;i<parts.length;i++) rows[i]=Integer.parseInt(parts[i]);
                if(key.startsWith("DROP_ROWS:")) candidate.dropRows(rows); else candidate.pickRows(rows);
            }
            else if(key.startsWith("PASTE_RAW:")) candidate.pasteText(key.substring(10),local.profile());
            else if(key.startsWith("SELECT:")) {
                String[] parts=key.split(":"); candidate.select(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
            } else if(key.startsWith("EDIT:")) {
                String[] parts=key.split(":",6);
                candidate.editText(parts[5],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Boolean.parseBoolean(parts[3]),Boolean.parseBoolean(parts[4]),format.profile());
            } else candidate.key(key);
            String stackId=sameStack(cached,candidate) ? cachedStackId : UUID.randomUUID().toString();
            ArrayList<String> rows=new ArrayList<>(candidate.formattedValues(notation,fractionMode));
            ArrayList<String> copies=new ArrayList<>(),compact=new ArrayList<>();
            for(UnifiedReal value:candidate.values()) {
                copies.add(ValueFormatter.clipboard(value,notation,fractionMode));
                compact.add(ValueFormatter.format(value,false,notation,fractionMode));
            }
            snapshot=File.createTempFile("calculation-",".state",getNoBackupFilesDir());
            Files.write(snapshot.toPath(),candidate.save());
            Bundle display=new Bundle();
            display.putString("stackId",stackId);
            display.putStringArrayList("rows",rows); display.putStringArrayList("clipboard",copies); display.putStringArrayList("compactRows",compact);
            display.putString("entry",candidate.entry()); display.putString("problem",candidate.entryProblem());
            display.putBoolean("editing",candidate.isEditing()); display.putBoolean("raw",candidate.rawEntry());
            display.putInt("start",candidate.selectionStart()); display.putInt("end",candidate.selectionEnd());
            display.putString("angle",candidate.angleUnit().name()); display.putString("notation",notation.name());
            display.putInt("numberMode",numberMode); display.putInt("fractionMode",fractionMode);
            display.putString("checkpoint",snapshot.getAbsolutePath());
            // Only paths cross Binder; large stacks must not hit Binder's transaction-size limit.
            presentation=File.createTempFile("calculation-",".display",getNoBackupFilesDir());
            DisplayResult.write(presentation,display);
            response.putString("presentation",presentation.getAbsolutePath());
            cached=candidate; cachedPath=snapshot.getAbsolutePath(); cachedStackId=stackId;
        } catch(OutOfMemoryError ex) {
            android.os.Process.killProcess(android.os.Process.myPid()); return;
        } catch(IOException | RuntimeException | StackOverflowError ex) {
            if(snapshot!=null) snapshot.delete(); if(presentation!=null) presentation.delete();
            response.putString("error",ex.getMessage()==null ? "Calculation failed" : ex.getMessage());
        }
        Message result=Message.obtain(null,EVALUATE); result.arg1=request.getInt("requestId"); result.arg2=request.getInt("generation"); result.setData(response);
        try { client.send(result); } catch(RemoteException ignored) {}
    }
}
