// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** All values stay exact; text formatting is never used as arithmetic input. */
public final class RpnCalculator {
    private List<UnifiedReal> stack = new ArrayList<>();
    private String entry = "";
    private boolean editing;
    public enum AngleUnit { RAD, DEG, GRAD }
    private AngleUnit angleUnit = AngleUnit.RAD;
    private final Deque<State> undo = new ArrayDeque<>();
    private static final class State {
        final List<UnifiedReal> stack; final String entry; final boolean editing;
        State(List<UnifiedReal> s, String e, boolean edit) { stack = new ArrayList<>(s); entry = e; editing = edit; }
    }
    /** Independent transaction candidate; immutable UnifiedReal values can be shared safely. */
    public RpnCalculator copy() {
        RpnCalculator result = new RpnCalculator();
        result.stack = new ArrayList<>(stack); result.entry = entry;
        result.editing = editing; result.angleUnit = angleUnit;
        result.undo.addAll(undo);
        return result;
    }
    public String entry() { return entry; }
    public boolean isEditing() { return editing; }
    public AngleUnit angleUnit() { return angleUnit; }
    public void setAngleUnit(AngleUnit unit) { angleUnit = Objects.requireNonNull(unit); }
    public List<String> formattedValues(ValueFormatter.Notation notation) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < stack.size(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new com.hp.creals.CR.AbortedException();
            rows.add(ValueFormatter.format(stack.get(i), !editing && i == stack.size()-1, notation));
        }
        return Collections.unmodifiableList(rows);
    }
    public List<UnifiedReal> values() { return Collections.unmodifiableList(new ArrayList<>(stack)); }
    public void key(String key) {
        if (key.equals("ANGLE")) { angleUnit = AngleUnit.values()[(angleUnit.ordinal()+1)%AngleUnit.values().length]; return; }
        if (key.equals("UNDO")) {
            if (!undo.isEmpty()) { State s = undo.pop(); stack = new ArrayList<>(s.stack); entry = s.entry; editing = s.editing; }
            return;
        }
        State before = new State(stack, entry, editing);
        try {
            if (key.matches("[0-9.]")) {
                editing = true;
                if (entry.length() >= 256) throw new IllegalArgumentException("Entry too long");
                if (key.equals(".") && entry.contains(".")) return;
                if (key.equals(".") && (entry.isEmpty() || entry.equals("-"))) entry += "0";
                entry += key;
            } else if (key.equals("BACK") || key.equals("DROP")) {
                if (editing) {
                    if (!entry.isEmpty()) entry = entry.substring(0, entry.length()-1);
                } else if (!stack.isEmpty()) stack.remove(stack.size()-1);
            } else if (key.equals("±") && editing) {
                entry = entry.startsWith("-") ? entry.substring(1) : "-" + entry;
            } else if (key.equals("CE")) { entry = ""; editing = false;
            } else if (key.equals("CLEAR")) { stack.clear(); entry = ""; editing = false;
            } else if (key.equals("ENTER")) {
                if (editing) commit(); else { need(1); stack.add(top()); }
            } else {
                commit();
                switch (key) {
                    case "π": stack.add(UnifiedReal.PI); break;
                    case "e": stack.add(UnifiedReal.E); break;
                    case "DUP": need(1); stack.add(top()); break;
                    case "SWAP": need(2); Collections.swap(stack, stack.size()-1, stack.size()-2); break;
                    case "+": case "−": case "×": case "÷": case "xʸ":
                        need(2);
                        UnifiedReal x = top(), y = stack.get(stack.size()-2), result;
                        switch (key) {
                            case "+": result = y.add(x); break;
                            case "−": result = y.subtract(x); break;
                            case "×": result = y.multiply(x); break;
                            case "÷": result = y.divide(x); break;
                            default: result = y.pow(x);
                        }
                        stack.remove(stack.size()-1); stack.set(stack.size()-1, result); break;
                    default:
                        need(1); UnifiedReal a = top(), r;
                        switch (key) {
                            case "±": r = a.negate(); break;
                            case "√": r = a.sqrt(); break;
                            case "1/x": r = a.inverse(); break;
                            case "sin": r = radians(a).sin(); break;
                            case "cos": r = radians(a).cos(); break;
                            case "tan": r = radians(a).tan(); break;
                            case "ln": r = a.ln(); break;
                            case "exp": case "eˣ": r = a.exp(); break;
                            default: throw new IllegalArgumentException("Unknown operation");
                        }
                        stack.set(stack.size()-1, r);
                }
                // Force lazy domain errors before committing the transaction.
                if (!stack.isEmpty()) ValueFormatter.format(top(), true, ValueFormatter.Notation.SCIENTIFIC);
            }
            undo.push(before); if (undo.size() > 100) undo.removeLast();
        } catch (RuntimeException ex) { stack = before.stack; entry = before.entry; editing = before.editing; throw ex; }
    }
    private UnifiedReal top() { return stack.get(stack.size()-1); }
    private void need(int count) { if (stack.size() < count) throw new IllegalArgumentException("Need " + count + " stack values"); }
    private void commit() {
        if (!entry.isEmpty()) stack.add(parse(entry));
        entry = ""; editing = false;
    }
    private UnifiedReal radians(UnifiedReal value) {
        if (angleUnit == AngleUnit.RAD) return value;
        return value.multiply(UnifiedReal.PI).divide(new UnifiedReal(angleUnit == AngleUnit.DEG ? 180 : 200));
    }
    public static UnifiedReal parse(String text) {
        BigDecimal d = new BigDecimal(text);
        if (Math.abs((long)d.scale()) > 1000 || d.precision() > 1000) throw new IllegalArgumentException("Number too large");
        BigInteger n = d.unscaledValue();
        return new UnifiedReal(d.scale() < 0
            ? new BoundedRational(n.multiply(BigInteger.TEN.pow(-d.scale())))
            : new BoundedRational(n, BigInteger.TEN.pow(d.scale())));
    }
    public static String format(UnifiedReal value) {
        if (value.exactlyDisplayable()) return value.toNiceString();
        return "≈ " + value.toStringTruncated(24);
    }
}
