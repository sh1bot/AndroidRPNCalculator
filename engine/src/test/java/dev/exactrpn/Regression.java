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
        check("DELETE".equals(KeyboardInput.command(112,0,false,false,false)),"delete mapping");
        check("ESCAPE".equals(KeyboardInput.command(111,0,false,false,false)),"escape mapping");
        check("UNDO".equals(KeyboardInput.command(54,0,true,false,false)),"undo shortcut");
        check("DUP".equals(KeyboardInput.command(32,0,true,false,false)),"duplicate shortcut");
        check("SWAP".equals(KeyboardInput.command(47,0,true,false,false)),"swap shortcut");
        check(KeyboardInput.command(54,'z',false,true,false)==null,"leave alt shortcuts to system");
        check("eˣ".equals(KeyboardInput.command(33,'E',false,false,false)),"uppercase exponential shortcut");
        keys(original,"4","5","CE"); check(original.entry().isEmpty() && top(original).definitelyEquals(new UnifiedReal(3)),"clear entry preserves stack");
        RpnCalculator branch = original.copy(); branch.key("UNDO"); branch.key("ENTER");
        original.key("UNDO"); check(original.entry().equals("45") && original.values().size()==1,"undo history independent across candidates");
        RpnCalculator hp = new RpnCalculator();
        check(!hp.isEditing() && hp.entry().isEmpty(), "initial stack mode");
        hp.key("BACK"); check(hp.values().isEmpty() && !hp.isEditing(), "drop on empty stack");
        number(hp, "123456789012345678901234567890"); hp.key("ENTER");
        check(!hp.isEditing() && hp.formattedValues(sci).get(0).equals("123456789012345678901234567890"), "bottom integer expands in stack mode");
        hp.key("4");
        check(hp.isEditing() && hp.formattedValues(sci).get(0).contains("e+29"), "typing moves prior integer to compact stack");
        hp.key("BACK");
        check(hp.isEditing() && hp.entry().isEmpty() && hp.values().size()==1, "backspace to empty stays editing");
        hp.key("BACK");
        check(hp.isEditing() && hp.values().size()==1, "empty backspace never drops stack");
        hp.key("DROP");
        check(hp.isEditing() && hp.values().size()==1, "delete key also respects empty entry mode");
        hp.key("ENTER");
        check(!hp.isEditing() && hp.values().size()==1, "enter on blank entry returns to stack without duplication");
        hp.key("ENTER");
        check(hp.values().size()==2 && !hp.isEditing(), "enter duplicates in stack mode");
        hp.key("BACK");
        check(hp.values().size()==1 && !hp.isEditing(), "context key drops result");
        hp.key("UNDO");
        check(hp.values().size()==2 && !hp.isEditing(), "undo drop restores stack mode");
        keys(hp,"5","BACK","CE","UNDO");
        check(hp.isEditing() && hp.entry().isEmpty(), "undo restores explicitly empty editing mode");
        RpnCalculator editCopy = hp.copy(); editCopy.key("6");
        check(hp.isEditing() && hp.entry().isEmpty() && editCopy.entry().equals("6"), "copy preserves independent empty edit");
        keys(hp,"CE","CLEAR","9","ENTER","0");
        try { hp.key("÷"); throw new AssertionError("division accepted"); } catch(ArithmeticException expected) {}
        check(hp.isEditing() && hp.entry().equals("0") && top(hp).definitelyEquals(new UnifiedReal(9)), "error restores edit mode and entry");
        hp.key("BACK");
        try { hp.key("+"); throw new AssertionError("underflow accepted"); } catch(IllegalArgumentException expected) {}
        check(hp.isEditing() && hp.entry().isEmpty(), "failed operation preserves empty edit");
        keys(hp,"CLEAR","2","ENTER","3","+");
        check(!hp.isEditing() && top(hp).definitelyEquals(new UnifiedReal(5)), "operation ends entry mode");
        hp.setAngleUnit(RpnCalculator.AngleUnit.DEG);
        keys(hp,"CLEAR","9","0","sin"); check(top(hp).definitelyOne(), "sin 90 degrees exactly one");
        keys(hp,"CLEAR","4","5","tan"); check(top(hp).definitelyOne(), "tan 45 degrees exactly one");
        hp.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        keys(hp,"CLEAR","1","0","0","sin"); check(top(hp).definitelyOne(), "sin 100 gradians exactly one");
        keys(hp,"CLEAR","2","0","0","cos"); check(top(hp).definitelyEquals(UnifiedReal.MINUS_ONE), "cos 200 gradians exactly minus one");
        hp.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        keys(hp,"CLEAR","π","2","÷","sin"); check(top(hp).definitelyOne(), "radians unchanged");
        hp.key("7"); hp.key("ANGLE");
        check(hp.angleUnit()==RpnCalculator.AngleUnit.DEG && hp.isEditing() && hp.entry().equals("7"), "angle toggle preserves entry and stack");
        hp.key("ANGLE"); check(hp.angleUnit()==RpnCalculator.AngleUnit.GRAD, "cycle to gradians");
        hp.key("ANGLE"); check(hp.angleUnit()==RpnCalculator.AngleUnit.RAD, "cycle to radians");
        check(NumberText.normalize("1234.56",',','.',3,3).equals("1234.56"),"C decimal in comma locale");
        check(NumberText.analyze("1.234",new NumberText.Profile(',','.',3,3)).ambiguous(),"dot-only ambiguity is never guessed");
        check(NumberText.normalize("1.234,56",',','.',3,3).equals("1234.56"),"German grouped decimal");
        check(NumberText.normalize("-0,1250",',','.',3,3).equals("-0.1250"),"negative local subunit");
        check(NumberText.normalize(",5",',','.',3,3).equals("0.5"),"local leading decimal");
        check(NumberText.normalize("1,234.56",'.',',',3,3).equals("1234.56"),"US grouped decimal");
        check(NumberText.analyze("1,234",new NumberText.Profile('.',',',3,3)).ambiguous(),"US grouped integer can be a foreign decimal");
        check(NumberText.analyze("1,234",new NumberText.Profile(',','.',3,3)).ambiguous(),"local decimal can be foreign grouping");
        check(NumberText.normalize("12,34,567.89",'.',',',3,2).equals("1234567.89"),"Indian grouping");
        check(NumberText.normalize("1 234,5",',','\u202f',3,3).equals("1234.5"),"French space grouping");
        check(NumberText.normalize("1\u00a0234,5",',','\u202f',3,3).equals("1234.5"),"French nonbreaking space grouping");
        check(NumberText.normalize("1’234.5",'.','’',3,3).equals("1234.5"),"Swiss grouping");
        check(NumberText.normalize("1'234.5",'.','’',3,3).equals("1234.5"),"Swiss ASCII apostrophe");
        check(NumberText.normalize("−1,25e−3",',','.',3,3).equals("-1.25e-3"),"local scientific notation and minus");
        check(NumberText.normalize("\u061c-١٬٢٣٤٫٥",'٫','٬',3,3).equals("-1234.5"),"Arabic digits and bidi mark");
        check(NumberText.normalize("  +12.50E+2  ",',','.',3,3).equals("+12.50E+2"),"C scientific paste preserves exact text");
        for (String invalid : new String[]{"1,23.4","1,,234","1,234,","1 234.5","1\n2","$12","1/2","NaN","Infinity","", "1e10000"}) {
            try { NumberText.normalize(invalid,'.',',',3,3); throw new AssertionError("accepted invalid paste: "+invalid); }
            catch (IllegalArgumentException expected) { check(true,"reject invalid clipboard number"); }
        }
        try { NumberText.selected("123,456",new NumberText.Profile('.',',',3,2)); throw new AssertionError("bad Indian grouping accepted"); }
        catch (IllegalArgumentException expected) { check(true,"Indian secondary width enforced"); }
        RpnCalculator pasted = new RpnCalculator(); keys(pasted,"7","ENTER","8");
        pasted.paste(NumberText.normalize("1.234,56",',','.',3,3));
        check(pasted.isEditing() && pasted.entry().equals("1234.56") && top(pasted).definitelyEquals(new UnifiedReal(7)), "paste replaces entry without changing stack");
        pasted.key("UNDO"); check(pasted.entry().equals("8") && pasted.isEditing(),"paste is one undo transaction");
        try { pasted.paste("oops"); throw new AssertionError("invalid direct paste accepted"); }
        catch (IllegalArgumentException expected) { check(pasted.entry().equals("8"),"invalid paste preserves state"); }
        keys(pasted,"PASTE:0.1","ENTER","PASTE:0.2","+");
        check(top(pasted).definitelyEquals(RpnCalculator.parse("0.3")),"pasted arithmetic remains exact");
        check(ValueFormatter.clipboard(huge,sci).equals("123456789012345678901234567890"),"clipboard retains full exact integer");
        check(ValueFormatter.clipboard(RpnCalculator.parse("-1e-20"),sci).equals("-1e-20"),"clipboard uses ASCII exponent minus");
        check(ValueFormatter.clipboard(third,sci).equals("0.333333333333"),"clipboard omits approximation annotation");
        check("PASTE".equals(KeyboardInput.command(50,0,true,false,false)),"Ctrl V paste shortcut");
        check("5".equals(KeyboardInput.command(0,'٥',false,false,false)),"localized hardware digits");
        pasted.paste("1e-3"); pasted.key(".");
        check(pasted.entry().equals("1e-3"),"decimal key does not corrupt pasted exponent");
        NumberText.Profile us=new NumberText.Profile('.',',',3,3);
        NumberText.Profile de=new NumberText.Profile(',','.',3,3);
        check(NumberText.analyze("1,234",us).ambiguous(),"comma can be decimal or grouping");
        check(NumberText.analyze("1.234",us).ambiguous(),"dot can be decimal or foreign grouping");
        check(NumberText.analyze("1.234",de).ambiguous(),"locale does not resolve unknown clipboard");
        check(NumberText.analyze("1.234,56",us).canonical().equals("1234.56"),"explicit European format is unambiguous");
        check(NumberText.analyze("1,234.56",de).canonical().equals("1234.56"),"explicit US format is unambiguous");
        check(NumberText.analyze("1’234.56",de).canonical().equals("1234.56"),"apostrophe grouping is explicit");
        check(NumberText.analyze("1.2340",de).canonical().equals("1.2340"),"trailing zero resolves ambiguity");
        check(!NumberText.analyze("hello",us).valid(),"invalid pasted text has no guessed value");
        RpnCalculator cursor=new RpnCalculator(); keys(cursor,"9","ENTER");
        cursor.pasteText("1,234",us);
        check(cursor.isEditing() && cursor.rawEntry() && cursor.entryProblem().equals("Ambiguous"),"ambiguous paste stays raw and red");
        check(cursor.values().size()==1 && cursor.entry().equals("1,234"),"paste never commits");
        try { cursor.key("ENTER"); throw new AssertionError("ambiguous number committed"); } catch(IllegalArgumentException expected) {}
        check(cursor.values().size()==1 && cursor.entry().equals("1,234"),"ambiguous Enter preserves text and stack");
        try { cursor.key("+"); throw new AssertionError("ambiguous arithmetic accepted"); } catch(IllegalArgumentException expected) {}
        check(cursor.values().size()==1 && cursor.rawEntry(),"arithmetic also cannot guess ambiguity");
        cursor.select(1,2); cursor.key(".");
        check(cursor.entry().equals("1.234") && !cursor.rawEntry() && cursor.entryProblem().isEmpty(),"explicit decimal replaces selected comma");
        check(cursor.selectionStart()==2 && cursor.selectionEnd()==2,"cursor stays next to replaced decimal");
        cursor.key("ENTER"); check(top(cursor).definitelyEquals(RpnCalculator.parse("1.234")),"corrected decimal commits exactly");
        cursor.key("UNDO"); check(cursor.isEditing() && cursor.entry().equals("1.234") && cursor.selectionStart()==2,"undo restores editing and caret");
        cursor.select(1,1); cursor.key("DELETE");
        check(cursor.entry().equals("1234") && cursor.selectionStart()==1,"Delete removes character after cursor");
        cursor.key("UNDO"); cursor.select(2,2); cursor.key("BACK");
        check(cursor.entry().equals("1234") && cursor.selectionStart()==1,"Backspace removes character before cursor");
        cursor.select(1,3); cursor.key("8"); check(cursor.entry().equals("184"),"digit replaces selected range");
        cursor.key("UNDO"); check(cursor.entry().equals("1234") && cursor.selectionStart()==1 && cursor.selectionEnd()==3,"undo restores selection");
        cursor.pasteText("1,234",us); cursor.key("BACK");
        check(cursor.entry().equals("1.23") && !cursor.rawEntry(),"editing into unambiguous decimal clears warning");
        cursor.pasteText("not a number",us); check(cursor.entryProblem().equals("Invalid") && cursor.isEditing(),"invalid paste remains editable");
        cursor.select(0,cursor.entry().length()); cursor.key("7"); check(cursor.entry().equals("7") && cursor.entryProblem().isEmpty(),"replace invalid paste with valid input");
        cursor.pasteText("1234,56",de); check(cursor.entry().equals("1234.56") && cursor.isEditing(),"unambiguous local paste stays uncommitted");
        cursor.setInputProfile(de); cursor.pasteText("1.234",de); cursor.select(1,2); cursor.key(".");
        check(!cursor.rawEntry() && cursor.entry().equals("1.234"),"locale decimal key explicitly resolves dot grouping");
        cursor.pasteText("1.234",de);
        cursor.editText("1,234",2,2,true,true,de);
        check(!cursor.rawEntry() && cursor.entry().equals("1.234"),"native single-decimal edit resolves meaning");
        cursor.pasteText("1,234",us); RpnCalculator cursorCopy=cursor.copy(); cursorCopy.select(1,2); cursorCopy.key(".");
        check(cursor.rawEntry() && cursor.entryProblem().equals("Ambiguous"),"candidate edits cannot change live ambiguous entry");
        cursor.pasteText("1.23",us); cursor.select(0,0); cursor.key("8");
        check(cursor.entry().equals("81.23"),"insert at beginning");
        cursor.select(cursor.entry().length(),cursor.entry().length()); cursor.key("BACK");
        check(cursor.entry().equals("81.2"),"append-end deletion remains supported");
        cursor.pasteText("123",us); cursor.key("ENTER"); cursor.key("DELETE");
        check(!cursor.isEditing(),"Delete in stack mode drops without opening entry");
        RpnCalculator accept=new RpnCalculator(); keys(accept,"2","ENTER"); accept.pasteText("12.34",us);
        check(accept.isEditing() && accept.values().size()==1,"safe paste waits in entry without auto commit");
        accept.key("+"); check(!accept.isEditing() && top(accept).definitelyEquals(RpnCalculator.parse("14.34")),"operator accepts safe pasted number");
        accept.key("UNDO"); check(accept.isEditing() && accept.entry().equals("12.34") && top(accept).definitelyEquals(UnifiedReal.TWO),"undo restores safe paste before operator");
        accept.key("ENTER"); check(!accept.isEditing() && accept.values().size()==2,"Enter accepts safe pasted number");
        accept.pasteText("9",us); accept.key("√");
        check(!accept.isEditing() && top(accept).definitelyEquals(new UnifiedReal(3)),"function accepts safe pasted number");
        RpnCalculator commaPaste=new RpnCalculator(); keys(commaPaste,"2","ENTER"); commaPaste.pasteText("9,5",us); commaPaste.key("+");
        check(top(commaPaste).definitelyEquals(RpnCalculator.parse("11.5")),"safe comma decimal accepted by operator");
        RpnCalculator blocked=new RpnCalculator(); keys(blocked,"9","ENTER"); blocked.pasteText("1,234",us);
        for(String operation:new String[]{"ENTER","+","−","×","÷","√","sin","ln","eˣ"}) {
            try { blocked.key(operation); throw new AssertionError("ambiguous input accepted by "+operation); }
            catch(IllegalArgumentException expected) { check(blocked.rawEntry() && blocked.values().size()==1 && blocked.entry().equals("1,234"),"ambiguity blocks "+operation); }
        }
        blocked.select(1,2); blocked.key("."); blocked.key("+");
        check(top(blocked).definitelyEquals(RpnCalculator.parse("10.234")),"corrected decimal can proceed with operator");
        RpnCalculator localized=new RpnCalculator(); localized.setInputProfile(de); keys(localized,"2","ENTER");
        localized.pasteText("1.234",de); localized.select(1,2); localized.key("."); localized.key("+");
        check(top(localized).definitelyEquals(RpnCalculator.parse("3.234")),"configured comma resolves input before operator");
        accept.pasteText("1.234",us); accept.select(1,1); accept.key("DELETE");
        check(accept.entry().equals("1234") && accept.entryProblem().isEmpty(),"removing ambiguous separator makes a safe integer");
        accept.key("√"); check(!accept.isEditing(),"function accepts corrected safe entry");
        try { NumberText.normalize("1.234",'.',',',3,3); throw new AssertionError("normalizer guessed ambiguity"); }
        catch(IllegalArgumentException expected) { check(true,"all public clipboard parsing rejects ambiguity"); }
        accept.pasteText("",us); check(accept.isEditing() && accept.entry().isEmpty(),"empty paste remains in edit mode");
        accept.key("ENTER"); check(!accept.isEditing(),"Enter leaves empty pasted edit without pushing zero");
        check("LEFT".equals(KeyboardInput.command(21,0,false,false,false)),"left key mapping");
        check("RIGHT".equals(KeyboardInput.command(22,0,false,false,false)),"right key mapping");
        check("HOME".equals(KeyboardInput.command(122,0,false,false,false)),"home key mapping");
        check("END".equals(KeyboardInput.command(123,0,false,false,false)),"end key mapping");
        check("HOME".equals(KeyboardInput.command(122,0,true,false,false)),"Ctrl Home mapping");
        check("END".equals(KeyboardInput.command(123,0,true,false,false)),"Ctrl End mapping");
        RpnCalculator nav=new RpnCalculator(); nav.paste("123.45");
        nav.key("LEFT"); check(nav.selectionEnd()==5,"left moves one character");
        nav.key("RIGHT"); check(nav.selectionEnd()==6,"right moves one character");
        nav.key("HOME"); check(nav.selectionStart()==0 && nav.selectionEnd()==0,"home moves to entry start");
        nav.key("LEFT"); check(nav.selectionEnd()==0,"left stays at start");
        nav.key("END"); nav.key("RIGHT"); check(nav.selectionEnd()==6,"end and right stay at end");
        nav.key("SELECT_LEFT"); check(nav.selectionStart()==6 && nav.selectionEnd()==5,"Shift Left extends backwards");
        nav.key("SELECT_HOME"); check(nav.selectionStart()==6 && nav.selectionEnd()==0,"Shift Home selects to start");
        nav.key("RIGHT"); check(nav.selectionStart()==6 && nav.selectionEnd()==6,"right collapses selection to right edge");
        nav.key("HOME"); nav.key("SELECT_RIGHT"); check(nav.selectionStart()==0 && nav.selectionEnd()==1,"Shift Right extends forwards");
        nav.key("SELECT_END"); check(nav.selectionStart()==0 && nav.selectionEnd()==6,"Shift End selects to end");
        nav.key("LEFT"); check(nav.selectionStart()==0 && nav.selectionEnd()==0,"left collapses selection to left edge");
        nav.key("RIGHT"); nav.key("RIGHT"); nav.key("9"); check(nav.entry().equals("1293.45"),"navigation places inserted digit correctly");
        nav.key("UNDO"); check(nav.entry().equals("123.45") && nav.selectionEnd()==2,"undo restores edit and navigation position");
        nav.key("ENTER"); nav.key("HOME"); check(!nav.isEditing() && nav.selectionEnd()==0,"navigation does not start editing stack");
        System.out.println("Passed " + checks + " regression checks");
    }
}


