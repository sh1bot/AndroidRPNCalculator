// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Number entry and edit history, independent of arithmetic values and the stack. */
public final class EntryEditor {
    public static final int MAX_ENTRY_LENGTH = 4096;
    String entry = "";
    boolean editing, raw;
    int selectionStart, selectionEnd;
    NumberText.Profile inputProfile = new NumberText.Profile('.', ',', 3, 3);
    NumberText.Profile rawProfile = inputProfile;
    final Deque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    static final class State {
        final String entry;
        final boolean editing, raw;
        final int start, end;
        final NumberText.Profile profile;

        State(
                String entry,
                boolean editing,
                boolean raw,
                int start,
                int end,
                NumberText.Profile profile) {
            this.entry = entry;
            this.editing = editing;
            this.raw = raw;
            this.start = start;
            this.end = end;
            this.profile = profile;
        }

        State(EntryEditor e) {
            this(e.entry, e.editing, e.raw, e.selectionStart, e.selectionEnd, e.rawProfile);
        }

        boolean same(EntryEditor e) {
            return entry.equals(e.entry)
                    && editing == e.editing
                    && raw == e.raw
                    && start == e.selectionStart
                    && end == e.selectionEnd
                    && (!raw || sameProfile(profile, e.rawProfile));
        }
    }

    void restore(State state) {
        entry = state.entry;
        editing = state.editing;
        raw = state.raw;
        selectionStart = state.start;
        selectionEnd = state.end;
        rawProfile = state.profile;
    }

    private void remember(State before) {
        if (before.same(this)) return;
        redo.clear();
        undo.push(before);
        if (undo.size() > 100) undo.removeLast();
    }

    static boolean sameProfile(NumberText.Profile a, NumberText.Profile b) {
        return a.decimal == b.decimal
                && a.grouping == b.grouping
                && a.primary == b.primary
                && a.secondary == b.secondary
                && a.radix == b.radix;
    }

    public int undoCount() {
        return undo.size();
    }

    public int redoCount() {
        return redo.size();
    }

    public void clearUndo() {
        undo.clear();
        redo.clear();
    }

    EntryEditor copy() {
        EntryEditor result = new EntryEditor();
        result.inputProfile = inputProfile;
        result.restore(new State(this));
        result.undo.addAll(undo);
        result.redo.addAll(redo);
        return result;
    }

    boolean same(EntryEditor other) {
        return new State(this).same(other);
    }

    public EntryEditor entryForCommit() {
        EntryEditor result = new EntryEditor();
        result.inputProfile = inputProfile;
        result.restore(new State(this));
        for (State state : undo)
            if (!state.editing) {
                result.undo.push(state);
                break;
            }
        return result;
    }

    /** Validate and canonicalize on the caller before crossing the arithmetic boundary. */
    public EntryEditor prepared() {
        EntryEditor result = entryForCommit();
        result.resolveRaw();
        if (result.raw) NumberText.analyze(result.entry, result.rawProfile).canonical();
        if (!result.entry.isEmpty()) RpnCalculator.parse(result.entry, result.inputProfile.radix);
        return result;
    }

    public boolean key(String key) {
        key = Command.entryKey(key, editing);
        if (key == null) return true;
        String direction = Command.cursorDirection(key);
        if (direction != null) {
            moveCursor(direction, key.startsWith("SELECT_"));
            return true;
        }
        if (key.equals(Command.UNDO) || key.equals(Command.REDO)) {
            Deque<State> from = key.equals(Command.UNDO) ? undo : redo;
            Deque<State> to = key.equals(Command.UNDO) ? redo : undo;
            if (!from.isEmpty()) {
                to.push(new State(this));
                restore(from.pop());
            }
            return true;
        }
        State before = new State(this);
        boolean handled = applyEntryKey(key, Command.clearsEntry(key));
        if (handled) remember(before);
        return handled;
    }

    public void pasteClipboard(String text, NumberText.Profile locale) {
        pasteText(text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 ? text.trim() : text, locale);
    }

    public String entry() {
        return entry;
    }

    public boolean isEditing() {
        return editing;
    }

    public boolean rawEntry() {
        return raw;
    }

    public int selectionStart() {
        return selectionStart;
    }

    public int selectionEnd() {
        return selectionEnd;
    }

    public void select(int start, int end) {
        selectionStart = Math.max(0, Math.min(entry.length(), start));
        selectionEnd = Math.max(0, Math.min(entry.length(), end));
    }

    public void setInputProfile(NumberText.Profile profile) {
        inputProfile = Objects.requireNonNull(profile);
    }

    public String entryProblem() {
        if (!editing || entry.isEmpty()) return "";
        if (raw) return NumberText.analyze(entry, rawProfile).ambiguous() ? "Ambiguous" : "Invalid";
        try {
            RpnCalculator.parse(entry, inputProfile.radix);
            return "";
        } catch (IllegalArgumentException ex) {
            return "Invalid";
        }
    }

    public void pasteText(String text, NumberText.Profile locale) {
        checkLength(text);
        State before = new State(this);
        entry = text;
        editing = true;
        raw = true;
        rawProfile = locale;
        select(entry.length(), entry.length());
        resolveRaw();
        remember(before);
    }

    public void editText(
            String text,
            int start,
            int end,
            boolean rawText,
            boolean explicitDecimal,
            NumberText.Profile profile) {
        checkLength(text);
        State before = new State(this);
        entry = text;
        editing = true;
        raw = rawText;
        inputProfile = profile;
        if (!rawText) rawProfile = profile;
        select(start, end);
        if (raw && explicitDecimal) resolveExplicitDecimal();
        resolveRaw();
        remember(before);
    }

    static void checkLength(String text) {
        if (text == null || text.length() > MAX_ENTRY_LENGTH)
            throw CalculationError.invalid(CalculationError.Code.ENTRY_TOO_LONG, MAX_ENTRY_LENGTH);
    }

    void resolveRaw() {
        if (entry.isEmpty()) {
            raw = false;
            return;
        }
        if (!raw) return;
        NumberText.Analysis a = NumberText.analyze(entry, rawProfile);
        if (a.valid() && !a.ambiguous()) {
            entry = a.canonical();
            raw = false;
            select(selectionStart, selectionEnd);
        }
    }

    private void resolveExplicitDecimal() {
        try {
            entry = NumberText.selected(entry, inputProfile);
            raw = false;
            select(selectionStart, selectionEnd);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void replaceSelection(String text) {
        int start = Math.min(selectionStart, selectionEnd),
                end = Math.max(selectionStart, selectionEnd);
        String result = entry.substring(0, start) + text + entry.substring(end);
        checkLength(result);
        entry = result;
        select(start + text.length(), start + text.length());
    }

    public void moveCursor(String direction, boolean extend) {
        if (!editing) return;
        int caret = selectionEnd, target = caret;
        if (direction.equals("HOME")) target = 0;
        else if (direction.equals("END")) target = entry.length();
        else if (direction.equals("LEFT")) {
            if (!extend && selectionStart != selectionEnd)
                target = Math.min(selectionStart, selectionEnd);
            else if (caret > 0) target = entry.offsetByCodePoints(caret, -1);
        } else if (direction.equals("RIGHT")) {
            if (!extend && selectionStart != selectionEnd)
                target = Math.max(selectionStart, selectionEnd);
            else if (caret < entry.length()) target = entry.offsetByCodePoints(caret, 1);
        }
        select(extend ? selectionStart : target, target);
    }

    private String entryWithoutSelection() {
        return entry.substring(0, Math.min(selectionStart, selectionEnd))
                + entry.substring(Math.max(selectionStart, selectionEnd));
    }

    public static boolean isEntryKey(String key) {
        return key.length() == 1
                && (key.charAt(0) == '.' || Character.digit(key.charAt(0), 16) >= 0);
    }

    boolean applyEntryKey(String key, boolean clearsEntry) {
        if (isEntryKey(key)) {
            editing = true;
            if (key.equals(".")) {
                if (raw) {
                    replaceSelection(Character.toString(inputProfile.decimal));
                    resolveExplicitDecimal();
                } else {
                    String remaining = entryWithoutSelection();
                    if (remaining.contains(".") || hasExponent(remaining)) return true;
                    replaceSelection(entry.isEmpty() || entry.equals("-") ? "0." : ".");
                }
            } else {
                int digit = Character.digit(key.charAt(0), inputProfile.radix);
                if (digit < 0) return true;
                replaceSelection(
                        Character.toString(
                                Character.toUpperCase(
                                        Character.forDigit(digit, inputProfile.radix))));
            }
            resolveRaw();
        } else if (key.equals("HOLD_DECIMAL")) {
            String remaining = entryWithoutSelection();
            if (hasExponent(remaining)) return true;
            replaceSelection(inputProfile.radix == 10 ? "e" : "P");
            resolveRaw();
        } else if (key.equals("BACK") || key.equals("DROP") || key.equals("DELETE")) {
            if (editing) {
                if (selectionStart != selectionEnd) replaceSelection("");
                else if (key.equals("DELETE")) {
                    if (selectionStart < entry.length()) {
                        int start = selectionStart;
                        select(start, entry.offsetByCodePoints(start, 1));
                        replaceSelection("");
                    }
                } else if (selectionStart > 0) {
                    int end = selectionStart;
                    select(entry.offsetByCodePoints(end, -1), end);
                    replaceSelection("");
                }
                resolveRaw();
            } else return false;
        } else if (key.equals("±") && editing) {
            boolean negative = entry.startsWith("-") || entry.startsWith("−");
            entry = negative ? entry.substring(1) : "-" + entry;
            select(selectionStart + (negative ? -1 : 1), selectionEnd + (negative ? -1 : 1));
            resolveRaw();
        } else if (clearsEntry) {
            entry = "";
            if (!key.equals("CE")) editing = false;
            raw = false;
            select(0, 0);
        } else return false;
        return true;
    }

    private boolean hasExponent(String text) {
        return text.indexOf('P') >= 0
                || inputProfile.radix == 10 && (text.indexOf('e') >= 0 || text.indexOf('E') >= 0);
    }

    static void writeProfile(DataOutputStream out, NumberText.Profile p) throws IOException {
        out.writeChar(p.decimal);
        out.writeChar(p.grouping);
        out.writeInt(p.primary);
        out.writeInt(p.secondary);
        out.writeInt(p.radix);
    }

    static NumberText.Profile readProfile(DataInputStream in, boolean legacy) throws IOException {
        return new NumberText.Profile(
                in.readChar(),
                in.readChar(),
                in.readInt(),
                in.readInt(),
                legacy ? 10 : in.readInt());
    }

    public byte[] save() throws IOException {
        return save(CalculationLimits.SAVED_BYTES);
    }

    public byte[] save(int limit) throws IOException {
        byte[] bytes = encode();
        while (bytes.length > limit && (!undo.isEmpty() || !redo.isEmpty())) {
            if (!undo.isEmpty()) undo.removeLast();
            else redo.removeLast();
            bytes = encode();
        }
        CalculationLimits.require(bytes.length, limit);
        return bytes;
    }

    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x45445432);
        writeProfile(out, inputProfile);
        writeState(out, new State(this));
        out.writeInt(undo.size());
        out.writeInt(redo.size());
        for (State state : undo) writeState(out, state);
        for (State state : redo) writeState(out, state);
        return bytes.toByteArray();
    }

    public static EntryEditor load(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int magic = in.readInt();
        boolean legacy = magic == 0x45445431;
        if (!legacy && magic != 0x45445432) throw new IOException("Unknown entry format");
        EntryEditor result = new EntryEditor();
        result.inputProfile = readProfile(in, legacy);
        result.restore(readState(in, legacy));
        int undos = in.readInt(), redos = in.readInt();
        if (undos < 0 || redos < 0 || undos > 100 || redos > 100 || undos + redos > 100)
            throw new IOException("Invalid entry history");
        for (int i = 0; i < undos; i++) result.undo.addLast(readState(in, legacy));
        for (int i = 0; i < redos; i++) result.redo.addLast(readState(in, legacy));
        if (in.available() != 0) throw new IOException("Trailing entry data");
        return result;
    }

    static void writeState(DataOutputStream out, State state) throws IOException {
        out.writeUTF(state.entry);
        out.writeBoolean(state.editing);
        out.writeBoolean(state.raw);
        out.writeInt(state.start);
        out.writeInt(state.end);
        writeProfile(out, state.profile);
    }

    static State readState(DataInputStream in, boolean legacy) throws IOException {
        String text = in.readUTF();
        boolean editing = in.readBoolean(), raw = in.readBoolean();
        int start = in.readInt(), end = in.readInt();
        NumberText.Profile profile = readProfile(in, legacy);
        if (text.length() > MAX_ENTRY_LENGTH
                || start < 0
                || end < 0
                || start > text.length()
                || end > text.length()
                || !editing && !text.isEmpty()) throw new IOException("Invalid entry state");
        return new State(text, editing, raw, start, end, profile);
    }
}
