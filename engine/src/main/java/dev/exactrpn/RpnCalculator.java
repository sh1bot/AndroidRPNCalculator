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

/** Exact stack arithmetic accepting completed entry drafts. */
public final class RpnCalculator {
    public static final int MAX_PASTE_LENGTH = 65536;
    private List<UnifiedReal> stack = new ArrayList<>();
    private EntryEditor editor = new EntryEditor();

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
    private final Deque<State> undo = new History(), redo = new History();

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
        final EntryEditor entry;

        State(List<UnifiedReal> stack, EntryEditor entry) {
            this.stack = stack;
            this.entry = entry;
        }

        State(RpnCalculator c) {
            this(new ArrayList<>(c.stack), c.editor.copy());
        }
    }

    private void restore(State state) {
        stack = new ArrayList<>(state.stack);
        editor = state.entry.copy();
        syncCurrentValues();
    }

    private void remember(State s) {
        if (sameStack(s.stack, stack) && s.entry.same(editor)) {
            discardPendingValues();
            return;
        }
        undo.push(s);
        syncCurrentValues();
        redo.clear();
        if (undo.size() > 100) undo.removeLast();
        discardPendingValues();
    }

    public EntryEditor detachEntryEditor() {
        EntryEditor result = editor;
        editor = editor.copy();
        editor.clearUndo();
        return result;
    }

    public void useEntryEditor(EntryEditor draft) {
        editor = draft.copy();
    }

    /** Replace the entry section without decoding the arithmetic graph or nesting snapshots. */
    public static byte[] saveWithEntry(byte[] calculation, EntryEditor draft) throws IOException {
        return saveWithEntry(calculation, draft, false);
    }

    public static byte[] saveWithEntry(byte[] calculation, EntryEditor draft, boolean newBranch)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(calculation));
        if (in.readInt() != 0x52504e36)
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
        editor = draft;
        while (undo.size() + redo.size() + draft.undoCount() + draft.redoCount() > 100) {
            if (!undo.isEmpty()) undo.removeLast();
            else redo.removeLast();
        }
        return this;
    }

    private static byte[] readSection(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > in.available()) throw new IOException("Invalid snapshot length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
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
        if (nonblank < 2) throw new IllegalArgumentException("Expected multiple clipboard values");
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
        if (key.equals("ANGLE")) {
            angleUnit = AngleUnit.values()[(angleUnit.ordinal() + 1) % AngleUnit.values().length];
            return;
        }
        if (key.equals(Command.UNDO)) {
            if (!undo.isEmpty()) {
                redo.push(new State(this));
                restore(undo.peek());
                undo.pop();
            }
            return;
        }
        if (key.equals(Command.REDO)) {
            if (!redo.isEmpty()) {
                undo.push(new State(this));
                restore(redo.peek());
                redo.pop();
            }
            return;
        }
        State before = new State(this);
        State committed = null;
        try {
            if (key.equals("CLEAR")) {
                stack.clear();
                editor = new EntryEditor();
            } else if (key.equals("BACK") || key.equals("DROP") || key.equals("DELETE")) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
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
            remember(before);
        } catch (RuntimeException ex) {
            if (ex instanceof com.hp.creals.CR.AbortedException) committed = null;
            restore(committed == null ? before : committed);
            if (committed != null) {
                remember(before);
            }
            pruneSavedValues(); // Discard a result whose deferred validation failed.
            throw ex;
        }
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
        undo.clear();
        redo.clear();
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
                && (!undo.isEmpty() || !redo.isEmpty())) {
            if (!undo.isEmpty()) undo.removeLast();
            else redo.removeLast();
            body = encode();
        }
        CalculationLimits.require(
                body.length + currentEntry.length + 13, CalculationLimits.ARITHMETIC_BYTES);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x52504e36);
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
            byte[] entry = state.entry.encode();
            out.writeInt(entry.length);
            out.write(entry);
        }
        out.flush();
        return bytes.toByteArray();
    }

    public static RpnCalculator load(byte[] bytes) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0x52504e36) throw new IOException("Unknown saved state version");
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
                    new State(stack, i == 0 ? c.editor.copy() : EntryEditor.load(readSection(in)));
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
        editor.select(0, 0);
        editor.clearUndo();
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
