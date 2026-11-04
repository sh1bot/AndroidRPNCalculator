// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Exact stack values plus a cursor-editable, transactional number entry. */
public final class RpnCalculator {
    private List<UnifiedReal> stack=new ArrayList<>();
    private String entry="";
    private boolean editing, raw;
    private int selectionStart, selectionEnd;
    public enum AngleUnit { RAD, DEG, GRAD }
    private AngleUnit angleUnit=AngleUnit.RAD;
    private NumberText.Profile inputProfile=new NumberText.Profile('.',',',3,3);
    private NumberText.Profile rawProfile=inputProfile;
    private final Deque<State> undo=new ArrayDeque<>();
    private static final class State {
        final List<UnifiedReal> stack;
        final String entry;
        final boolean editing,raw;
        final int start,end;
        final NumberText.Profile profile;
        State(RpnCalculator c) {
            stack=new ArrayList<>(c.stack); entry=c.entry; editing=c.editing; raw=c.raw;
            start=c.selectionStart; end=c.selectionEnd; profile=c.rawProfile;
        }
    }
    private void restore(State s) {
        stack=new ArrayList<>(s.stack); entry=s.entry; editing=s.editing; raw=s.raw;
        selectionStart=s.start; selectionEnd=s.end; rawProfile=s.profile;
    }
    private void remember(State s) { undo.push(s); if(undo.size()>100) undo.removeLast(); }
    public RpnCalculator copy() {
        RpnCalculator c=new RpnCalculator(); c.restore(new State(this));
        c.angleUnit=angleUnit; c.inputProfile=inputProfile; c.undo.addAll(undo); return c;
    }
    public String entry() { return entry; }
    public boolean isEditing() { return editing; }
    public boolean rawEntry() { return raw; }
    public int selectionStart() { return selectionStart; }
    public int selectionEnd() { return selectionEnd; }
    public void select(int start,int end) {
        selectionStart=Math.max(0,Math.min(entry.length(),start));
        selectionEnd=Math.max(0,Math.min(entry.length(),end));
    }
    public AngleUnit angleUnit() { return angleUnit; }
    public void setAngleUnit(AngleUnit unit) { angleUnit=Objects.requireNonNull(unit); }
    public void setInputProfile(NumberText.Profile profile) { inputProfile=Objects.requireNonNull(profile); }
    public String entryProblem() {
        if(!editing || entry.isEmpty()) return "";
        if(raw) return NumberText.analyze(entry,rawProfile).ambiguous() ? "Ambiguous" : "Invalid";
        try { parse(entry); return ""; } catch(IllegalArgumentException ex) { return "Invalid"; }
    }
    public List<UnifiedReal> values() { return Collections.unmodifiableList(new ArrayList<>(stack)); }
    public List<String> formattedValues(ValueFormatter.Notation notation) { return formattedValues(notation,false); }
    public List<String> formattedValues(ValueFormatter.Notation notation,boolean fractions) {
        List<String> rows=new ArrayList<>();
        for(int i=0;i<stack.size();i++) {
            if(Thread.currentThread().isInterrupted()) throw new com.hp.creals.CR.AbortedException();
            rows.add(ValueFormatter.format(stack.get(i),!editing && i==stack.size()-1,notation,fractions));
        }
        return Collections.unmodifiableList(rows);
    }
    /** Trusted canonical paste, also used by programmatic callers. */
    public void paste(String canonical) {
        parse(canonical); State before=new State(this);
        entry=canonical; editing=true; raw=false; select(entry.length(),entry.length()); remember(before);
    }
    /** Clipboard text is retained, including ambiguous or invalid punctuation, for correction. */
    public void pasteText(String text,NumberText.Profile locale) {
        checkLength(text); State before=new State(this);
        entry=text; editing=true; raw=true; rawProfile=locale;
        select(entry.length(),entry.length()); resolveRaw(); remember(before);
    }
    public void editText(String text,int start,int end,boolean rawText,boolean explicitDecimal,NumberText.Profile profile) {
        checkLength(text); State before=new State(this);
        entry=text; editing=true; raw=rawText; inputProfile=profile;
        if(!rawText) rawProfile=profile;
        select(start,end);
        if(raw && explicitDecimal) resolveExplicitDecimal();
        resolveRaw(); remember(before);
    }
    private void checkLength(String text) {
        if(text==null || text.length()>4096) throw new IllegalArgumentException("Entry too long");
    }
    private void resolveRaw() {
        if(entry.isEmpty()) { raw=false; return; }
        if(!raw) return;
        NumberText.Analysis a=NumberText.analyze(entry,rawProfile);
        if(a.valid() && !a.ambiguous()) {
            entry=a.canonical(); raw=false; select(selectionStart,selectionEnd);
        }
    }
    private void resolveExplicitDecimal() {
        try { entry=NumberText.selected(entry,inputProfile); raw=false; select(selectionStart,selectionEnd); }
        catch(IllegalArgumentException ignored) {}
    }
    private void replaceSelection(String text) {
        int start=Math.min(selectionStart,selectionEnd),end=Math.max(selectionStart,selectionEnd);
        String result=entry.substring(0,start)+text+entry.substring(end); checkLength(result);
        entry=result; select(start+text.length(),start+text.length());
    }
    public void moveCursor(String direction,boolean extend) {
        if(!editing) return;
        int caret=selectionEnd,target=caret;
        if(direction.equals("HOME")) target=0;
        else if(direction.equals("END")) target=entry.length();
        else if(direction.equals("LEFT")) {
            if(!extend && selectionStart!=selectionEnd) target=Math.min(selectionStart,selectionEnd);
            else if(caret>0) target=entry.offsetByCodePoints(caret,-1);
        } else if(direction.equals("RIGHT")) {
            if(!extend && selectionStart!=selectionEnd) target=Math.max(selectionStart,selectionEnd);
            else if(caret<entry.length()) target=entry.offsetByCodePoints(caret,1);
        }
        select(extend ? selectionStart : target,target);
    }
    public void key(String key) {
        String direction=key.startsWith("SELECT_") ? key.substring(7) : key;
        if(direction.equals("LEFT") || direction.equals("RIGHT") || direction.equals("HOME") || direction.equals("END")) {
            moveCursor(direction,key.startsWith("SELECT_")); return;
        }
        if(key.startsWith("PASTE:")) { paste(key.substring(6)); return; }
        if(key.equals("ANGLE")) { angleUnit=AngleUnit.values()[(angleUnit.ordinal()+1)%AngleUnit.values().length]; return; }
        if(key.equals("UNDO")) { if(!undo.isEmpty()) restore(undo.pop()); return; }
        State before=new State(this);
        try {
            if(key.matches("[0-9.]")) {
                editing=true;
                if(key.equals(".")) {
                    if(raw) { replaceSelection(Character.toString(inputProfile.decimal)); resolveExplicitDecimal(); }
                    else {
                        String remaining=entry.substring(0,Math.min(selectionStart,selectionEnd))+entry.substring(Math.max(selectionStart,selectionEnd));
                        if(remaining.contains(".") || remaining.contains("e") || remaining.contains("E")) return;
                        replaceSelection(entry.isEmpty() || entry.equals("-") ? "0." : ".");
                    }
                } else replaceSelection(key);
                resolveRaw();
            } else if(key.equals("BACK") || key.equals("DROP") || key.equals("DELETE")) {
                if(editing) {
                    if(selectionStart!=selectionEnd) replaceSelection("");
                    else if(key.equals("DELETE")) {
                        if(selectionStart<entry.length()) { int start=selectionStart; select(start,entry.offsetByCodePoints(start,1)); replaceSelection(""); }
                    } else if(selectionStart>0) {
                        int end=selectionStart;
                        select(entry.offsetByCodePoints(end,-1),end); replaceSelection("");
                    }
                    resolveRaw();
                } else if(!stack.isEmpty()) stack.remove(stack.size()-1);
            } else if(key.equals("±") && editing) {
                boolean negative=entry.startsWith("-") || entry.startsWith("−");
                entry=negative ? entry.substring(1) : "-"+entry;
                select(selectionStart+(negative?-1:1),selectionEnd+(negative?-1:1)); resolveRaw();
            } else if(key.equals("CE") || key.equals("CLEAR")) {
                if(key.equals("CLEAR")) stack.clear();
                entry=""; editing=false; raw=false; select(0,0);
            } else if(key.equals("ENTER")) {
                if(editing) commit(); else { need(1); stack.add(top()); }
            } else {
                commit();
                switch(key) {
                    case "π": stack.add(UnifiedReal.PI); break;
                    case "e": stack.add(UnifiedReal.E); break;
                    case "DUP": need(1); stack.add(top()); break;
                    case "SWAP": need(2); Collections.swap(stack,stack.size()-1,stack.size()-2); break;
                    case "+": case "−": case "×": case "÷": case "xʸ":
                        need(2);
                        UnifiedReal x=top(),y=stack.get(stack.size()-2),result;
                        switch(key) {
                            case "+": result=y.add(x); break;
                            case "−": result=y.subtract(x); break;
                            case "×": result=y.multiply(x); break;
                            case "÷": result=y.divide(x); break;
                            default: result=y.pow(x);
                        }
                        stack.remove(stack.size()-1); stack.set(stack.size()-1,result); break;
                    default:
                        need(1); UnifiedReal a=top(),r;
                        switch(key) {
                            case "±": r=a.negate(); break;
                            case "√": r=a.sqrt(); break;
                            case "1/x": r=a.inverse(); break;
                            case "sin": r=radians(a).sin(); break;
                            case "cos": r=radians(a).cos(); break;
                            case "tan": r=radians(a).tan(); break;
                            case "ln": r=a.ln(); break;
                            case "exp": case "eˣ": r=a.exp(); break;
                            default: throw new IllegalArgumentException("Unknown operation");
                        }
                        stack.set(stack.size()-1,r);
                }
                if(!stack.isEmpty()) ValueFormatter.format(top(),true,ValueFormatter.Notation.SCIENTIFIC);
            }
            remember(before);
        } catch(RuntimeException ex) { restore(before); throw ex; }
    }
    private UnifiedReal top() { return stack.get(stack.size()-1); }
    private void need(int count) { if(stack.size()<count) throw new IllegalArgumentException("Need "+count+" stack values"); }
    private void commit() {
        resolveRaw();
        if(raw) NumberText.analyze(entry,rawProfile).canonical(); // Throws instead of guessing.
        if(!entry.isEmpty()) stack.add(parse(entry));
        entry=""; editing=false; raw=false; select(0,0);
    }
    private UnifiedReal radians(UnifiedReal value) {
        return angleUnit==AngleUnit.RAD ? value : value.multiply(UnifiedReal.PI).divide(new UnifiedReal(angleUnit==AngleUnit.DEG?180:200));
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




