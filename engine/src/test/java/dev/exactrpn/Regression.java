// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;
import com.android.calculator2.UnifiedReal;
public final class Regression {
    static int checks;
    static void check(boolean b, String label) { checks++; if (!b) throw new AssertionError(label); }
    static void keys(RpnCalculator c, String... keys) { for(String k: keys) c.key(k); }
    static UnifiedReal top(RpnCalculator c) { return c.values().get(c.values().size()-1); }
    static void number(RpnCalculator c, String n) { for(char ch:n.toCharArray()) c.key(String.valueOf(ch)); }
    public static void main(String[] args) throws Exception {
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
        ValueFormatter.Notation sci = ValueFormatter.Notation.SCIENTIFIC;
        ValueFormatter.Notation eng = ValueFormatter.Notation.ENGINEERING;
        check(ValueFormatter.format(RpnCalculator.parse("0.3"),true,sci).equals("0.3"),"exact decimal display");
        UnifiedReal huge = RpnCalculator.parse("123456789012345678901234567890");
        check(ValueFormatter.format(huge,true,sci).equals("123456789012345678901234567890"),"newest integer expanded");
        check(ValueFormatter.format(huge,false,sci).equals("≈ 1.23456789012e+29"),"older integer compact scientific");
        check(ValueFormatter.format(huge,false,eng).equals("≈ 123.456789012e+27"),"engineering multiple of three");
        check(ValueFormatter.format(RpnCalculator.parse("-0.00000001"),false,eng).equals("-10e−9"),"negative engineering exponent");
        check(ValueFormatter.format(UnifiedReal.ZERO,true,sci).equals("0"),"zero formatting");
        String exponential = ValueFormatter.format(RpnCalculator.parse("234.56").exp(),true,sci);
        check(exponential.length()<30 && exponential.contains("e+101"),"large noninteger exponential stays compact");
        check(ValueFormatter.format(RpnCalculator.parse("1e-20"),true,sci).equals("1e−20"),"tiny rational remains visible");
        UnifiedReal third = UnifiedReal.ONE.divide(new UnifiedReal(3));
        check(ValueFormatter.format(third,true,sci).equals("≈ 0.333333333333"),"recurring fraction limited precision");
        keys(c,"CLEAR","1","eˣ"); check(top(c).definitelyEquals(UnifiedReal.E),"e to x key");
        check(third.multiply(new UnifiedReal(3)).definitelyOne(),"display retains exact stack arithmetic");
        RpnCalculator original = new RpnCalculator(); keys(original,"1","ENTER","2");
        RpnCalculator candidate = original.copy(); candidate.key("+");
        check(original.entry().equals("2") && original.values().size()==1,"candidate does not mutate live state");
        candidate.key("UNDO");
        check(candidate.entry().equals("2") && candidate.values().size()==1,"candidate preserves undo");
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean aborted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread difficult = new Thread(() -> {
            started.countDown();
            try { com.hp.creals.CR.PI.subtract(com.hp.creals.CR.PI).signum(); }
            catch(com.hp.creals.CR.AbortedException expected) { aborted.set(true); }
            catch(Throwable unexpected) { failure.set(unexpected); }
        });
        difficult.setDaemon(true); difficult.start(); started.await(); Thread.sleep(25);
        difficult.interrupt(); difficult.join(2000);
        check(!difficult.isAlive() && aborted.get() && failure.get()==null,"interrupt stops difficult real comparison");
        original.key("+"); check(top(original).definitelyEquals(new UnifiedReal(3)),"calculation works after cancelled candidate");
        check("7".equals(KeyboardInput.command(14,'7',false,false,false)),"digit key mapping");
        check("7".equals(KeyboardInput.command(151,0,false,false,false)),"numpad digit mapping");
        check("+".equals(KeyboardInput.command(157,0,false,false,false)),"numpad arithmetic");
        check("+".equals(KeyboardInput.command(81,'+',false,false,false)),"shifted plus mapping");
        check("ENTER".equals(KeyboardInput.command(66,0,false,false,false)),"enter key mapping");
        check("ENTER".equals(KeyboardInput.command(160,0,false,false,false)),"numpad enter mapping");
        check("ENTER".equals(KeyboardInput.command(62,' ',false,false,false)),"space mapping");
        check("BACK".equals(KeyboardInput.command(67,0,false,false,false)),"backspace mapping");
        check("DROP".equals(KeyboardInput.command(112,0,false,false,false)),"delete mapping");
        check("ESCAPE".equals(KeyboardInput.command(111,0,false,false,false)),"escape mapping");
        check("UNDO".equals(KeyboardInput.command(54,0,true,false,false)),"undo shortcut");
        check("DUP".equals(KeyboardInput.command(32,0,true,false,false)),"duplicate shortcut");
        check("SWAP".equals(KeyboardInput.command(47,0,true,false,false)),"swap shortcut");
        check(KeyboardInput.command(54,'z',false,true,false)==null,"leave alt shortcuts to system");
        check("eˣ".equals(KeyboardInput.command(33,'E',false,false,false)),"uppercase exponential shortcut");
        keys(original,"4","5","CE"); check(original.entry().isEmpty() && top(original).definitelyEquals(new UnifiedReal(3)),"clear entry preserves stack");
        RpnCalculator branch = original.copy(); branch.key("UNDO"); branch.key("ENTER");
        original.key("UNDO"); check(original.entry().equals("45") && original.values().size()==1,"undo history independent across candidates");
        System.out.println("Passed " + checks + " regression checks");
    }
}
