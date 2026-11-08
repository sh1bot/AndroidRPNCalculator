// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Exact stack values plus a cursor-editable, transactional number entry. */
public final class RpnCalculator {
    public static final int MAX_ENTRY_LENGTH = 4096;
    public static final int MAX_PASTE_LENGTH = 65536;
    private List<UnifiedReal> stack = new ArrayList<>();
    private String entry = "";
    private boolean editing, raw;
    private RpnCalculator restoredEditor;
    private int selectionStart, selectionEnd;

    public enum AngleUnit {
        RAD,
        DEG,
        GRAD
    }

    private AngleUnit angleUnit = AngleUnit.RAD;
    private NumberText.Profile inputProfile = new NumberText.Profile('.', ',', 3, 3);
    private NumberText.Profile rawProfile = inputProfile;
    private final IdentityHashMap<UnifiedReal, SavedValue> savedValues = new IdentityHashMap<>();
    private final Deque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    private static final class State {
        final List<UnifiedReal> stack;
        final String entry;
        final boolean editing, raw;
        final int start, end;
        final NumberText.Profile profile;

        State(
                List<UnifiedReal> stack,
                String entry,
                boolean editing,
                boolean raw,
                int start,
                int end,
                NumberText.Profile profile) {
            this.stack = stack;
            this.entry = entry;
            this.editing = editing;
            this.raw = raw;
            this.start = start;
            this.end = end;
            this.profile = profile;
        }

        State(RpnCalculator c) {
            stack = new ArrayList<>(c.stack);
            entry = c.entry;
            editing = c.editing;
            raw = c.raw;
            start = c.selectionStart;
            end = c.selectionEnd;
            profile = c.rawProfile;
        }
    }

    private void restore(State s) {
        stack = new ArrayList<>(s.stack);
        entry = s.entry;
        editing = s.editing;
        raw = s.raw;
        selectionStart = s.start;
        selectionEnd = s.end;
        rawProfile = s.profile;
    }

    private void remember(State s) {
        if (sameStack(s.stack, stack)
                && s.entry.equals(entry)
                && s.editing == editing
                && s.raw == raw
                && s.start == selectionStart
                && s.end == selectionEnd
                && (!raw || sameProfile(s.profile, rawProfile))) return;
        boolean discarded = !redo.isEmpty();
        redo.clear();
        undo.push(s);
        if (undo.size() > 100) {
            undo.removeLast();
            discarded = true;
        }
        if (discarded) pruneSavedValues();
    }

    private static boolean sameProfile(NumberText.Profile a, NumberText.Profile b) {
        return a.decimal == b.decimal
                && a.grouping == b.grouping
                && a.primary == b.primary
                && a.secondary == b.secondary;
    }

    public RpnCalculator copy() {
        RpnCalculator c = new RpnCalculator();
        c.restore(new State(this));
        c.angleUnit = angleUnit;
        c.inputProfile = inputProfile;
        c.undo.addAll(undo);
        c.redo.addAll(redo);
        c.savedValues.putAll(savedValues);
        return c;
    }

    /** Entry-only editor: no arithmetic values or expression graph crosses into the UI. */
    public RpnCalculator detachEntryEditor() {
        if (restoredEditor != null) {
            RpnCalculator editor = restoredEditor;
            restoredEditor = null;
            for (int i = 0; i < editor.undo.size(); i++) undo.removeFirst();
            for (int i = 0; i < editor.redo.size(); i++) redo.removeFirst();
            return editor;
        }
        RpnCalculator editor = new RpnCalculator();
        editor.inputProfile = inputProfile;
        editor.restore(entryState(new State(this), Collections.emptyList()));
        if (editing) {
            moveEntryHistory(undo, editor.undo);
            moveEntryHistory(redo, editor.redo);
        }
        return editor;
    }

    private void moveEntryHistory(Deque<State> source, Deque<State> target) {
        while (!source.isEmpty() && sameStack(source.peek().stack, stack)) {
            State state = source.pop();
            target.addLast(entryState(state, Collections.emptyList()));
            if (!state.editing) break;
        }
    }

    private static State entryState(State entry, List<UnifiedReal> stack) {
        return new State(
                new ArrayList<>(stack),
                entry.entry,
                entry.editing,
                entry.raw,
                entry.start,
                entry.end,
                entry.profile);
    }

    /** Completed entry and its pre-edit boundary, without per-character undo history. */
    public RpnCalculator entryForCommit() {
        RpnCalculator editor = new RpnCalculator();
        editor.inputProfile = inputProfile;
        editor.restore(entryState(new State(this), Collections.emptyList()));
        for (State state : undo) {
            if (!state.editing) {
                editor.undo.push(entryState(state, Collections.emptyList()));
                break;
            }
        }
        return editor;
    }

    /** Install a completed UI draft without retaining its individual edits. */
    public void useEntryEditor(RpnCalculator editor) {
        if (!editor.stack.isEmpty())
            throw new IllegalArgumentException("Entry contains stack values");
        if (editor.editing) {
            for (State state : editor.undo) {
                if (!state.editing) {
                    if (undo.isEmpty()
                            || undo.peek().editing
                            || !sameStack(undo.peek().stack, stack))
                        undo.push(entryState(state, stack));
                    break;
                }
            }
            if (undo.size() > 100) {
                undo.removeLast();
                pruneSavedValues();
            }
        }
        restore(entryState(new State(editor), stack));
    }

    /** Persist a UI draft beside an already encoded arithmetic snapshot; no graph decoding. */
    public static byte[] saveWithEntry(byte[] calculation, RpnCalculator editor)
            throws IOException {
        byte[] draft = editor.save();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x52504e33);
        out.writeInt(calculation.length);
        out.write(calculation);
        out.writeInt(draft.length);
        out.write(draft);
        return bytes.toByteArray();
    }

    private static RpnCalculator loadWithEntry(DataInputStream in) throws IOException {
        byte[] calculation = readSection(in), draft = readSection(in);
        if (in.available() != 0) throw new IOException("Trailing saved state data");
        // Sections are ordinary snapshots, never nested envelopes.
        if (new DataInputStream(new ByteArrayInputStream(calculation)).readInt() == 0x52504e33
                || new DataInputStream(new ByteArrayInputStream(draft)).readInt() == 0x52504e33)
            throw new IOException("Nested entry snapshot");
        RpnCalculator calculator = load(calculation), editor = load(draft);
        if (!editor.stack.isEmpty()) throw new IOException("Entry contains stack values");
        calculator.restore(entryState(new State(editor), calculator.stack));
        for (State state : editor.undo) {
            if (!state.stack.isEmpty()) throw new IOException("Invalid entry history");
        }
        for (State state : editor.redo) {
            if (!state.stack.isEmpty()) throw new IOException("Invalid entry history");
        }
        while (calculator.undo.size()
                        + calculator.redo.size()
                        + editor.undo.size()
                        + editor.redo.size()
                > 100) {
            if (!calculator.undo.isEmpty()) calculator.undo.removeLast();
            else calculator.redo.removeLast();
        }
        // The worker detached these edits when publishing the arithmetic snapshot.
        List<State> edits = new ArrayList<>(editor.undo);
        for (int i = edits.size() - 1; i >= 0; i--)
            calculator.undo.push(entryState(edits.get(i), calculator.stack));
        edits = new ArrayList<>(editor.redo);
        for (int i = edits.size() - 1; i >= 0; i--)
            calculator.redo.addFirst(entryState(edits.get(i), calculator.stack));
        calculator.restoredEditor = editor;
        return calculator;
    }

    private static byte[] readSection(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > in.available()) throw new IOException("Invalid snapshot length");
        byte[] section = new byte[length];
        in.readFully(section);
        return section;
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

    public AngleUnit angleUnit() {
        return angleUnit;
    }

    public void setAngleUnit(AngleUnit unit) {
        angleUnit = Objects.requireNonNull(unit);
    }

    public void setInputProfile(NumberText.Profile profile) {
        inputProfile = Objects.requireNonNull(profile);
    }

    public String entryProblem() {
        if (!editing || entry.isEmpty()) return "";
        if (raw) return NumberText.analyze(entry, rawProfile).ambiguous() ? "Ambiguous" : "Invalid";
        try {
            parse(entry);
            return "";
        } catch (IllegalArgumentException ex) {
            return "Invalid";
        }
    }

    public List<UnifiedReal> values() {
        return Collections.unmodifiableList(new ArrayList<>(stack));
    }

    public List<String> formattedValues(
            ValueFormatter.Notation notation, FractionMode fractionMode) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < stack.size(); i++) {
            if (Thread.currentThread().isInterrupted())
                throw new com.hp.creals.CR.AbortedException();
            rows.add(
                    ValueFormatter.format(
                            stack.get(i),
                            !editing && i == stack.size() - 1,
                            notation,
                            fractionMode));
        }
        return Collections.unmodifiableList(rows);
    }

    /** Trusted canonical paste, also used by programmatic callers. */
    public void paste(String canonical) {
        parse(canonical);
        State before = new State(this);
        entry = canonical;
        editing = true;
        raw = false;
        select(entry.length(), entry.length());
        remember(before);
    }

    /** Clipboard text is retained, including ambiguous or invalid punctuation, for correction. */
    /** Multiple nonblank lines are validated before any state is changed. */
    public void pasteClipboard(String text, NumberText.Profile locale) {
        if (text.length() > MAX_PASTE_LENGTH)
            throw CalculationError.invalid(CalculationError.Code.ENTRY_TOO_LONG, MAX_PASTE_LENGTH);
        String[] lines = text.split("\\R", -1);
        List<UnifiedReal> values = new ArrayList<>();
        int nonblank = 0;
        for (String line : lines) if (!line.trim().isEmpty()) nonblank++;
        if (nonblank < 2) {
            pasteText(lines.length == 1 ? text : text.trim(), locale);
            return;
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            try {
                checkLength(line);
                values.add(parseClipboardLine(line, locale));
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw CalculationError.invalid(CalculationError.Code.INVALID_PASTE_LINE, i + 1);
            }
        }
        State before = new State(this);
        // Commit any current entry only after the clipboard has passed validation.
        try {
            commit();
        } catch (RuntimeException ex) {
            restore(before);
            throw ex;
        }
        for (UnifiedReal value : values) {
            stack.add(value);
            savedValues.put(value, SavedValue.of(value, "r", null, null));
        }
        if (before.editing) collapseEntryEdits(before);
        remember(before);
    }

    private static final java.util.regex.Pattern COPIED_FRACTION =
            java.util.regex.Pattern.compile("([+-]?[0-9]+)(?: ([0-9]+))?/([0-9]+)");

    private static UnifiedReal parseClipboardLine(String text, NumberText.Profile locale) {
        if (!text.contains("/")) return parse(NumberText.analyze(text, locale).canonical());
        java.util.regex.Matcher fraction = COPIED_FRACTION.matcher(text);
        if (!fraction.matches())
            throw CalculationError.invalid(CalculationError.Code.INVALID_NUMBER);
        UnifiedReal whole = parse(fraction.group(1)), denominator = parse(fraction.group(3));
        if (fraction.group(2) == null) return whole.divide(denominator);
        UnifiedReal part = parse(fraction.group(2)).divide(denominator);
        return text.startsWith("-") ? whole.negate().add(part).negate() : whole.add(part);
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

    private void checkLength(String text) {
        if (text == null || text.length() > MAX_ENTRY_LENGTH)
            throw CalculationError.invalid(CalculationError.Code.ENTRY_TOO_LONG, MAX_ENTRY_LENGTH);
    }

    private void resolveRaw() {
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

    public void key(String key) {
        restoredEditor = null;
        if (key.equals("HOLD_DECIMAL") && !editing) return;
        if (key.equals("SMART_CLEAR")) key = editing ? "CE" : "CLEAR";
        // Resolve against the calculation state, even when UI updates trail queued input.
        if (key.equals("HOLD_DROP")) key = editing ? "BACK" : "ROLL_DOWN";
        String direction = key.startsWith("SELECT_") ? key.substring(7) : key;
        if (direction.equals("LEFT")
                || direction.equals("RIGHT")
                || direction.equals("HOME")
                || direction.equals("END")) {
            moveCursor(direction, key.startsWith("SELECT_"));
            return;
        }
        if (key.equals("ANGLE")) {
            angleUnit = AngleUnit.values()[(angleUnit.ordinal() + 1) % AngleUnit.values().length];
            return;
        }
        if (key.equals(Command.UNDO)) {
            if (!undo.isEmpty()) {
                redo.push(new State(this));
                restore(undo.pop());
            }
            return;
        }
        if (key.equals(Command.REDO)) {
            if (!redo.isEmpty()) {
                undo.push(new State(this));
                restore(redo.pop());
            }
            return;
        }
        State before = new State(this);
        State committed = null;
        boolean clearsEntry = key.equals("CE") || key.equals("CLEAR") || key.equals("CANCEL_ENTRY");
        try {
            if (isEntryKey(key)) {
                editing = true;
                if (key.equals(".")) {
                    if (raw) {
                        replaceSelection(Character.toString(inputProfile.decimal));
                        resolveExplicitDecimal();
                    } else {
                        String remaining = entryWithoutSelection();
                        if (remaining.contains(".")
                                || remaining.contains("e")
                                || remaining.contains("E")) return;
                        replaceSelection(entry.isEmpty() || entry.equals("-") ? "0." : ".");
                    }
                } else replaceSelection(key);
                resolveRaw();
            } else if (key.equals("HOLD_DECIMAL")) {
                String remaining = entryWithoutSelection();
                if (remaining.contains("e") || remaining.contains("E")) return;
                replaceSelection("e");
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
                } else if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            } else if (key.equals("±") && editing) {
                boolean negative = entry.startsWith("-") || entry.startsWith("−");
                entry = negative ? entry.substring(1) : "-" + entry;
                select(selectionStart + (negative ? -1 : 1), selectionEnd + (negative ? -1 : 1));
                resolveRaw();
            } else if (clearsEntry) {
                if (key.equals("CLEAR")) stack.clear();
                entry = "";
                if (!key.equals("CE")) editing = false;
                raw = false;
                select(0, 0);
            } else if (key.equals(Command.ENTER)) {
                if (editing) commit();
                else {
                    need(1);
                    stack.add(top());
                }
            } else {
                commit();
                committed = new State(this);
                applyStackOrOperation(key);
                if (!stack.isEmpty())
                    ValueFormatter.format(top(), true, ValueFormatter.Notation.SCIENTIFIC);
            }
            if (before.editing && !editing && !clearsEntry) collapseEntryEdits(before);
            remember(before);
        } catch (RuntimeException ex) {
            if (ex instanceof com.hp.creals.CR.AbortedException) committed = null;
            restore(committed == null ? before : committed);
            if (committed != null) {
                if (before.editing) collapseEntryEdits(before);
                remember(before);
            }
            pruneSavedValues(); // Discard a result whose deferred validation failed.
            throw ex;
        }
    }

    private void collapseEntryEdits(State before) {
        while (!undo.isEmpty() && undo.peek().editing && sameStack(undo.peek().stack, before.stack))
            undo.pop();
    }

    private void applyStackOrOperation(String key) {
        switch (key) {
            case "Factor":
                need(1);
                List<BigInteger> factors =
                        ExactFunctions.factors(ExactFunctions.integer(top(), "Factorisation"));
                stack.remove(stack.size() - 1);
                for (BigInteger factor : factors) {
                    UnifiedReal value = new UnifiedReal(factor);
                    stack.add(value);
                    savedValues.put(value, SavedValue.of(value, "r", null, null));
                }
                break;
            case "DUP":
                need(1);
                stack.add(top());
                break;
            case "SWAP":
                need(2);
                Collections.swap(stack, stack.size() - 1, stack.size() - 2);
                break;
            case "ROLL_UP":
                if (stack.size() > 1) Collections.rotate(stack, -1);
                break;
            case "ROLL_DOWN":
                if (stack.size() > 1) Collections.rotate(stack, 1);
                break;
            default:
                applyOperation(key);
        }
    }

    private static boolean sameStack(List<UnifiedReal> a, List<UnifiedReal> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** Remove arbitrary rows as one undoable action, leaving unfinished entry untouched. */
    public void dropRows(int... indices) {
        TreeSet<Integer> rows = rowSet(indices);
        if (rows.isEmpty()) return;
        State before = new State(this);
        for (int index : rows.descendingSet()) stack.remove(index);
        remember(before);
    }

    /** Long-press digits pick a one-based stack level; entry mode keeps their digit function. */
    public void holdDigit(String digit) {
        if (!digit.matches("[0-9]")) throw new IllegalArgumentException("Invalid digit shortcut");
        if (editing) {
            key(digit);
            return;
        }
        int depth = digit.equals("0") ? 10 : Integer.parseInt(digit);
        need(depth);
        pickRows(stack.size() - depth);
    }

    /** Copy rows onto the stack in display order, preserving their exact values and entry. */
    public void pickRows(int... indices) {
        TreeSet<Integer> rows = rowSet(indices);
        if (rows.isEmpty()) return;
        State before = new State(this);
        List<UnifiedReal> picked = new ArrayList<>();
        for (int index : rows) picked.add(stack.get(index));
        stack.addAll(picked);
        remember(before);
    }

    /** Append the exact sum of distinct selected rows, leaving the originals and entry intact. */
    public void sumRows(int... indices) {
        aggregateRows(Operation.ADD, indices);
    }

    /** Append the exact product without consuming any selected value or unfinished entry. */
    public void productRows(int... indices) {
        aggregateRows(Operation.MULTIPLY, indices);
    }

    private void aggregateRows(Operation operation, int... indices) {
        TreeSet<Integer> rows = rowSet(indices);
        if (rows.isEmpty()) return;
        UnifiedReal sum = null;
        SavedValue expression = null;
        for (int index : rows) {
            UnifiedReal value = stack.get(index);
            if (sum == null) {
                sum = value;
                expression = savedValues.get(value);
            } else {
                sum = operation.evaluate(sum, value, angleUnit);
                expression = SavedValue.of(sum, operation.key, expression, savedValues.get(value));
            }
        }
        State before = new State(this);
        stack.add(sum);
        savedValues.put(sum, expression);
        remember(before);
    }

    /** Validate every result before replacing any selected row. */
    public void transformRows(Operation operation, int... indices) {
        if (operation == null || operation.arity != 1)
            throw new IllegalArgumentException("Expected a unary operation");
        TreeSet<Integer> rows = rowSet(indices);
        if (rows.isEmpty()) return;
        List<UnifiedReal> results = new ArrayList<>();
        List<SavedValue> expressions = new ArrayList<>();
        for (int row : rows) {
            UnifiedReal original = stack.get(row);
            UnifiedReal result = operation.evaluate(original, null, angleUnit);
            ValueFormatter.format(result, true, ValueFormatter.Notation.SCIENTIFIC);
            results.add(result);
            expressions.add(
                    SavedValue.of(
                            result,
                            operation.savedName(angleUnit),
                            savedValues.get(original),
                            null));
        }
        State before = new State(this);
        int i = 0;
        for (int row : rows) {
            UnifiedReal result = results.get(i);
            stack.set(row, result);
            savedValues.put(result, expressions.get(i++));
        }
        remember(before);
    }

    private TreeSet<Integer> rowSet(int... indices) {
        TreeSet<Integer> rows = new TreeSet<>();
        for (int index : indices) {
            if (index < 0 || index >= stack.size())
                throw CalculationError.invalid(CalculationError.Code.ROW_MISSING);
            rows.add(index);
        }
        return rows;
    }

    public List<UnifiedReal> selectedValues(int... indices) {
        List<UnifiedReal> result = new ArrayList<>();
        for (int index : rowSet(indices)) result.add(stack.get(index));
        return result;
    }

    public boolean sameStack(List<UnifiedReal> values) {
        return sameStack(stack, values);
    }

    public static boolean isEntryKey(String key) {
        return key.length() == 1
                && (key.charAt(0) == '.' || key.charAt(0) >= '0' && key.charAt(0) <= '9');
    }

    private String entryWithoutSelection() {
        return entry.substring(0, Math.min(selectionStart, selectionEnd))
                + entry.substring(Math.max(selectionStart, selectionEnd));
    }

    public boolean isEmpty() {
        return stack.isEmpty() && entry.isEmpty();
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
        pruneSavedValues();
    }

    private void pruneSavedValues() {
        Set<UnifiedReal> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(stack);
        for (State state : undo) live.addAll(state.stack);
        for (State state : redo) live.addAll(state.stack);
        savedValues.keySet().retainAll(live);
    }

    /**
     * At most 100 undo steps, with a 1 MiB storage budget including the active state. Large active
     * calculations are kept intact; only the oldest undo steps are evicted.
     */
    public byte[] save() throws IOException {
        byte[] result = encode();
        while (result.length > 1024 * 1024 && (!undo.isEmpty() || !redo.isEmpty())) {
            if (!undo.isEmpty()) undo.removeLast();
            else redo.removeLast();
            pruneSavedValues();
            result = encode();
        }
        return result;
    }

    private byte[] encode() throws IOException {
        List<State> states = new ArrayList<>();
        states.add(new State(this));
        states.addAll(undo);
        states.addAll(redo);
        IdentityHashMap<SavedValue, Integer> ids = new IdentityHashMap<>();
        List<SavedValue> nodes = new ArrayList<>();
        Deque<SavedValue> todo = new ArrayDeque<>();
        for (State state : states)
            for (UnifiedReal value : state.stack) {
                SavedValue root = savedValues.get(value);
                if (root == null) throw new IOException("Missing exact expression");
                todo.push(root);
                while (!todo.isEmpty()) {
                    SavedValue node = todo.peek();
                    if (ids.containsKey(node)) {
                        todo.pop();
                        continue;
                    }
                    if (node.left != null && !ids.containsKey(node.left)) {
                        todo.push(node.left);
                        continue;
                    }
                    if (node.right != null && !ids.containsKey(node.right)) {
                        todo.push(node.right);
                        continue;
                    }
                    ids.put(node, nodes.size());
                    nodes.add(node);
                    todo.pop();
                }
            }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x52504e32);
        out.writeUTF(angleUnit.name());
        writeProfile(out, inputProfile);
        out.writeInt(nodes.size());
        for (SavedValue node : nodes) {
            out.writeUTF(node.op);
            out.writeUTF(node.literal);
            out.writeInt(node.left == null ? -1 : ids.get(node.left));
            out.writeInt(node.right == null ? -1 : ids.get(node.right));
        }
        out.writeInt(undo.size());
        out.writeInt(redo.size());
        out.writeInt(states.size());
        for (State state : states) {
            out.writeInt(state.stack.size());
            for (UnifiedReal value : state.stack) out.writeInt(ids.get(savedValues.get(value)));
            out.writeUTF(state.entry);
            out.writeBoolean(state.editing);
            out.writeBoolean(state.raw);
            out.writeInt(state.start);
            out.writeInt(state.end);
            writeProfile(out, state.profile);
        }
        out.flush();
        return bytes.toByteArray();
    }

    public static RpnCalculator load(byte[] bytes) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int version = in.readInt();
            if (version == 0x52504e33) return loadWithEntry(in);
            if (version != 0x52504e31 && version != 0x52504e32)
                throw new IOException("Unknown saved state version");
            RpnCalculator c = new RpnCalculator();
            c.angleUnit = AngleUnit.valueOf(in.readUTF());
            c.inputProfile = readProfile(in);
            int count = readCount(in, 1000000);
            List<SavedValue> nodes = new ArrayList<>();
            List<UnifiedReal> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (Thread.currentThread().isInterrupted())
                    throw new com.hp.creals.CR.AbortedException();
                String op = in.readUTF(), literal = in.readUTF();
                int left = in.readInt(), right = in.readInt();
                if (left < -1 || right < -1 || left >= i || right >= i)
                    throw new IOException("Invalid expression reference");
                SavedValue node =
                        new SavedValue(
                                op,
                                literal,
                                left < 0 ? null : nodes.get(left),
                                right < 0 ? null : nodes.get(right));
                UnifiedReal value =
                        node.evaluate(
                                left < 0 ? null : values.get(left),
                                right < 0 ? null : values.get(right));
                nodes.add(node);
                values.add(value);
                c.savedValues.put(value, node);
            }
            int undos = version == 0x52504e32 ? readCount(in, 100) : -1;
            int redos = version == 0x52504e32 ? readCount(in, 100) : 0;
            int states = readCount(in, 101);
            if (undos < 0) undos = states - 1;
            if (states != 1 + undos + redos) throw new IOException("Invalid undo/redo history");
            if (states == 0) throw new IOException("Missing current state");
            for (int i = 0; i < states; i++) {
                int size = readCount(in, 1000000);
                List<UnifiedReal> stack = new ArrayList<>();
                for (int j = 0; j < size; j++) {
                    int id = in.readInt();
                    if (id < 0 || id >= count) throw new IOException("Invalid stack reference");
                    stack.add(values.get(id));
                }
                String entry = in.readUTF();
                c.checkLength(entry);
                boolean editing = in.readBoolean(), raw = in.readBoolean();
                int start = in.readInt(), end = in.readInt();
                if (start < 0
                        || end < 0
                        || start > entry.length()
                        || end > entry.length()
                        || (!editing && !entry.isEmpty()))
                    throw new IOException("Invalid entry state");
                State state = new State(stack, entry, editing, raw, start, end, readProfile(in));
                if (i == 0) c.restore(state);
                else if (i <= undos) c.undo.addLast(state);
                else c.redo.addLast(state);
            }
            if (in.available() != 0) throw new IOException("Trailing saved state data");
            c.pruneSavedValues();
            return c;
        } catch (com.hp.creals.CR.AbortedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid saved state", ex);
        }
    }

    private static int readCount(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IOException("Invalid saved state size");
        return count;
    }

    private static void writeProfile(DataOutputStream out, NumberText.Profile p)
            throws IOException {
        out.writeChar(p.decimal);
        out.writeChar(p.grouping);
        out.writeInt(p.primary);
        out.writeInt(p.secondary);
    }

    private static NumberText.Profile readProfile(DataInputStream in) throws IOException {
        return new NumberText.Profile(in.readChar(), in.readChar(), in.readInt(), in.readInt());
    }

    private UnifiedReal top() {
        return stack.get(stack.size() - 1);
    }

    private void need(int count) {
        if (stack.size() < count)
            throw CalculationError.invalid(CalculationError.Code.NEED_VALUES, count);
    }

    private void commit() {
        resolveRaw();
        if (raw) NumberText.analyze(entry, rawProfile).canonical(); // Throws instead of guessing.
        if (!entry.isEmpty()) {
            UnifiedReal value = parse(entry);
            stack.add(value);
            savedValues.put(value, SavedValue.of(value, "r", null, null));
        }
        entry = "";
        editing = false;
        raw = false;
        select(0, 0);
    }

    private void applyOperation(String key) {
        Operation operation = Operation.find(key);
        if (operation == null) throw new IllegalArgumentException("Unknown operation");
        need(operation.arity);
        UnifiedReal a = operation.arity == 0 ? null : stack.get(stack.size() - operation.arity);
        UnifiedReal b = operation.arity == 2 ? top() : null;
        UnifiedReal result = operation.evaluate(a, b, angleUnit);
        savedValues.put(
                result,
                SavedValue.of(
                        result,
                        operation.savedName(angleUnit),
                        savedValues.get(a),
                        savedValues.get(b)));
        for (int i = 0; i < operation.arity; i++) stack.remove(stack.size() - 1);
        stack.add(result);
    }

    public static UnifiedReal parse(String text) {
        BigDecimal d = new BigDecimal(text);
        if (Math.abs((long) d.scale()) > 1000 || d.precision() > 1000)
            throw CalculationError.invalid(CalculationError.Code.NUMBER_TOO_LARGE);
        BigInteger n = d.unscaledValue();
        return new UnifiedReal(
                d.scale() < 0
                        ? new BoundedRational(n.multiply(BigInteger.TEN.pow(-d.scale())))
                        : new BoundedRational(n, BigInteger.TEN.pow(d.scale())));
    }
}
