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
    static UnifiedReal root(UnifiedReal base,UnifiedReal index) {
        BigInteger degree=index.bigIntegerValue();
        if(degree==null) return base.pow(index.inverse());
        if(degree.signum()==0) throw new ArithmeticException("Root degree cannot be zero");
        boolean inverse=degree.signum()<0; degree=degree.abs();
        if(degree.equals(BigInteger.ONE)) return inverse ? base.inverse() : base;
        if(base.boundedRationalValue()!=null) {
            BigInteger[] ratio=parts(base); boolean negative=ratio[0].signum()<0;
            if(negative && !degree.testBit(0)) throw new ArithmeticException("Even root of a negative number");
            BigInteger numerator=exactRoot(ratio[0].abs(),degree), denominator=exactRoot(ratio[1],degree);
            if(numerator!=null && denominator!=null) {
                if(negative) numerator=numerator.negate();
                UnifiedReal result=new UnifiedReal(new BoundedRational(numerator,denominator));
                return inverse ? result.inverse() : result;
            }
        }
        boolean negative=base.signum()<0;
        if(negative && !degree.testBit(0)) throw new ArithmeticException("Even root of a negative number");
        UnifiedReal result=(negative ? base.negate() : base).pow(new UnifiedReal(degree).inverse());
        if(negative) result=result.negate();
        return inverse ? result.inverse() : result;
    }
    private static BigInteger exactRoot(BigInteger n,BigInteger degree) {
        if(n.compareTo(BigInteger.ONE)<=0 || degree.equals(BigInteger.ONE)) return n;
        if(degree.compareTo(BigInteger.valueOf(n.bitLength()))>0) return null;
        int k=degree.intValue(); BigInteger divisor=BigInteger.valueOf(k), previous=BigInteger.valueOf(k-1);
        BigInteger x=BigInteger.ONE.shiftLeft((n.bitLength()+k-1)/k);
        while(true) {
            check(); BigInteger next=x.multiply(previous).add(n.divide(x.pow(k-1))).divide(divisor);
            if(next.compareTo(x)>=0) return x.pow(k).equals(n) ? x : null;
            x=next;
        }
    }
    static List<BigInteger> factors(BigInteger n) { return factors(n,new HashMap<>()); }
    private static List<BigInteger> factors(BigInteger n,Map<BigInteger,Boolean> proofs) {
        if(n.compareTo(TWO)<0) throw new IllegalArgumentException("Factorisation requires an integer greater than 1");
        List<BigInteger> factors=new ArrayList<>();
        for(int small:new int[]{2,3,5,7,11,13,17,19,23,29,31,37}) {
            BigInteger p=BigInteger.valueOf(small);
            while(n.mod(p).signum()==0) { check(); factors.add(p); n=n.divide(p); }
        }
        Deque<BigInteger> pending=new ArrayDeque<>(); if(!n.equals(BigInteger.ONE)) pending.push(n);
        while(!pending.isEmpty()) {
            check(); n=pending.pop();
            if(provenPrime(n,proofs)) { factors.add(n); continue; }
            BigInteger p=rho(n); pending.push(p); pending.push(n.divide(p));
        }
        Collections.sort(factors); return factors;
    }
    private static boolean provenPrime(BigInteger n,Map<BigInteger,Boolean> proofs) {
        if(n.bitLength()<=64) return prime64(n);
        Boolean cached=proofs.get(n); if(cached!=null) return cached;
        // A failed probable-prime test proves compositeness. Passing is only a filter:
        // certify candidates by Pocklington using the fully proven factorisation of n-1.
        if(!n.isProbablePrime(80)) { proofs.put(n,false); return false; }
        BigInteger minusOne=n.subtract(BigInteger.ONE);
        Set<BigInteger> primeDivisors=new HashSet<>(factors(minusOne,proofs));
        for(BigInteger q:primeDivisors) {
            for(BigInteger a=TWO;;a=a.add(BigInteger.ONE)) {
                check();
                if(!a.modPow(minusOne,n).equals(BigInteger.ONE)) { proofs.put(n,false); return false; }
                BigInteger gcd=a.modPow(minusOne.divide(q),n).subtract(BigInteger.ONE).gcd(n);
                if(gcd.equals(BigInteger.ONE)) break;
                if(!gcd.equals(n)) { proofs.put(n,false); return false; }
            }
        }
        proofs.put(n,true); return true;
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
