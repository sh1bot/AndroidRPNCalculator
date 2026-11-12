// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.io.IOException;
import java.util.List;

/** Test driver using the same local-editor/completed-draft boundary as CalculatorSession. */
final class TestCalculator {
    RpnCalculator engine;
    private EntryEditor editor;
    private boolean branch;

    TestCalculator() {
        this(new RpnCalculator());
    }

    private TestCalculator(RpnCalculator engine) {
        this.engine = engine;
        editor = engine.detachEntryEditor();
    }

    static TestCalculator load(byte[] bytes) throws IOException {
        return new TestCalculator(RpnCalculator.load(bytes));
    }

    byte[] save() throws IOException {
        return RpnCalculator.saveWithEntry(engine.save(), editor, branch);
    }

    void key(String key) {
        apply(Command.key(key), NumberText.COMPUTER, editor.inputProfile);
    }

    void apply(Command command, NumberText.Profile locale, NumberText.Profile format) {
        String key = command.wire;
        boolean local =
                command.isEntryUpdate()
                        || EntryEditor.isEntryKey(key)
                        || Command.cursorDirection(key) != null
                        || command.is("CE")
                        || command.is("CANCEL_ENTRY")
                        || command.is("HOLD_DECIMAL")
                        || command.is(Command.UNDO) && editor.undoCount() > 0
                        || command.is(Command.REDO) && editor.redoCount() > 0
                        || editor.isEditing()
                                && (command.kind == Command.Kind.HOLD_DIGIT
                                        || Command.cursorDirection(key) != null
                                        || command.is("BACK")
                                        || command.is("DROP")
                                        || command.is("DELETE")
                                        || command.is("HOLD_DROP")
                                        || command.is("SMART_CLEAR")
                                        || command.is("±"));
        if (command.kind == Command.Kind.PASTE_RAW)
            local =
                    java.util.Arrays.stream(command.text.split("\\R", -1))
                                    .filter(line -> !line.trim().isEmpty())
                                    .count()
                            < 2;
        if (local) {
            String before = editor.entry();
            boolean wasEditing = editor.isEditing();
            int undos = editor.undoCount(), redos = editor.redoCount();
            editor.setInputProfile(format);
            command.apply(editor, locale, format);
            if (!command.is(Command.UNDO) && !command.is(Command.REDO))
                branch |=
                        !before.equals(editor.entry())
                                || wasEditing != editor.isEditing()
                                || undos != editor.undoCount()
                                || redos != editor.redoCount();
            return;
        }
        byte[] before;
        try {
            before = engine.save();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        EntryEditor oldEditor = editor;
        List<UnifiedReal> oldStack = engine.values();
        boolean oldBranch = branch;
        if (branch) engine.discardRedo();
        engine.useEntryEditor(
                command.is(Command.UNDO) || command.is(Command.REDO) ? editor : editor.prepared());
        try {
            (command.kind == Command.Kind.PASTE_RAW
                            ? Command.pushValues(ClipboardNumbers.parse(command.text, locale))
                            : command.forEntry(editor.isEditing()))
                    .apply(engine);
            EntryEditor incoming = engine.detachEntryEditor();
            if (!command.is(Command.UNDO)
                    && !command.is(Command.REDO)
                    && engine.sameStack(oldStack)
                    && new EntryEditor.State(oldEditor).same(incoming)) {
                editor = oldEditor;
                branch = oldBranch;
            } else {
                editor = incoming;
                branch = false;
            }
        } catch (RuntimeException failure) {
            EntryEditor incoming = engine.detachEntryEditor();
            if (incoming.entry().equals(oldEditor.entry())
                    && incoming.isEditing() == oldEditor.isEditing()) {
                try {
                    engine = RpnCalculator.load(before);
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
                editor = oldEditor;
                branch = oldBranch;
            } else {
                editor = incoming;
                branch = false;
            }
            throw failure;
        }
    }

    String entry() {
        return editor.entry();
    }

    boolean isEditing() {
        return editor.isEditing();
    }

    boolean rawEntry() {
        return editor.rawEntry();
    }

    String entryProblem() {
        return editor.entryProblem();
    }

    int selectionStart() {
        return editor.selectionStart();
    }

    int selectionEnd() {
        return editor.selectionEnd();
    }

    void select(int start, int end) {
        editor.select(start, end);
    }

    void moveCursor(String direction, boolean extend) {
        editor.moveCursor(direction, extend);
    }

    void setInputProfile(NumberText.Profile profile) {
        editor.setInputProfile(profile);
    }

    void pasteText(String text, NumberText.Profile locale) {
        apply(Command.paste(text), locale, editor.inputProfile);
    }

    void pasteClipboard(String text, NumberText.Profile locale) {
        pasteText(text, locale);
    }

    void editText(
            String text,
            int start,
            int end,
            boolean raw,
            boolean decimal,
            NumberText.Profile profile) {
        apply(Command.edit(text, start, end, raw, decimal), profile, profile);
    }

    void useEntryEditor(EntryEditor entry) {
        editor = entry;
    }

    EntryEditor detachEntryEditor() {
        return editor;
    }

    boolean isEmpty() {
        return engine.values().isEmpty() && editor.entry().isEmpty();
    }

    int undoCount() {
        return engine.undoCount() + editor.undoCount();
    }

    int redoCount() {
        return (branch ? 0 : engine.redoCount()) + editor.redoCount();
    }

    void clearUndo() {
        engine.clearUndo();
        editor.clearUndo();
    }

    void discardRedo() {
        engine.discardRedo();
        editor.redo.clear();
    }

    List<UnifiedReal> values() {
        return engine.values();
    }

    void setAngleUnit(RpnCalculator.AngleUnit unit) {
        engine.setAngleUnit(unit);
    }

    void holdDigit(String digit) {
        apply(Command.holdDigit(digit), NumberText.COMPUTER, editor.inputProfile);
    }

    /**
     * Test the arithmetic row-operation history directly; the UI disallows row selection in entry
     * mode.
     */
    void arithmeticKey(String key) {
        engine.useEntryEditor(editor);
        engine.key(key);
        editor = engine.detachEntryEditor();
        branch = false;
    }

    private void rowAction(Runnable action) {
        if (branch) engine.discardRedo();
        engine.useEntryEditor(editor.entryForCommit());
        action.run();
        editor = engine.detachEntryEditor();
        branch = false;
    }

    void dropRows(int... rows) {
        rowAction(() -> engine.dropRows(rows));
    }

    void pickRows(int... rows) {
        rowAction(() -> engine.pickRows(rows));
    }

    void sumRows(int... rows) {
        rowAction(() -> engine.sumRows(rows));
    }

    void productRows(int... rows) {
        rowAction(() -> engine.productRows(rows));
    }

    void transformRows(Operation operation, int... rows) {
        rowAction(() -> engine.transformRows(operation, rows));
    }

    List<UnifiedReal> selectedValues(int... rows) {
        return engine.selectedValues(rows);
    }

    boolean sameStack(List<UnifiedReal> values) {
        return engine.sameStack(values);
    }
}
