// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;
import com.android.calculator2.BoundedRational;
import java.math.BigInteger;
import java.util.*;

/** Integer functions and exact rational remainder; no conversion through doubles. */
final class ExactFunctions {
    private static final BigInteger TWO=BigInteger.valueOf(2), THREE=BigInteger.valueOf(3);
    static BigInteger integer(UnifiedReal value,String name) {
        BigInteger n=value.bigIntegerValue();
        if(n==null) throw new IllegalArgumentException(name+" requires an integer");
        return n;
    }
    static UnifiedReal remainder(UnifiedReal y,UnifiedReal x,boolean modulo) {
        BigInteger[] a=parts(y), b=parts(x);
        if(b[0].signum()==0) throw new ArithmeticException("Divide by zero");
        BigInteger quotient=a[0].multiply(b[1]).divide(a[1].multiply(b[0]));
        BigInteger denominator=a[1].multiply(b[1]);
        BigInteger numerator=a[0].multiply(b[1]).subtract(b[0].multiply(a[1]).multiply(quotient));
        if(modulo && numerator.signum()!=0 && numerator.signum()!=b[0].signum()) numerator=numerator.add(b[0].multiply(a[1]));
        BigInteger gcd=numerator.gcd(denominator);
        return new UnifiedReal(new BoundedRational(numerator.divide(gcd),denominator.divide(gcd)));
    }
    private static BigInteger[] parts(UnifiedReal value) {
        if(value.boundedRationalValue()==null) throw new IllegalArgumentException("Remainder requires rational operands");
        String[] parts=value.boundedRationalValue().toNiceString().split("/");
        return new BigInteger[]{new BigInteger(parts[0]),parts.length==1 ? BigInteger.ONE : new BigInteger(parts[1])};
    }
    static List<BigInteger> factors(BigInteger n) {
        if(n.compareTo(TWO)<0) throw new IllegalArgumentException("Factorisation requires an integer greater than 1");
        List<BigInteger> factors=new ArrayList<>();
        for(BigInteger p:new BigInteger[]{TWO,THREE}) {
            while(n.mod(p).signum()==0) { check(); factors.add(p); n=n.divide(p); }
        }
        // Above 64 bits, exact trial division avoids treating a probable prime as proven prime.
        BigInteger divisor=BigInteger.valueOf(5); int step=2;
        while(n.bitLength()>64 && divisor.multiply(divisor).compareTo(n)<=0) {
            check();
            while(n.mod(divisor).signum()==0) { check(); factors.add(divisor); n=n.divide(divisor); }
            divisor=divisor.add(BigInteger.valueOf(step)); step=6-step;
        }
        Deque<BigInteger> pending=new ArrayDeque<>(); if(!n.equals(BigInteger.ONE)) pending.push(n);
        while(!pending.isEmpty()) {
            check(); n=pending.pop();
            if(n.bitLength()>64 || prime64(n)) { factors.add(n); continue; }
            BigInteger p=rho(n); pending.push(p); pending.push(n.divide(p));
        }
        Collections.sort(factors); return factors;
    }
    private static boolean prime64(BigInteger n) {
        if(n.equals(TWO) || n.equals(THREE)) return true;
        if(!n.testBit(0)) return false;
        BigInteger minusOne=n.subtract(BigInteger.ONE);
        int shifts=minusOne.getLowestSetBit(); BigInteger odd=minusOne.shiftRight(shifts);
        // These bases deterministically cover every unsigned 64-bit integer.
        for(long base:new long[]{2,325,9375,28178,450775,9780504,1795265022}) {
            check(); BigInteger a=BigInteger.valueOf(base).mod(n); if(a.signum()==0) continue;
            BigInteger x=a.modPow(odd,n);
            if(x.equals(BigInteger.ONE) || x.equals(minusOne)) continue;
            boolean witness=true;
            for(int i=1;i<shifts;i++) { x=x.multiply(x).mod(n); if(x.equals(minusOne)) { witness=false; break; } }
            if(witness) return false;
        }
        return true;
    }
    private static BigInteger rho(BigInteger n) {
        if(!n.testBit(0)) return TWO;
        for(BigInteger c=BigInteger.ONE;;c=c.add(BigInteger.ONE)) {
            BigInteger x=TWO,y=TWO,d=BigInteger.ONE;
            while(d.equals(BigInteger.ONE)) {
                check(); x=x.multiply(x).add(c).mod(n);
                y=y.multiply(y).add(c).mod(n); y=y.multiply(y).add(c).mod(n);
                d=x.subtract(y).abs().gcd(n);
            }
            if(!d.equals(n)) return d;
        }
    }
    private static void check() { if(Thread.currentThread().isInterrupted()) throw new com.hp.creals.CR.AbortedException(); }
}
