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
        check(ValueFormatter.format(RpnCalculator.parse("56123456789.456"),false,sci).equals("≈ 56123456789.5"),"large decimal rounds with guard digits");
        for(int tail=100;tail<1000;tail+=7) {
            String input="56789012345."+tail;
            String expected=new java.math.BigDecimal(input).round(new java.math.MathContext(12,java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString();
            check(ValueFormatter.format(RpnCalculator.parse(input),false,sci).equals((new java.math.BigDecimal(input).compareTo(new java.math.BigDecimal(expected))==0 ? "" : "≈ ")+expected),"large decimal rounding "+input);
        }
        check(ValueFormatter.clipboard(RpnCalculator.parse("56565642624.456"),sci).equals("56565642624.456"),"copy preserves large exact decimal");
        check(ValueFormatter.clipboard(RpnCalculator.parse("-0.0000000000001234567890123456"),eng).equals("-0.0000000000001234567890123456"),"copy preserves tiny exact decimal");
        check(ValueFormatter.clipboard(RpnCalculator.parse("1").divide(RpnCalculator.parse("8")),sci).equals("0.125"),"copy exact terminating rational result");
        check(ValueFormatter.format(RpnCalculator.parse("56565642624.456"),true,sci).equals("56565642624.456"),"newest terminating decimal stays exact");
        RpnCalculator toggled=new RpnCalculator(); number(toggled,"56565642624.456"); toggled.key("ENTER");
        for(int i=0;i<10;i++) { toggled.formattedValues(sci,true); toggled.formattedValues(sci,false); }
        check(top(toggled).definitelyEquals(RpnCalculator.parse("56565642624.456")),"repeated fraction formatting preserves exact input");
        check(ValueFormatter.clipboard(top(toggled),sci).equals("56565642624.456"),"copy stays exact after fraction toggles");
        UnifiedReal huge = RpnCalculator.parse("123456789012345678901234567890");
        check(ValueFormatter.format(huge,true,sci).equals("123456789012345678901234567890"),"newest integer expanded");
        check(ValueFormatter.format(huge,false,sci).equals("≈ 1.23456789012e+29"),"older integer compact scientific");
        check(ValueFormatter.format(huge,false,eng).equals("≈ 123.456789012e+27"),"engineering multiple of three");
        check(ValueFormatter.format(RpnCalculator.parse("-0.00000001"),false,eng).equals("-10e−9"),"negative engineering exponent");
        check(ValueFormatter.format(UnifiedReal.ZERO,true,sci).equals("0"),"zero formatting");
        String exponential = ValueFormatter.format(RpnCalculator.parse("234.56").exp(),true,sci);
        check(exponential.length()<30 && exponential.contains("e+101"),"large noninteger exponential stays compact");
        check(ValueFormatter.format(RpnCalculator.parse("1e-20"),false,sci).equals("1e−20"),"tiny rational remains visible");
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
        check(NumberText.normalize("1\u2009234.5",'.',',',3,3).equals("1234.5"),"thin space computer grouping");
        check(NumberText.normalize("1 234.5",'.',',',3,3).equals("1234.5"),"space computer grouping");
        for (String invalid : new String[]{"1,23.4","1,,234","1,234,","1\n2","$12","1/2","NaN","Infinity","", "1e10000"}) {
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
        check(ValueFormatter.clipboard(RpnCalculator.parse("-1e-20"),sci).equals("-0.00000000000000000001"),"clipboard preserves tiny exact value with ASCII minus");
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
        check(ValueFormatter.format(RpnCalculator.parse("0.3"),true,sci,true).equals("3/10"),"fraction mode displays exact decimal rational");
        check(ValueFormatter.format(third,true,sci,true).equals("1/3"),"fraction mode preserves recurring rational");
        check(ValueFormatter.format(new UnifiedReal(new com.android.calculator2.BoundedRational(6,-8)),true,sci,true).equals("-3/4"),"fraction sign and reduction");
        check(ValueFormatter.format(UnifiedReal.TWO,true,sci,true).equals("2"),"fraction mode leaves integers natural");
        check(ValueFormatter.format(UnifiedReal.ZERO,true,sci,true).equals("0"),"fraction mode zero");
        check(ValueFormatter.format(huge,true,sci,true).equals(huge.bigIntegerValue().toString()),"fraction mode retains full newest integer");
        check(ValueFormatter.format(huge,false,sci,true).contains("e+29"),"older integer remains compact in fraction mode");
        UnifiedReal rootTwo=UnifiedReal.TWO.sqrt();
        check(ValueFormatter.format(rootTwo,true,sci,true).equals(ValueFormatter.format(rootTwo,true,sci)),"irrational fraction fallback");
        check(ValueFormatter.format(UnifiedReal.PI,true,sci,true).equals(ValueFormatter.format(UnifiedReal.PI,true,sci)),"pi fraction fallback");
        check(ValueFormatter.format(RpnCalculator.parse("1e-20"),true,eng,true).equals("1/100000000000000000000"),"tiny rational remains exact fraction");
        RpnCalculator fractions=new RpnCalculator(); keys(fractions,"1","ENTER","3","÷");
        check(fractions.formattedValues(sci,true).get(0).equals("1/3"),"stack fraction display");
        check(fractions.formattedValues(sci,false).get(0).equals("≈ 0.333333333333"),"stack decimal display");
        check(top(fractions).multiply(new UnifiedReal(3)).definitelyOne(),"display mode leaves exact arithmetic unchanged");
        check(ValueFormatter.clipboard(top(fractions),sci,true).equals("1/3"),"fraction display copies exact fraction");
        check(ValueFormatter.clipboard(RpnCalculator.parse("0.125"),sci,true).equals("1/8"),"terminating decimal copies as fraction");
        check(ValueFormatter.clipboard(new UnifiedReal(new com.android.calculator2.BoundedRational(6,-8)),sci,true).equals("-3/4"),"copied fraction is reduced with positive denominator");
        check(ValueFormatter.clipboard(UnifiedReal.TWO,sci,true).equals("2"),"fraction mode copies integer without denominator");
        check(ValueFormatter.clipboard(UnifiedReal.PI,sci,true).equals(ValueFormatter.clipboard(UnifiedReal.PI,sci,false)),"fraction mode copies irrational decimal fallback");
        check(ValueFormatter.clipboard(top(fractions),sci,false).equals("0.333333333333"),"decimal mode retains decimal copy");
        RpnCalculator durable=new RpnCalculator();
        number(durable,"56565642624.456"); keys(durable,"ENTER","π","2","√","+","1","ENTER","3","÷");
        durable.key("ANGLE"); durable.paste("12.345"); durable.select(2,5);
        RpnCalculator restored=RpnCalculator.load(durable.save());
        check(restored.values().size()==3,"restore full stack");
        check(restored.values().get(0).definitelyEquals(RpnCalculator.parse("56565642624.456")),"restore exact large decimal");
        check(restored.values().get(1).toStringTruncated(80).equals(UnifiedReal.PI.add(UnifiedReal.TWO.sqrt()).toStringTruncated(80)),"restore exact irrational expression");
        check(top(restored).multiply(new UnifiedReal(3)).definitelyOne(),"restore exact recurring fraction");
        check(restored.entry().equals("12.345") && restored.isEditing() && restored.selectionStart()==2 && restored.selectionEnd()==5,"restore editing and cursor selection");
        check(restored.angleUnit()==RpnCalculator.AngleUnit.DEG,"restore angle mode");
        restored.key("UNDO"); check(restored.entry().equals("") && restored.values().size()==3,"restore undo history");
        restored.key("CLEAR");
        RpnCalculator cleared=RpnCalculator.load(restored.save());
        check(cleared.isEmpty() && cleared.undoCount()>0,"empty foreground state retains undo");
        cleared.key("UNDO"); check(cleared.values().size()==3,"undo accidental Clear after restoration");
        cleared.key("CLEAR"); cleared.clearUndo();
        check(cleared.isEmpty() && cleared.undoCount()==0,"empty background state discards undo");
        cleared.key("UNDO"); check(cleared.isEmpty(),"discarded undo cannot resurrect cleared values");
        RpnCalculator rawSaved=new RpnCalculator(); rawSaved.pasteText("1,234",new NumberText.Profile('.',',',3,3)); rawSaved.select(1,2);
        RpnCalculator rawRestored=RpnCalculator.load(rawSaved.save());
        check(rawRestored.rawEntry() && rawRestored.entryProblem().equals("Ambiguous") && rawRestored.entry().equals("1,234"),"restore ambiguous clipboard entry");
        check(rawRestored.selectionStart()==1 && rawRestored.selectionEnd()==2,"restore raw cursor selection");
        RpnCalculator angleSaved=new RpnCalculator(); angleSaved.setAngleUnit(RpnCalculator.AngleUnit.GRAD);
        number(angleSaved,"12.34"); angleSaved.key("sin"); angleSaved.setAngleUnit(RpnCalculator.AngleUnit.RAD);
        RpnCalculator angleRestored=RpnCalculator.load(angleSaved.save());
        check(ValueFormatter.format(top(angleRestored),true,sci).equals(ValueFormatter.format(top(angleSaved),true,sci)),"restore trig expression with original angle unit");
        RpnCalculator bounded=new RpnCalculator();
        for(int i=0;i<150;i++) bounded.paste(Integer.toString(i));
        check(bounded.undoCount()==100 && RpnCalculator.load(bounded.save()).undoCount()==100,"undo count is bounded across persistence");
        String longRaw="字".repeat(4096);
        for(int i=0;i<110;i++) bounded.pasteText(longRaw,new NumberText.Profile('.',',',3,3));
        byte[] boundedBytes=bounded.save();
        check(boundedBytes.length<=1024*1024 && bounded.undoCount()<100,"saved undo history respects byte budget");
        check(RpnCalculator.load(boundedBytes).entry().equals(longRaw),"undo eviction preserves current entry");
        for(String operation:new String[]{"±","√","1/x","ln","eˣ","sin","cos","tan"}) {
            RpnCalculator expression=new RpnCalculator(); keys(expression,"π",operation);
            RpnCalculator loaded=RpnCalculator.load(expression.save());
            check(top(loaded).toStringTruncated(60).equals(top(expression).toStringTruncated(60)),"restore unary expression "+operation);
            loaded.key("UNDO"); check(top(loaded).definitelyEquals(UnifiedReal.PI),"restore unary undo "+operation);
        }
        for(String operation:new String[]{"+","−","×","÷","xʸ"}) {
            RpnCalculator expression=new RpnCalculator(); keys(expression,"π","2","√",operation);
            RpnCalculator loaded=RpnCalculator.load(expression.save());
            check(top(loaded).toStringTruncated(60).equals(top(expression).toStringTruncated(60)),"restore binary expression "+operation);
        }
        RpnCalculator editingHistory=new RpnCalculator(); number(editingHistory,"123");
        editingHistory.key("UNDO"); check(editingHistory.entry().equals("12"),"current entry edits undo individually");
        editingHistory.key("REDO"); check(editingHistory.entry().equals("123"),"redo current entry edit");
        editingHistory.key("ENTER");
        check(editingHistory.undoCount()==2,"commit drops intermediate entry edits");
        editingHistory.key("UNDO"); check(editingHistory.entry().equals("123") && editingHistory.values().isEmpty(),"undo commit restores complete entry");
        editingHistory.key("UNDO"); check(editingHistory.isEmpty(),"next undo skips intermediate spellings");
        editingHistory.key("REDO"); check(editingHistory.entry().equals("123"),"redo complete entry");
        editingHistory.key("REDO"); check(top(editingHistory).definitelyEquals(new UnifiedReal(123)) && !editingHistory.isEditing(),"redo commit");
        RpnCalculator operationHistory=new RpnCalculator(); number(operationHistory,"12"); operationHistory.key("ENTER"); number(operationHistory,"345"); operationHistory.key("+");
        operationHistory.key("UNDO"); check(operationHistory.entry().equals("345") && top(operationHistory).definitelyEquals(new UnifiedReal(12)),"undo operator restores full entry");
        operationHistory.key("UNDO"); check(operationHistory.entry().isEmpty() && top(operationHistory).definitelyEquals(new UnifiedReal(12)),"operator commit drops intermediate edits");
        operationHistory.key("UNDO"); check(operationHistory.entry().equals("12") && operationHistory.values().isEmpty(),"older calculation remains reachable");
        operationHistory.key("REDO"); operationHistory.key("REDO"); operationHistory.key("REDO");
        check(top(operationHistory).definitelyEquals(new UnifiedReal(357)),"redo full calculation sequence");
        operationHistory.key("CLEAR"); operationHistory.key("UNDO");
        check(top(operationHistory).definitelyEquals(new UnifiedReal(357)),"undo Clear still works");
        operationHistory.key("REDO"); check(operationHistory.isEmpty(),"redo Clear");
        operationHistory.key("UNDO"); operationHistory.key("9");
        check(operationHistory.redoCount()==0,"new edit discards redo branch");
        operationHistory.key("REDO"); check(operationHistory.entry().equals("9"),"discarded redo cannot replay stale Clear");
        RpnCalculator failedCommit=new RpnCalculator(); number(failedCommit,"123"); int priorUndo=failedCommit.undoCount();
        try { failedCommit.key("+"); throw new AssertionError("underflow accepted"); } catch(IllegalArgumentException expected) {}
        check(failedCommit.undoCount()==priorUndo,"failed operation preserves entry edit history");
        failedCommit.key("UNDO"); check(failedCommit.entry().equals("12"),"edits still undo individually after failed commit");
        RpnCalculator failedRedo=new RpnCalculator(); keys(failedRedo,"π","UNDO");
        try { failedRedo.key("+"); throw new AssertionError("underflow accepted"); } catch(IllegalArgumentException expected) {}
        check(failedRedo.redoCount()==1,"failed operation preserves redo"); failedRedo.key("REDO");
        check(top(failedRedo).definitelyEquals(UnifiedReal.PI),"redo exact constant after failure");
        RpnCalculator redoSaved=new RpnCalculator(); keys(redoSaved,"π","2","√","+","UNDO");
        RpnCalculator redoRestored=RpnCalculator.load(redoSaved.save());
        check(redoRestored.redoCount()==1 && redoRestored.entry().isEmpty(),"restore redo history");
        redoRestored.key("REDO");
        check(top(redoRestored).toStringTruncated(80).equals(UnifiedReal.PI.add(UnifiedReal.TWO.sqrt()).toStringTruncated(80)),"redo exact irrational operation after restart");
        redoRestored.key("UNDO"); RpnCalculator redoCopy=redoRestored.copy(); redoCopy.key("REDO");
        check(redoRestored.redoCount()==1 && redoCopy.redoCount()==0,"redo history isolated between candidates");
        redoRestored.clearUndo(); check(redoRestored.undoCount()==0 && redoRestored.redoCount()==0,"clear history removes undo and redo");
        check("REDO".equals(KeyboardInput.command(53,0,true,false,false)),"Ctrl Y redo shortcut");
        java.io.ByteArrayOutputStream legacyBytes=new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream legacy=new java.io.DataOutputStream(legacyBytes);
        legacy.writeInt(0x52504e31); legacy.writeUTF("RAD");
        legacy.writeChar('.'); legacy.writeChar(','); legacy.writeInt(3); legacy.writeInt(3);
        legacy.writeInt(0); legacy.writeInt(1); legacy.writeInt(0); legacy.writeUTF("12.3");
        legacy.writeBoolean(true); legacy.writeBoolean(false); legacy.writeInt(2); legacy.writeInt(4);
        legacy.writeChar('.'); legacy.writeChar(','); legacy.writeInt(3); legacy.writeInt(3);
        RpnCalculator legacyRestored=RpnCalculator.load(legacyBytes.toByteArray());
        check(legacyRestored.entry().equals("12.3") && legacyRestored.selectionStart()==2 && legacyRestored.selectionEnd()==4,"restore previous saved-state format");
        legacyRestored.key("ENTER"); check(top(legacyRestored).definitelyEquals(RpnCalculator.parse("12.3")),"legacy state remains usable");
        UnifiedReal sevenThirds=RpnCalculator.parse("7").divide(RpnCalculator.parse("3"));
        check(ValueFormatter.format(sevenThirds,true,sci,1).equals("7/3"),"improper fraction display");
        check(ValueFormatter.format(sevenThirds,true,sci,2).equals("2 1/3"),"mixed fraction display");
        check(ValueFormatter.clipboard(sevenThirds.negate(),sci,2).equals("-2 1/3"),"mixed negative sign applies to whole value");
        check(ValueFormatter.format(third.negate(),true,sci,2).equals("-1/3"),"proper fraction remains proper in mixed mode");
        check(ValueFormatter.format(UnifiedReal.TWO,true,sci,2).equals("2"),"mixed mode leaves integer natural");
        check(ValueFormatter.format(UnifiedReal.PI,true,sci,2).equals(ValueFormatter.format(UnifiedReal.PI,true,sci)),"mixed mode irrational fallback");
        for(String[] example:new String[][]{{"7","3","1"},{"-7","3","-1"},{"7","-3","1"},{"-7","-3","-1"},{"5.5","2","1.5"},{"0.3","0.1","0"}}) {
            RpnCalculator remainder=new RpnCalculator(); remainder.paste(example[0]); remainder.key("ENTER"); remainder.paste(example[1]); remainder.key("rem");
            check(top(remainder).definitelyEquals(RpnCalculator.parse(example[2])),"exact remainder "+example[0]+" rem "+example[1]);
            RpnCalculator remainderSaved=RpnCalculator.load(remainder.save());
            check(top(remainderSaved).definitelyEquals(top(remainder)),"remainder survives restore");
        }
        RpnCalculator modular=new RpnCalculator(); modular.paste("-7"); modular.key("ENTER"); modular.paste("3"); modular.key("mod");
        check(top(modular).definitelyEquals(UnifiedReal.TWO),"modulo has divisor sign");
        modular.key("UNDO"); check(modular.entry().equals("3") && top(modular).definitelyEquals(new UnifiedReal(-7)),"remainder undo restores pending divisor");
        modular.key("REDO"); check(top(modular).definitelyEquals(UnifiedReal.TWO),"remainder redo");
        RpnCalculator negativeMod=new RpnCalculator(); negativeMod.paste("7"); negativeMod.key("ENTER"); negativeMod.paste("-3"); negativeMod.key("mod");
        check(top(negativeMod).definitelyEquals(new UnifiedReal(-2)),"negative-divisor modulo");
        RpnCalculator zeroDivisor=new RpnCalculator(); keys(zeroDivisor,"7","ENTER","0");
        try { zeroDivisor.key("rem"); throw new AssertionError("zero remainder divisor accepted"); } catch(ArithmeticException expected) {}
        check(top(zeroDivisor).definitelyEquals(new UnifiedReal(7)) && zeroDivisor.entry().equals("0"),"zero-divisor remainder leaves state intact");
        RpnCalculator irrationalRem=new RpnCalculator(); keys(irrationalRem,"π","2");
        try { irrationalRem.key("rem"); throw new AssertionError("irrational remainder accepted"); } catch(IllegalArgumentException expected) {}
        check(top(irrationalRem).definitelyEquals(UnifiedReal.PI) && irrationalRem.entry().equals("2"),"irrational remainder is rejected without changing state");
        RpnCalculator factorial=new RpnCalculator(); keys(factorial,"0","!");
        check(top(factorial).definitelyOne(),"zero factorial"); keys(factorial,"CLEAR","2","0","!");
        check(top(factorial).bigIntegerValue().toString().equals("2432902008176640000"),"factorial is exact beyond double precision");
        factorial.key("UNDO"); check(factorial.entry().equals("20"),"factorial undo restores full entry");
        factorial.key("REDO"); check(top(factorial).bigIntegerValue().toString().equals("2432902008176640000"),"factorial redo");
        for(String invalid:new String[]{"-1","1.5"}) {
            RpnCalculator rejected=new RpnCalculator(); rejected.paste(invalid);
            try { rejected.key("!"); throw new AssertionError("invalid factorial accepted"); } catch(IllegalArgumentException expected) {}
            check(rejected.entry().equals(invalid) && rejected.values().isEmpty(),"invalid factorial leaves entry intact");
        }
        RpnCalculator largeFactorial=new RpnCalculator(); largeFactorial.paste("20000"); largeFactorial.key("!");
        check(top(largeFactorial).bigIntegerValue().toString().length()>65535,"large factorial exceeds legacy UTF literal limit");
        check(top(RpnCalculator.load(largeFactorial.save())).bigIntegerValue().equals(top(largeFactorial).bigIntegerValue()),"large factorial saves as exact expression");
        for(String[] example:new String[][]{{"84","2,2,3,7"},{"2","2"},{"81","3,3,3,3"},{"1000000016000000063","1000000007,1000000009"},{"2305843009213693951","2305843009213693951"},{"18446744073709551615","3,5,17,257,641,65537,6700417"},{"1208925819614629174706176","2"}}) {
            RpnCalculator factorised=new RpnCalculator(); factorised.paste(example[0]); factorised.key("Factor");
            java.math.BigInteger product=java.math.BigInteger.ONE, previous=java.math.BigInteger.ZERO;
            java.util.List<String> actual=new java.util.ArrayList<>();
            for(UnifiedReal factor:factorised.values()) {
                java.math.BigInteger n=factor.bigIntegerValue(); check(n.compareTo(previous)>=0,"factors pushed smallest first");
                product=product.multiply(n); previous=n; actual.add(n.toString());
            }
            check(product.equals(new java.math.BigInteger(example[0])),"factor product is original integer");
            if(!example[0].equals("1208925819614629174706176")) check(String.join(",",actual).equals(example[1]),"prime factor multiplicities "+example[0]);
            else check(actual.size()==80,"large integer factorisation preserves multiplicity");
            RpnCalculator factorRestored=RpnCalculator.load(factorised.save());
            check(factorRestored.values().size()==factorised.values().size(),"factor stack survives restore");
            factorRestored.key("UNDO"); check(factorRestored.entry().equals(example[0]) && factorRestored.values().isEmpty(),"factor undo restores original input");
            factorRestored.key("REDO"); check(factorRestored.values().size()==factorised.values().size(),"factor redo restores all factors");
        }
        for(String invalid:new String[]{"0","1","-12","1.5"}) {
            RpnCalculator rejected=new RpnCalculator(); rejected.paste(invalid);
            try { rejected.key("Factor"); throw new AssertionError("invalid factorisation accepted"); } catch(IllegalArgumentException expected) {}
            check(rejected.entry().equals(invalid) && rejected.values().isEmpty(),"invalid factorisation is transactional");
        }
        RpnCalculator interruptedFactors=new RpnCalculator(); interruptedFactors.paste("170141183460469231731687303715884105727");
        int factorUndo=interruptedFactors.undoCount(); Thread.currentThread().interrupt();
        try { interruptedFactors.key("Factor"); throw new AssertionError("factorisation ignored cancellation"); }
        catch(com.hp.creals.CR.AbortedException expected) { } finally { Thread.interrupted(); }
        check(interruptedFactors.entry().equals("170141183460469231731687303715884105727") && interruptedFactors.values().isEmpty() && interruptedFactors.undoCount()==factorUndo,"factorisation cancellation is transactional");
        RpnCalculator inverseTrig=new RpnCalculator(); inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.DEG); inverseTrig.paste("0.5"); inverseTrig.key("asin");
        check(top(inverseTrig).definitelyEquals(new UnifiedReal(30)),"asin degrees exact");
        keys(inverseTrig,"CLEAR","0","acos"); check(top(inverseTrig).definitelyEquals(new UnifiedReal(90)),"acos degrees exact");
        keys(inverseTrig,"CLEAR","1","atan"); check(top(inverseTrig).definitelyEquals(new UnifiedReal(45)),"atan degrees exact");
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.GRAD); keys(inverseTrig,"CLEAR","1","atan");
        check(top(inverseTrig).definitelyEquals(new UnifiedReal(50)),"atan gradians exact");
        inverseTrig.setAngleUnit(RpnCalculator.AngleUnit.RAD); keys(inverseTrig,"CLEAR","0","asin"); check(top(inverseTrig).definitelyZero(),"asin radians");
        inverseTrig.paste("0.25"); inverseTrig.key("asin");
        RpnCalculator inverseSaved=RpnCalculator.load(inverseTrig.save());
        check(top(inverseSaved).toStringTruncated(60).equals(top(inverseTrig).toStringTruncated(60)),"inverse trig expression survives restore");
        RpnCalculator square=new RpnCalculator(); keys(square,"π","x²");
        check(top(RpnCalculator.load(square.save())).toStringTruncated(60).equals(top(square).toStringTruncated(60)),"square expression survives restore");
        RpnCalculator root=new RpnCalculator(); keys(root,"9","ENTER","2","root"); check(top(root).definitelyEquals(new UnifiedReal(3)),"Xth root of Y operand order");
        check("rem".equals(KeyboardInput.command(0,'%',false,false,false)),"remainder keyboard shortcut");
        check("!".equals(KeyboardInput.command(0,'!',false,false,false)),"factorial keyboard shortcut");
        check("Factor".equals(KeyboardInput.command(0,'f',false,false,false)),"factorise keyboard shortcut");
        RpnCalculator rowDrop=new RpnCalculator(); keys(rowDrop,"1","ENTER","2","ENTER","3","ENTER","4","ENTER");
        rowDrop.pasteText("1,234",new NumberText.Profile('.',',',3,3)); rowDrop.select(1,2);
        int beforeDropHistory=rowDrop.undoCount(); rowDrop.dropRows(2,0,2);
        check(rowDrop.values().size()==2 && rowDrop.values().get(0).definitelyEquals(UnifiedReal.TWO) && top(rowDrop).definitelyEquals(new UnifiedReal(4)),"drop nonadjacent rows once, preserving order");
        check(rowDrop.rawEntry() && rowDrop.entry().equals("1,234") && rowDrop.selectionStart()==1 && rowDrop.selectionEnd()==2,"row drop preserves ambiguous entry and caret");
        check(rowDrop.undoCount()==beforeDropHistory+1,"multi-row drop is one undo action");
        RpnCalculator rowDropRestored=RpnCalculator.load(rowDrop.save());
        rowDropRestored.key("UNDO"); check(rowDropRestored.values().size()==4 && rowDropRestored.entry().equals("1,234"),"undo multi-row drop after restart");
        rowDropRestored.key("REDO"); check(rowDropRestored.values().size()==2 && top(rowDropRestored).definitelyEquals(new UnifiedReal(4)),"redo multi-row drop");
        try { rowDrop.dropRows(0,99); throw new AssertionError("invalid row accepted"); } catch(IllegalArgumentException expected) {}
        check(rowDrop.values().size()==2 && rowDrop.values().get(0).definitelyEquals(UnifiedReal.TWO),"invalid row drop is atomic");
        int noOpUndo=rowDrop.undoCount(); rowDrop.dropRows(); check(rowDrop.undoCount()==noOpUndo,"empty row drop does not add undo");
        rowDrop.dropRows(1,0); check(rowDrop.values().isEmpty() && rowDrop.rawEntry(),"drop entire stack without discarding entry");
        byte[] valid=durable.save();
        try { RpnCalculator.load(java.util.Arrays.copyOf(valid,valid.length-1)); throw new AssertionError("truncated state accepted"); }
        catch(java.io.IOException expected) { check(true,"reject truncated state"); }
        valid[0]=0;
        try { RpnCalculator.load(valid); throw new AssertionError("unknown state version accepted"); }
        catch(java.io.IOException expected) { check(true,"reject unknown saved state version"); }
        System.out.println("Passed " + checks + " regression checks");
    }
}



