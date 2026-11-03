// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;
import com.android.calculator2.UnifiedReal;
public final class Regression {
    static int checks;
    static void check(boolean b, String label) { checks++; if (!b) throw new AssertionError(label); }
    static void keys(RpnCalculator c, String... keys) { for(String k: keys) c.key(k); }
    static UnifiedReal top(RpnCalculator c) { return c.values().get(c.values().size()-1); }
    static void number(RpnCalculator c, String n) { for(char ch:n.toCharArray()) c.key(String.valueOf(ch)); }
    public static void main(String[] args) {
        RpnCalculator c = new RpnCalculator();
        number(c,"0.1"); c.key("ENTER"); number(c,"0.2"); c.key("+");
        check(top(c).definitelyEquals(RpnCalculator.parse("0.3")), "exact decimal addition");
        keys(c,"CLEAR","2","ENTER","3","−"); check(top(c).definitelyEquals(UnifiedReal.MINUS_ONE),"subtraction order");
        keys(c,"CLEAR","2","ENTER","3","÷"); check(top(c).multiply(new UnifiedReal(3)).definitelyEquals(UnifiedReal.TWO),"division order and exact rational");
        keys(c,"CLEAR","2","√","ENTER","×"); check(top(c).definitelyEquals(UnifiedReal.TWO),"sqrt simplification");
        keys(c,"CLEAR","π","sin"); check(top(c).definitelyZero(),"symbolic sine pi");
        keys(c,"CLEAR","2","ENTER","3","xʸ"); check(top(c).definitelyEquals(new UnifiedReal(8)),"power order");
        keys(c,"UNDO"); check(c.values().size()==1 && c.entry().equals("3"),"undo restores pending entry");
        keys(c,"CLEAR","5","ENTER","0");
        try { c.key("÷"); throw new AssertionError("zero division accepted"); } catch(ArithmeticException expected) {}
        check(c.values().size()==1 && c.entry().equals("0"),"zero division preserves state");
        keys(c,"CLEAR","1","±","ENTER");
        try { c.key("√"); throw new AssertionError("negative root accepted"); } catch(ArithmeticException expected) {}
        check(top(c).definitelyEquals(UnifiedReal.MINUS_ONE),"domain error preserves stack");
        keys(c,"CLEAR","1","ENTER","2","ENTER","SWAP"); check(top(c).definitelyOne(),"swap");
        keys(c,"DROP"); check(top(c).definitelyEquals(UnifiedReal.TWO),"drop");
        keys(c,"DUP"); check(c.values().size()==2,"duplicate");
        keys(c,"CLEAR");
        try { c.key("+"); throw new AssertionError("underflow accepted"); } catch(IllegalArgumentException expected) {}
        check(c.values().isEmpty(),"underflow preserves stack");
        check(RpnCalculator.format(UnifiedReal.TWO.sqrt()).contains("√"),"symbolic display");
        check(RpnCalculator.parse("1e-20").multiply(RpnCalculator.parse("1e20")).definitelyOne(),"exact decimal parser");
        // Cancellation must interrupt undecidable real comparisons and roll back pending input.
        keys(c,"1","ENTER","0"); Thread.currentThread().interrupt();
        try { c.key("÷"); } catch(RuntimeException expected) {} finally { Thread.interrupted(); }
        check(c.values().size()==1 && c.entry().equals("0"),"interruption/error rollback");
        System.out.println("Passed " + checks + " regression checks");
    }
}
