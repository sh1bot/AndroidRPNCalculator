// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** One request at a time. Cancelling destroys the remote process, never the UI process. */
final class RemoteCalculator {
    // Startup (bind + HELLO) must complete promptly; arithmetic itself stays unbounded.
    private static final long HELLO_TIMEOUT_MS = 5000;
    static final class StartupTimeout extends IllegalStateException {
        StartupTimeout(String message) {
            super(message);
        }
    }

    static final class UserFailure extends IllegalStateException {
        UserFailure(String message) {
            super(message);
        }

        UserFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class InvalidCheckpoint extends IllegalStateException {
        InvalidCheckpoint(String message) {
            super(message);
        }
    }

    private final Context context;
    private final boolean temporary;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Messenger remote;
    private CompletableFuture<Void> workerExit = CompletableFuture.completedFuture(null);
    private ServiceConnection connection;
    private int pid, generation, nextRequest;
    private boolean closed;
    private final String sessionId = java.util.UUID.randomUUID().toString();
    private String remoteSnapshotId;
    private CompletableFuture<Bundle> pending;
    private Bundle pendingRequest;
    private Runnable helloWatchdog;
    private volatile java.util.function.Consumer<CalculationError.Code> noticeListener;
    private final Messenger replies =
            new Messenger(
                    new Handler(
                            Looper.getMainLooper(),
                            message -> {
                                synchronized (this) {
                                    if (message.arg2 != generation) {
                                        return true;
                                    }
                                    if (message.what == CalculationService.HELLO) {
                                        pid = message.arg1;
                                        if (helloWatchdog != null) {
                                            main.removeCallbacks(helloWatchdog);
                                            helloWatchdog = null;
                                        }
                                        if (pending != null) sendPending();
                                    } else if (message.what == CalculationService.EVALUATE
                                            && pending != null
                                            && message.arg1 == nextRequest) {
                                        pending.complete(message.getData());
                                        pending = null;
                                        pendingRequest = null;
                                    } else if (message.what == CalculationService.NOTE
                                            && pending != null
                                            && message.arg1 == nextRequest) {
                                        java.util.function.Consumer<CalculationError.Code>
                                                listener = noticeListener;
                                        String code = message.getData().getString("errorCode");
                                        if (listener != null && code != null)
                                            listener.accept(CalculationError.Code.valueOf(code));
                                    }
                                }
                                return true;
                            }));

    RemoteCalculator(Context context, boolean temporary) {
        this.context = context.getApplicationContext();
        this.temporary = temporary;
    }

    void setNoticeListener(java.util.function.Consumer<CalculationError.Code> listener) {
        noticeListener = listener;
    }

    DisplayResult evaluate(CalculationRequest request) {
        if (!temporary) DeviceAccess.requireUnlocked(context);
        CompletableFuture<Bundle> future = new CompletableFuture<>();
        String inputId = request.inputId;
        if (request.input != null && inputId == null)
            inputId = java.util.UUID.randomUUID().toString();
        int epoch;
        boolean reuse;
        synchronized (this) {
            epoch = generation;
            reuse = inputId != null && inputId.equals(remoteSnapshotId);
        }
        Bundle outgoing = new Bundle();
        try {
            outgoing.putByteArray("payload", request.encode(sessionId, inputId, !reuse));
        } catch (IOException ex) {
            throw new UserFailure(context.getString(R.string.result_unreadable), ex);
        }
        boolean cacheIntact = false;
        try {
            synchronized (this) {
                if (closed || epoch != generation || Thread.currentThread().isInterrupted())
                    throw new com.hp.creals.CR.AbortedException();
                pending = future;
                pendingRequest = outgoing;
                ++nextRequest;
                if (remote != null && pid != 0) sendPending();
                else if (connection == null) bind();
            }
            Bundle response = future.get();
            cacheIntact = response.getBoolean("cacheIntact");
            return readResponse(response, epoch);
        } catch (InterruptedException ex) {
            cancel();
            Thread.currentThread().interrupt();
            throw new com.hp.creals.CR.AbortedException();
        } catch (ExecutionException ex) {
            throw new UserFailure(context.getString(R.string.process_stopped), ex);
        } catch (IOException ex) {
            forgetSnapshot();
            throw new UserFailure(context.getString(R.string.result_unreadable), ex);
        } catch (RuntimeException ex) {
            if (!cacheIntact) forgetSnapshot();
            throw ex;
        }
    }

    private DisplayResult readResponse(Bundle response, int epoch) throws IOException {
        if (response.getBoolean("deviceLocked")) throw new DeviceAccess.Locked();
        if (response.getBoolean("invalidCheckpoint"))
            throw new InvalidCheckpoint("Invalid checkpoint");
        if (CalculationError.Code.TRANSFER_TOO_LARGE.name().equals(response.getString("errorCode")))
            throw CalculationError.invalid(CalculationError.Code.TRANSFER_TOO_LARGE);
        if (response.containsKey("errorCode"))
            throw new UserFailure(
                    ErrorMessages.fromCode(
                            context,
                            CalculationError.Code.valueOf(response.getString("errorCode")),
                            response.getInt("errorArgument")));
        DisplayResult result = DisplayResult.decode(response.getByteArray("payload"));
        synchronized (this) {
            if (closed || epoch != generation) throw new com.hp.creals.CR.AbortedException();
            if (result.state != null) remoteSnapshotId = result.checkpoint;
        }
        return result;
    }

    private synchronized void forgetSnapshot() {
        remoteSnapshotId = null;
    }

    private void sendPending() {
        Message message = Message.obtain(null, CalculationService.EVALUATE);
        message.replyTo = replies;
        message.arg1 = nextRequest;
        message.arg2 = generation;
        message.setData(pendingRequest);
        try {
            remote.send(message);
        } catch (RemoteException ex) {
            fail(ex);
        }
    }

    private void bind() {
        final int epoch = generation;
        connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        synchronized (RemoteCalculator.this) {
                            if (epoch != generation) return;
                            remote = new Messenger(binder);
                            Message hello = Message.obtain(null, CalculationService.HELLO);
                            hello.replyTo = replies;
                            hello.arg2 = epoch;
                            try {
                                remote.send(hello);
                            } catch (RemoteException ex) {
                                fail(ex);
                            }
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        disconnected();
                    }

                    @Override
                    public void onBindingDied(ComponentName name) {
                        disconnected();
                    }

                    @Override
                    public void onNullBinding(ComponentName name) {
                        disconnected();
                    }

                    private void disconnected() {
                        synchronized (RemoteCalculator.this) {
                            if (epoch == generation)
                                fail(new IllegalStateException("Calculation process exited"));
                        }
                    }
                };
        ServiceConnection binding = connection;
        Runnable watchdog =
                () -> {
                    synchronized (RemoteCalculator.this) {
                        if (epoch == generation && pending != null && pid == 0)
                            fail(
                                    new StartupTimeout(
                                            context.getString(R.string.process_start_failed)));
                    }
                };
        helloWatchdog = watchdog;
        main.postDelayed(watchdog, HELLO_TIMEOUT_MS);
        Runnable startBinding =
                () -> {
                    synchronized (RemoteCalculator.this) {
                        if (epoch != generation) return;
                        if (!context.bindService(
                                new Intent(
                                        context,
                                        temporary
                                                ? LockedCalculationService.class
                                                : CalculationService.class),
                                binding,
                                Context.BIND_AUTO_CREATE))
                            fail(
                                    new IllegalStateException(
                                            context.getString(R.string.process_start_failed)));
                    }
                };
        workerExit.thenRun(() -> main.post(startBinding));
    }

    private void fail(Exception error) {
        if (pending != null) pending.completeExceptionally(error);
        pending = null;
        pendingRequest = null;
        disconnect(true);
    }

    synchronized void cancel() {
        if (pending != null) pending.cancel(false);
        pending = null;
        pendingRequest = null;
        disconnect(true);
    }

    synchronized void close() {
        if (closed) return;
        closed = true;
        if (pending != null) cancel();
        else disconnect(false);
    }

    private void disconnect(boolean terminate) {
        if (helloWatchdog != null) {
            main.removeCallbacks(helloWatchdog);
            helloWatchdog = null;
        }
        ++generation;
        remoteSnapshotId = null;
        int process = pid;
        ServiceConnection binding = connection;
        IBinder binder = remote == null ? null : remote.getBinder();
        CompletableFuture<Void> exit = new CompletableFuture<>();
        if (terminate && process != 0) workerExit = exit;
        pid = 0;
        remote = null;
        connection = null;
        if (binding == null && process == 0) return;
        // Unbind before killing and wait for binder death before binding again.
        // Otherwise a new connection can attach to the dying worker and trigger restart backoff.
        Runnable release =
                () -> {
                    if (binding != null) {
                        try {
                            context.unbindService(binding);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (terminate && process != 0) {
                        try {
                            binder.linkToDeath(() -> exit.complete(null), 0);
                            android.os.Process.killProcess(process);
                        } catch (RemoteException alreadyDead) {
                            exit.complete(null);
                        }
                    }
                };
        if (Looper.myLooper() == main.getLooper()) release.run();
        else main.post(release);
    }
}
