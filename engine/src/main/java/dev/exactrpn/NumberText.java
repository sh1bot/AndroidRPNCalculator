// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/** Exact numeric parsing, with explicit ambiguity detection for untrusted clipboard text. */
public final class NumberText {
    public static final class Profile {
        public final char decimal, grouping;
        public final int primary, secondary, radix;

        public Profile(char decimal, char grouping, int primary, int secondary) {
            this(decimal, grouping, primary, secondary, 10);
        }

        public Profile(char decimal, char grouping, int primary, int secondary, int radix) {
            RadixMode.of(radix);
            this.decimal = decimal;
            this.grouping = grouping;
            this.primary = primary;
            this.secondary = secondary;
            this.radix = radix;
        }
    }

    public static final Profile COMPUTER = new Profile('.', '\u2009', 3, 3);

    public static final class Analysis {
        public final List<String> candidates;

        private Analysis(List<String> values) {
            candidates = Collections.unmodifiableList(values);
        }

        public boolean ambiguous() {
            return candidates.size() > 1;
        }

        public boolean valid() {
            return !candidates.isEmpty();
        }

        public String canonical() {
            if (candidates.size() != 1)
                throw CalculationError.invalid(
                        ambiguous()
                                ? CalculationError.Code.AMBIGUOUS_NUMBER
                                : CalculationError.Code.INVALID_NUMBER);
            return candidates.get(0);
        }
    }

    private NumberText() {}

    public static Analysis analyze(String text, Profile local) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        int radix = local.radix, group = RadixMode.of(radix).grouping;
        Profile[] profiles = {
            new Profile('.', (char) 0, 0, 0, radix),
            local,
            new Profile('.', '\u2009', group, group, radix),
            new Profile('.', '\u2019', group, group, radix),
            new Profile('.', ',', group, group, radix),
            new Profile(',', '.', group, group, radix),
            new Profile(',', '\u202f', group, group, radix)
        };
        for (Profile profile : profiles) {
            try {
                String canonical = parse(text, profile);
                BigInteger[] parts = ExactFunctions.parts(RpnCalculator.parse(canonical, radix));
                values.putIfAbsent(parts[0] + "/" + parts[1], canonical);
            } catch (IllegalArgumentException | ArithmeticException ignored) {
            }
        }
        return new Analysis(new ArrayList<>(values.values()));
    }

    public static String selected(String text, Profile profile) {
        return parse(text, profile);
    }

    private static String parse(String text, Profile profile) {
        if (text == null || text.length() > EntryEditor.MAX_ENTRY_LENGTH) throw invalid();
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u200e' || c == '\u200f' || c == '\u061c' || (c >= '\u2066' && c <= '\u2069'))
                continue;
            int digit = Character.digit(c, profile.radix);
            if (digit >= 0)
                cleaned.append(Character.toUpperCase(Character.forDigit(digit, profile.radix)));
            else if (c == '\u218a') cleaned.append('A');
            else if (c == '\u218b') cleaned.append('B');
            else if (c == '\u2212') cleaned.append('-');
            else if ((space(profile.grouping) && space(c))
                    || (profile.grouping == '\u2019' && c == '\''))
                cleaned.append(profile.grouping);
            else cleaned.append(c);
        }
        String value = trim(cleaned.toString());
        String exponent = "";
        int p = Math.max(value.indexOf('p'), value.indexOf('P'));
        int e = profile.radix == 10 ? Math.max(value.indexOf('e'), value.indexOf('E')) : -1;
        if (p >= 0 && e >= 0) throw invalid();
        int marker = Math.max(p, e);
        char exponentSymbol =
                profile.radix == 10 && e >= 0 ? value.charAt(e) : profile.radix == 10 ? 'e' : 'P';
        if (marker >= 0) {
            exponent = value.substring(marker + 1);
            value = value.substring(0, marker);
            String sign = "";
            if (exponent.startsWith("+") || exponent.startsWith("-")) {
                sign = exponent.substring(0, 1);
                exponent = exponent.substring(1);
            }
            if (exponent.isEmpty() || !digits(exponent, profile.radix)) throw invalid();
            exponent = exponentSymbol + sign + exponent;
        }
        String sign = "";
        if (value.startsWith("-") || value.startsWith("+")) {
            sign = value.substring(0, 1);
            value = value.substring(1);
        }
        int point = value.indexOf(profile.decimal);
        if (point >= 0 && value.indexOf(profile.decimal, point + 1) >= 0) throw invalid();
        String whole = point < 0 ? value : value.substring(0, point);
        String fraction = point < 0 ? "" : value.substring(point + 1);
        if (!digitsOrEmpty(fraction, profile.radix)) throw invalid();
        if (profile.grouping != 0 && whole.indexOf(profile.grouping) >= 0) {
            if (profile.primary <= 0 || profile.grouping == profile.decimal) throw invalid();
            int repeat = profile.secondary > 0 ? profile.secondary : profile.primary;
            String[] groups = whole.split(Pattern.quote(Character.toString(profile.grouping)), -1);
            if (groups.length < 2 || groups[0].isEmpty() || groups[0].length() > repeat)
                throw invalid();
            for (int i = 0; i < groups.length; i++) {
                if (!digits(groups[i], profile.radix)) throw invalid();
                if (i > 0
                        && groups[i].length()
                                != (i == groups.length - 1 ? profile.primary : repeat))
                    throw invalid();
            }
            whole = String.join("", groups);
        } else if (!digitsOrEmpty(whole, profile.radix)) throw invalid();
        if (whole.isEmpty() && fraction.isEmpty()) throw invalid();
        String canonical =
                sign
                        + (whole.isEmpty() ? "0" : whole)
                        + (point >= 0 ? "." + fraction : "")
                        + exponent;
        RpnCalculator.parse(canonical, profile.radix);
        return canonical;
    }

    private static boolean digits(String text, int radix) {
        return !text.isEmpty() && digitsOrEmpty(text, radix);
    }

    private static boolean digitsOrEmpty(String text, int radix) {
        for (int i = 0; i < text.length(); i++)
            if (Character.digit(text.charAt(i), radix) < 0) return false;
        return true;
    }

    private static boolean space(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private static String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && space(value.charAt(start))) start++;
        while (end > start && space(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Clipboard must contain one valid number");
    }
}
