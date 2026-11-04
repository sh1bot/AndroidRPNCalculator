// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.regex.Pattern;

/** Exact, locale-aware clipboard parsing. Dot-only ambiguity deliberately favors C decimals. */
public final class NumberText {
    private static final Pattern C_NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private NumberText() {}
    public static String normalize(String text, char decimal, char grouping, int primary, int secondary) {
        if (text == null || text.length() > 4096) throw invalid();
        StringBuilder cleaned = new StringBuilder();
        for (int i=0;i<text.length();i++) {
            char c = text.charAt(i);
            if (c=='\u200e' || c=='\u200f' || c=='\u061c' || (c>='\u2066' && c<='\u2069')) continue;
            int digit = Character.digit(c,10);
            if (digit>=0) cleaned.append((char)('0'+digit));
            else if (c=='\u2212') cleaned.append('-');
            else if ((space(grouping) && space(c)) || (grouping=='\u2019' && c=='\'')) cleaned.append(grouping);
            else cleaned.append(c);
        }
        String s = trim(cleaned.toString());
        if (C_NUMBER.matcher(s).matches()) return validate(s);
        String exponent = "";
        int e = Math.max(s.indexOf('e'),s.indexOf('E'));
        if (e>=0) {
            exponent = s.substring(e);
            if (!exponent.matches("[eE][+-]?[0-9]+")) throw invalid();
            s = s.substring(0,e);
        }
        String sign = "";
        if (s.startsWith("-") || s.startsWith("+")) { sign = s.substring(0,1); s = s.substring(1); }
        int point = s.indexOf(decimal);
        if (point>=0 && s.indexOf(decimal,point+1)>=0) throw invalid();
        String whole = point<0 ? s : s.substring(0,point);
        String fraction = point<0 ? "" : s.substring(point+1);
        if (!fraction.matches("[0-9]*")) throw invalid();
        if (whole.indexOf(grouping)>=0) {
            if (primary<=0 || grouping==decimal) throw invalid();
            int repeat = secondary>0 ? secondary : primary;
            String[] groups = whole.split(Pattern.quote(Character.toString(grouping)),-1);
            if (groups.length<2 || groups[0].isEmpty() || groups[0].length()>repeat) throw invalid();
            for (int i=0;i<groups.length;i++) {
                if (!groups[i].matches("[0-9]+")) throw invalid();
                if (i>0 && groups[i].length()!=(i==groups.length-1 ? primary : repeat)) throw invalid();
            }
            whole = String.join("",groups);
        } else if (!whole.matches("[0-9]*")) throw invalid();
        if (whole.isEmpty() && fraction.isEmpty()) throw invalid();
        return validate(sign+(whole.isEmpty()?"0":whole)+(point>=0?"."+fraction:"")+exponent);
    }
    private static String validate(String s) {
        if (!C_NUMBER.matcher(s).matches()) throw invalid();
        RpnCalculator.parse(s); // Retain the controller's precision and exponent limits.
        return s;
    }
    private static boolean space(char c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }
    private static String trim(String s) {
        int start=0,end=s.length();
        while(start<end && space(s.charAt(start))) start++;
        while(end>start && space(s.charAt(end-1))) end--;
        return s.substring(start,end);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Clipboard must contain one valid number"); }
}
