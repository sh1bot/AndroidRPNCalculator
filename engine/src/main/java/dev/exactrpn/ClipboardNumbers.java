// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Caller-side clipboard parsing, completed before any arithmetic request is sent. */
public final class ClipboardNumbers {
    private ClipboardNumbers() {}

    private static final java.util.regex.Pattern FRACTION =
            java.util.regex.Pattern.compile("(.+?)(?: ([^ ]+))?/([^/ ]+)");

    public static RationalValue[] parse(String text, NumberText.Profile locale) {
        if (text.length() > RpnCalculator.MAX_PASTE_LENGTH)
            throw CalculationError.invalid(
                    CalculationError.Code.ENTRY_TOO_LONG, RpnCalculator.MAX_PASTE_LENGTH);
        List<RationalValue> values = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            try {
                EntryEditor.checkLength(line);
                if (!line.contains("/")) {
                    values.add(
                            ExactFunctions.rational(
                                    RpnCalculator.parse(
                                            NumberText.analyze(line, locale).canonical(),
                                            locale.radix)));
                } else {
                    var match = FRACTION.matcher(line);
                    if (!match.matches())
                        throw CalculationError.invalid(CalculationError.Code.INVALID_NUMBER);
                    String wholeText = canonical(match.group(1), locale);
                    BigInteger whole = integerValue(wholeText, locale),
                            denominator = unsignedInteger(match.group(3), locale);
                    BigInteger numerator =
                            match.group(2) == null
                                    ? whole
                                    : whole.abs()
                                            .multiply(denominator)
                                            .add(unsignedInteger(match.group(2), locale))
                                            .multiply(
                                                    BigInteger.valueOf(
                                                            wholeText.startsWith("-") ? -1 : 1));
                    values.add(new RationalValue(numerator, denominator));
                }
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw CalculationError.invalid(CalculationError.Code.INVALID_PASTE_LINE, i + 1);
            }
            if (values.size() > RpnCalculator.MAX_PASTE_VALUES)
                throw CalculationError.invalid(
                        CalculationError.Code.TOO_MANY_VALUES, RpnCalculator.MAX_PASTE_VALUES);
        }
        if (values.size() < 2) throw new IllegalArgumentException("Expected multiple values");
        return values.toArray(new RationalValue[0]);
    }

    private static BigInteger unsignedInteger(String text, NumberText.Profile profile) {
        String canonical = canonical(text, profile);
        if (canonical.startsWith("-") || canonical.startsWith("+"))
            throw new IllegalArgumentException("Fraction part must be unsigned");
        return integerValue(canonical, profile);
    }

    private static BigInteger integerValue(String canonical, NumberText.Profile profile) {
        BigInteger value = RpnCalculator.parse(canonical, profile.radix).bigIntegerValue();
        if (value == null) throw new IllegalArgumentException("Fraction part is not an integer");
        return value;
    }

    private static String canonical(String text, NumberText.Profile profile) {
        NumberText.Profile plain =
                new NumberText.Profile(profile.decimal, (char) 0, 0, 0, profile.radix);
        return NumberText.selected(text, plain);
    }
}
