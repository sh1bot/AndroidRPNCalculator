// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;

/** One request at a time. Cancelling destroys the remote process, never the UI process. */
final class RemoteCalculator {
    static final class InvalidCheckpoint extends IllegalStateException {
        InvalidCheckpoint(String message) { super(message); }
    }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Messenger remote;
    private ServiceConnection connection;
    private int pid, generation, nextRequest;
    private boolean closed;
    private CompletableFuture<Bundle> pending;
    private Bundle pendingRequest;
    private final Messenger replies=new Messenger(new Handler(Looper.getMainLooper(),message -> {
        synchronized(this) {
            if(message.arg2!=generation) return true;
            if(message.what==CalculationService.HELLO) {
                pid=message.arg1;
                if(pending!=null) sendPending();
            } else if(message.what==CalculationService.EVALUATE && pending!=null && message.arg1==nextRequest) {
                pending.complete(message.getData()); pending=null; pendingRequest=null;
            }
        }
        return true;
    }));
    RemoteCalculator(Context context) { this.context=context.getApplicationContext(); }
    Bundle evaluate(Bundle request) {
        CompletableFuture<Bundle> future=new CompletableFuture<>();
        synchronized(this) {
            if(closed) throw new com.hp.creals.CR.AbortedException();
            pending=future; pendingRequest=new Bundle(request); pendingRequest.putInt("requestId",++nextRequest);
            if(remote!=null && pid!=0) sendPending(); else if(connection==null) bind();
        }
        try {
            Bundle response=future.get();
            if(response.getBoolean("invalidCheckpoint")) throw new InvalidCheckpoint(response.getString("error"));
            if(response.containsKey("error")) throw new IllegalStateException(response.getString("error"));
            File file=new File(response.getString("presentation"));
            try { return DisplayResult.read(file); }
            finally { file.delete(); }
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new com.hp.creals.CR.AbortedException(); }
        catch(ExecutionException ex) { throw new IllegalStateException("Calculation process stopped; previous state preserved",ex); }
        catch(IOException ex) { throw new IllegalStateException("Could not read calculation result",ex); }
    }
    private void sendPending() {
        Message message=Message.obtain(null,CalculationService.EVALUATE); message.replyTo=replies;
        pendingRequest.putInt("generation",generation); message.setData(pendingRequest);
        try { remote.send(message); }
        catch(RemoteException ex) { fail(ex); }
    }
    private void bind() {
        final int epoch=generation;
        connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                synchronized(RemoteCalculator.this) {
                    if(epoch!=generation) return;
                    remote=new Messenger(binder);
                    Message hello=Message.obtain(null,CalculationService.HELLO); hello.replyTo=replies; hello.arg2=epoch;
                    try { remote.send(hello); } catch(RemoteException ex) { fail(ex); }
                }
            }
            @Override public void onServiceDisconnected(ComponentName name) { disconnected(); }
            @Override public void onBindingDied(ComponentName name) { disconnected(); }
            @Override public void onNullBinding(ComponentName name) { disconnected(); }
            private void disconnected() {
                synchronized(RemoteCalculator.this) {
                    if(epoch==generation) fail(new IllegalStateException("Calculation process exited"));
                }
            }
        };
        ServiceConnection binding=connection;
        main.post(() -> {
            synchronized(RemoteCalculator.this) {
                if(epoch!=generation) return;
                if(!context.bindService(new Intent(context,CalculationService.class),binding,Context.BIND_AUTO_CREATE))
                    fail(new IllegalStateException("Could not start calculation process"));
            }
        });
    }
    private void fail(Exception error) {
        if(pending!=null) pending.completeExceptionally(error);
        pending=null; pendingRequest=null;
        disconnect();
    }
    synchronized void cancel() {
        if(pending!=null) pending.cancel(false);
        pending=null; pendingRequest=null;
        disconnect();
    }
    synchronized void close() { closed=true; cancel(); }
    private void disconnect() {
        ++generation;
        int process=pid; ServiceConnection binding=connection;
        pid=0; remote=null; connection=null;
        if(process!=0) android.os.Process.killProcess(process);
        if(binding!=null) main.post(() -> { try { context.unbindService(binding); } catch(IllegalArgumentException ignored) {} });
    }
}
