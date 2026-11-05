// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;
import java.math.BigInteger;

/** Exact expression nodes for persistence; never reconstruct a value from display text. */
final class SavedValue {
    final String op, literal;
    final SavedValue left,right;
    SavedValue(String op,String literal,SavedValue left,SavedValue right) {
        this.op=op; this.literal=literal; this.left=left; this.right=right;
    }
    static SavedValue of(UnifiedReal value,String op,SavedValue left,SavedValue right) {
        BoundedRational rational=value.boundedRationalValue();
        return rational!=null && rational.toString().length()<60000 ? new SavedValue("r",rational.toString(),null,null)
            : new SavedValue(op,"",left,right);
    }
    UnifiedReal evaluate(UnifiedReal a,UnifiedReal b) {
        switch(op) {
            case "r": {
                String[] parts=literal.split("/",-1);
                if(parts.length!=2) throw new IllegalArgumentException("Invalid rational");
                BigInteger denominator=new BigInteger(parts[1]);
                if(denominator.signum()==0) throw new IllegalArgumentException("Zero denominator");
                return new UnifiedReal(new BoundedRational(new BigInteger(parts[0]),denominator));
            }
            case "π": return UnifiedReal.PI;
            case "e": return UnifiedReal.E;
            case "+": return a.add(b);
            case "−": return a.subtract(b);
            case "×": return a.multiply(b);
            case "÷": return a.divide(b);
            case "xʸ": return a.pow(b);
            case "root": return ExactFunctions.root(a,b);
            case "rem": case "mod": return ExactFunctions.remainder(a,b,op.equals("mod"));
            case "±": return a.negate();
            case "√": return a.sqrt();
            case "x²": return a.multiply(a);
            case "!": return a.fact();
            case "1/x": return a.inverse();
            case "ln": return a.ln();
            case "exp": case "eˣ": return a.exp();
            default:
                String[] parts=op.split(":",-1);
                if(parts.length!=2) throw new IllegalArgumentException("Unknown saved operation");
                RpnCalculator.AngleUnit unit=RpnCalculator.AngleUnit.valueOf(parts[1]);
                if(parts[0].equals("asin") || parts[0].equals("acos") || parts[0].equals("atan")) {
                    UnifiedReal angle=parts[0].equals("asin") ? a.asin() : parts[0].equals("acos") ? a.acos() : a.atan();
                    return unit==RpnCalculator.AngleUnit.RAD ? angle : angle.divide(UnifiedReal.PI).multiply(new UnifiedReal(unit==RpnCalculator.AngleUnit.DEG?180:200));
                }
                UnifiedReal radians=unit==RpnCalculator.AngleUnit.RAD ? a
                    : a.multiply(UnifiedReal.PI).divide(new UnifiedReal(unit==RpnCalculator.AngleUnit.DEG ? 180 : 200));
                switch(parts[0]) {
                    case "sin": return radians.sin();
                    case "cos": return radians.cos();
                    case "tan": return radians.tan();
                    default: throw new IllegalArgumentException("Unknown saved function");
                }
        }
    }
}
