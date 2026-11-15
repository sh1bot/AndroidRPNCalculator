// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.os.FileObserver;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Polling, view lookup and filesystem watching shared by the instrumentation tests. */
final class UiTestSupport {
    /** Polls {@code condition} on the main thread until it holds, for up to 15 seconds. */
    static void await(String description, BooleanSupplier condition) {
        await(description, 15_000, condition);
    }

    /**
     * Polls {@code condition} on the main thread until it holds; if {@code timeoutMillis}
     * elapses first, the test fails with exactly {@code description} as the message.
     */
    static void await(String description, long timeoutMillis, BooleanSupplier condition) {
        poll(description, timeoutMillis, condition);
    }

    /**
     * Like {@link #await(String, BooleanSupplier)}, but the failure message appends the
     * calling frame after {@code description}, as the former per-class helpers did.
     */
    static void awaitHere(String description, BooleanSupplier condition) {
        poll(description + " at " + new Throwable().getStackTrace()[1], 15_000, condition);
    }

    private static void poll(String failure, long timeoutMillis, BooleanSupplier condition) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        long until = SystemClock.elapsedRealtime() + timeoutMillis;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < until);
        fail(failure);
    }

    /** Returns the first button in the hierarchy whose text equals {@code text}, or null. */
    static Button findButton(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button found = findButton(((ViewGroup) view).getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }

    /**
     * Starts watching {@code directory}; each event whose path starts with one of {@code
     * prefixes} or equals one of {@code exactNames} increments {@code accesses} once.
     */
    static FileObserver watch(
            File directory, AtomicInteger accesses, String[] prefixes, String... exactNames) {
        FileObserver observer =
                new FileObserver(directory.getAbsolutePath(), FileObserver.ALL_EVENTS) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path == null) return;
                        for (String prefix : prefixes)
                            if (path.startsWith(prefix)) {
                                accesses.incrementAndGet();
                                return;
                            }
                        for (String name : exactNames)
                            if (path.equals(name)) {
                                accesses.incrementAndGet();
                                return;
                            }
                    }
                };
        observer.startWatching();
        return observer;
    }
}
