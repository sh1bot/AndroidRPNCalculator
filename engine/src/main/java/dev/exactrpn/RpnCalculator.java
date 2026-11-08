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
    public static final int MAX_ENTRY_LENGTH = EntryEditor.MAX_ENTRY_LENGTH;
    public static final int MAX_PASTE_LENGTH = 65536;
    private List<UnifiedReal> stack = new ArrayList<>();
    private final EntryEditor editor = new EntryEditor(false);
    private EntryEditor restoredEditor;

    public enum AngleUnit {
        RAD,
        DEG,
        GRAD
    }

    private AngleUnit angleUnit = AngleUnit.RAD;
    private final IdentityHashMap<UnifiedReal, SavedValue> savedValues = new IdentityHashMap<>();
    private final Set<UnifiedReal> pendingValues =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<UnifiedReal, Integer> references = new IdentityHashMap<>();
    private Set<UnifiedReal> currentValues = Collections.newSetFromMap(new IdentityHashMap<>());
    private final UndoHistory<State> history =
            new UndoHistory<>(new History(), new History(), this::syncCurrentValues);
    private final Deque<State> undo = history.undo, redo = history.redo;

    private void saveValue(UnifiedReal value, SavedValue expression) {
        savedValues.put(value, expression);
        if (!references.containsKey(value)) pendingValues.add(value);
    }

    private void discardPendingValues() {
        for (UnifiedReal value : pendingValues)
            if (!references.containsKey(value)) savedValues.remove(value);
        pendingValues.clear();
    }

    private void retain(UnifiedReal value) {
        references.merge(value, 1, Integer::sum);
    }

    private void release(UnifiedReal value) {
        int count = references.get(value) - 1;
        if (count == 0) {
            references.remove(value);
            savedValues.remove(value);
        } else references.put(value, count);
    }

    /** History owns references independently of the mutable current stack. */
    private final class History extends ArrayDeque<State> {
        @Override
        public void push(State state) {
            addFirst(state);
        }

        @Override
        public State pop() {
            return removeFirst();
        }

        @Override
        public void addFirst(State state) {
            for (UnifiedReal value : state.stack) retain(value);
            super.addFirst(state);
        }

        @Override
        public void addLast(State state) {
            for (UnifiedReal value : state.stack) retain(value);
            super.addLast(state);
        }

        @Override
        public boolean addAll(java.util.Collection<? extends State> states) {
            for (State state : states) addLast(state);
            return !states.isEmpty();
        }

        @Override
        public State removeFirst() {
            State state = super.removeFirst();
            for (UnifiedReal value : state.stack) release(value);
            return state;
        }

        @Override
        public State removeLast() {
            State state = super.removeLast();
            for (UnifiedReal value : state.stack) release(value);
            return state;
        }

        @Override
        public void clear() {
            while (!isEmpty()) removeFirst();
        }
    }

    private void syncCurrentValues() {
        Set<UnifiedReal> next = Collections.newSetFromMap(new IdentityHashMap<>());
        next.addAll(stack);
        for (UnifiedReal value : next) if (!currentValues.contains(value)) retain(value);
        for (UnifiedReal value : currentValues) if (!next.contains(value)) release(value);
        currentValues = next;
    }

    private static final class State {
        final List<UnifiedReal> stack;
        final EntryEditor.State entry;

        State(List<UnifiedReal> stack, EntryEditor.State entry) {
            this.stack = stack;
            this.entry = entry;
        }

        State(RpnCalculator c) {
            this(new ArrayList<>(c.stack), new EntryEditor.State(c.editor));
        }
    }

    private void restore(State state) {
        stack = new ArrayList<>(state.stack);
        editor.restore(state.entry);
        syncCurrentValues();
    }

    private void remember(State s) {
        if (sameStack(s.stack, stack) && s.entry.same(editor)) {
            discardPendingValues();
            return;
        }
        history.remember(s);
        discardPendingValues();
    }

    public EntryEditor detachEntryEditor() {
        if (restoredEditor != null) {
            EntryEditor result = restoredEditor;
            restoredEditor = null;
            for (int i = 0; i < result.undo.size(); i++) undo.removeFirst();
            for (int i = 0; i < result.redo.size(); i++) redo.removeFirst();
            if (result.undoCount() == 0 && result.redoCount() == 0 && editor.editing) {
                moveEntryHistory(undo, result.undo);
                moveEntryHistory(redo, result.redo);
            }
            return result;
        }
        EntryEditor result = new EntryEditor();
        result.inputProfile = editor.inputProfile;
        result.restore(new EntryEditor.State(editor));
        if (editor.editing) {
            moveEntryHistory(undo, result.undo);
            moveEntryHistory(redo, result.redo);
        }
        return result;
    }

    private void moveEntryHistory(Deque<State> source, Deque<EntryEditor.State> target) {
        while (!source.isEmpty() && sameStack(source.peek().stack, stack)) {
            State state = source.pop();
            target.addLast(state.entry);
            if (!state.entry.editing) break;
        }
    }

    private static State entryState(EntryEditor.State entry, List<UnifiedReal> stack) {
        return new State(new ArrayList<>(stack), entry);
    }

    public void useEntryEditor(EntryEditor draft) {
        if (draft.editing) {
            for (EntryEditor.State state : draft.undo)
                if (!state.editing) {
                    if (undo.isEmpty()
                            || undo.peek().entry.editing
                            || !sameStack(undo.peek().stack, stack))
                        undo.push(entryState(state, stack));
                    break;
                }
            if (undo.size() + redo.size() > 100) {
                if (undo.size() > 1) undo.removeLast();
                else redo.removeLast();
            }
        }
        editor.inputProfile = draft.inputProfile;
        editor.restore(new EntryEditor.State(draft));
    }

    /** Replace the entry section without decoding the arithmetic graph or nesting snapshots. */
    public static byte[] saveWithEntry(byte[] calculation, EntryEditor draft) throws IOException {
        return saveWithEntry(calculation, draft, false);
    }

    public static byte[] saveWithEntry(byte[] calculation, EntryEditor draft, boolean newBranch)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(calculation));
        if (in.readInt() != 0x52504e35)
            throw new IOException("Unsupported snapshot for entry replacement");
        int length = in.readInt();
        if (length < 0 || length > calculation.length - 13)
            throw new IOException("Invalid arithmetic section");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(calculation, 0, 8 + length);
        byte[] entry = draft.save(CalculationLimits.SAVED_BYTES - 13 - length);
        out.writeInt(entry.length);
        out.write(entry);
        out.writeBoolean(newBranch);
        return bytes.toByteArray();
    }

    private RpnCalculator restoreEntry(EntryEditor draft, boolean newBranch) {
        if (newBranch) discardRedo();
        editor.inputProfile = draft.inputProfile;
        editor.restore(new EntryEditor.State(draft));
        history.trim(100 - draft.undoCount() - draft.redoCount());
        List<EntryEditor.State> edits = new ArrayList<>(draft.undo);
        for (int i = edits.size() - 1; i >= 0; i--) undo.push(entryState(edits.get(i), stack));
        edits = new ArrayList<>(draft.redo);
        for (int i = edits.size() - 1; i >= 0; i--) redo.addFirst(entryState(edits.get(i), stack));
        restoredEditor = draft;
        return this;
    }

    private static byte[] readSection(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > in.available()) throw new IOException("Invalid snapshot length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    public String entry() {
        return editor.entry();
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    public boolean rawEntry() {
        return editor.rawEntry();
    }

    public int selectionStart() {
        return editor.selectionStart();
    }

    public int selectionEnd() {
        return editor.selectionEnd();
    }

    public void select(int start, int end) {
        editor.select(start, end);
    }

    public void moveCursor(String direction, boolean extend) {
        editor.moveCursor(direction, extend);
    }

    public void setInputProfile(NumberText.Profile profile) {
        editor.setInputProfile(profile);
    }

    public String entryProblem() {
        return editor.entryProblem();
    }

    public static boolean isEntryKey(String key) {
        return EntryEditor.isEntryKey(key);
    }

    public void pasteText(String text, NumberText.Profile locale) {
        State before = new State(this);
        editor.pasteText(text, locale);
        remember(before);
    }

    public void editText(
            String text,
            int start,
            int end,
            boolean raw,
            boolean decimal,
            NumberText.Profile profile) {
        State before = new State(this);
        editor.editText(text, start, end, raw, decimal, profile);
        remember(before);
    }

    private boolean applyEntryKey(String key, boolean clearsEntry) {
        if (!editor.editing && (key.equals("BACK") || key.equals("DROP") || key.equals("DELETE"))) {
            if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            return true;
        }
        if (key.equals("CLEAR")) stack.clear();
        return editor.applyEntryKey(key, clearsEntry);
    }

    public AngleUnit angleUnit() {
        return angleUnit;
    }

    public void setAngleUnit(AngleUnit unit) {
        angleUnit = Objects.requireNonNull(unit);
    }

    public List<UnifiedReal> values() {
        return Collections.unmodifiableList(new ArrayList<>(stack));
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
                EntryEditor.checkLength(line);
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
            saveValue(value, SavedValue.of(value, "r", null, null));
        }
        if (before.entry.editing) collapseEntryEdits(before);
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

    public void key(String key) {
        restoredEditor = null;
        if (key.equals("HOLD_DECIMAL") && !editor.editing) return;
        if (key.equals("SMART_CLEAR")) key = editor.editing ? "CE" : "CLEAR";
        // Resolve against the calculation state, even when UI updates trail queued input.
        if (key.equals("HOLD_DROP")) key = editor.editing ? "BACK" : "ROLL_DOWN";
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
        if (key.equals(Command.UNDO) || key.equals(Command.REDO)) {
            boolean backwards = key.equals(Command.UNDO);
            if (!(backwards ? undo : redo).isEmpty())
                history.move(backwards, new State(this), this::restore);
            return;
        }
        State before = new State(this);
        State committed = null;
        boolean clearsEntry = key.equals("CE") || key.equals("CLEAR") || key.equals("CANCEL_ENTRY");
        try {
            if (applyEntryKey(key, clearsEntry)) {
                // Entry changes share the transaction and undo policy below.
            } else if (key.equals(Command.ENTER)) {
                if (editor.editing) commit();
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
            if (before.entry.editing && !editor.editing && !clearsEntry) collapseEntryEdits(before);
            remember(before);
        } catch (RuntimeException ex) {
            if (ex instanceof com.hp.creals.CR.AbortedException) committed = null;
            restore(committed == null ? before : committed);
            if (committed != null) {
                if (before.entry.editing) collapseEntryEdits(before);
                remember(before);
            }
            pruneSavedValues(); // Discard a result whose deferred validation failed.
            throw ex;
        }
    }

    private void collapseEntryEdits(State before) {
        while (!undo.isEmpty()
                && undo.peek().entry.editing
                && sameStack(undo.peek().stack, before.stack)) undo.pop();
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
                    saveValue(value, SavedValue.of(value, "r", null, null));
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
        if (editor.editing) {
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
        saveValue(sum, expression);
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
            saveValue(result, expressions.get(i++));
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

    public boolean isEmpty() {
        return stack.isEmpty() && editor.entry.isEmpty();
    }

    public int undoCount() {
        return undo.size();
    }

    public int redoCount() {
        return redo.size();
    }

    public void discardRedo() {
        if (redo.isEmpty()) return;
        redo.clear();
    }

    public void clearUndo() {
        history.clear();
    }

    // Only needed after loading expression nodes or rolling back a failed result.
    private void pruneSavedValues() {
        savedValues.keySet().retainAll(references.keySet());
        pendingValues.clear();
    }

    /** Evict old undo states to fit the transfer budget; never truncate an arithmetic value. */
    public byte[] save() throws IOException {
        byte[] body = encode();
        byte[] currentEntry = editor.save();
        while (body.length + currentEntry.length + 13 > CalculationLimits.ARITHMETIC_BYTES
                && history.evictOldest()) body = encode();
        CalculationLimits.require(
                body.length + currentEntry.length + 13, CalculationLimits.ARITHMETIC_BYTES);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x52504e35);
        out.writeInt(body.length);
        out.write(body);
        out.writeInt(currentEntry.length);
        out.write(currentEntry);
        out.writeBoolean(false);
        return bytes.toByteArray();
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
        out.writeUTF(angleUnit.name());
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
        for (int index = 0; index < states.size(); index++) {
            State state = states.get(index);
            out.writeInt(state.stack.size());
            for (UnifiedReal value : state.stack) out.writeInt(ids.get(savedValues.get(value)));
            if (index == 0) continue;
            EntryEditor.writeState(out, state.entry);
        }
        out.flush();
        return bytes.toByteArray();
    }

    public static RpnCalculator load(byte[] bytes) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0x52504e35) throw new IOException("Unknown saved state version");
            byte[] body = readSection(in), entry = readSection(in);
            boolean branch = in.readBoolean();
            if (in.available() != 0) throw new IOException("Trailing saved state data");
            return readMath(new DataInputStream(new ByteArrayInputStream(body)))
                    .restoreEntry(EntryEditor.load(entry), branch);
        } catch (com.hp.creals.CR.AbortedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid saved state", ex);
        }
    }

    private static RpnCalculator readMath(DataInputStream in) throws IOException {
        RpnCalculator c = new RpnCalculator();
        c.angleUnit = AngleUnit.valueOf(in.readUTF());
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
        int undos = readCount(in, 100);
        int redos = readCount(in, 100);
        int states = readCount(in, 101);
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
            State state =
                    new State(
                            stack,
                            i == 0 ? new EntryEditor.State(c.editor) : EntryEditor.readState(in));
            if (i == 0) c.restore(state);
            else if (i <= undos) c.undo.addLast(state);
            else c.redo.addLast(state);
        }
        if (in.available() != 0) throw new IOException("Trailing saved state data");
        c.pruneSavedValues();
        return c;
    }

    private static int readCount(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IOException("Invalid saved state size");
        return count;
    }

    private UnifiedReal top() {
        return stack.get(stack.size() - 1);
    }

    private void need(int count) {
        if (stack.size() < count)
            throw CalculationError.invalid(CalculationError.Code.NEED_VALUES, count);
    }

    private void commit() {
        editor.resolveRaw();
        if (editor.raw)
            NumberText.analyze(editor.entry, editor.rawProfile)
                    .canonical(); // Throws instead of guessing.
        if (!editor.entry.isEmpty()) {
            UnifiedReal value = parse(editor.entry);
            stack.add(value);
            saveValue(value, SavedValue.of(value, "r", null, null));
        }
        editor.entry = "";
        editor.editing = false;
        editor.raw = false;
        select(0, 0);
    }

    private void applyOperation(String key) {
        Operation operation = Operation.find(key);
        if (operation == null) throw new IllegalArgumentException("Unknown operation");
        need(operation.arity);
        UnifiedReal a = operation.arity == 0 ? null : stack.get(stack.size() - operation.arity);
        UnifiedReal b = operation.arity == 2 ? top() : null;
        UnifiedReal result = operation.evaluate(a, b, angleUnit);
        saveValue(
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
