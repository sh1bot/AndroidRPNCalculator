// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** Bounded snapshots shared by entry editing and arithmetic. */
final class UndoHistory<S> {
    final Deque<S> undo, redo;
    private final Runnable afterPush;

    UndoHistory() {
        this(new ArrayDeque<>(), new ArrayDeque<>(), null);
    }

    UndoHistory(Deque<S> undo, Deque<S> redo, Runnable afterPush) {
        this.undo = undo;
        this.redo = redo;
        this.afterPush = afterPush;
    }

    void remember(S before) {
        undo.push(before);
        if (afterPush != null) afterPush.run();
        redo.clear();
        trim(100);
    }

    void move(boolean backwards, S current, Consumer<S> restore) {
        Deque<S> from = backwards ? undo : redo, to = backwards ? redo : undo;
        if (from.isEmpty()) return;
        to.push(current);
        // Keep the target owned until the current state has retained its expression references.
        restore.accept(from.peek());
        from.pop();
    }

    void trim(int limit) {
        while (undo.size() + redo.size() > limit) evictOldest();
    }

    boolean evictOldest() {
        if (!undo.isEmpty()) undo.removeLast();
        else if (!redo.isEmpty()) redo.removeLast();
        else return false;
        return true;
    }

    void clear() {
        undo.clear();
        redo.clear();
    }
}
