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
                    BigInteger[] parts =
                            ExactFunctions.parts(
                                    RpnCalculator.parse(
                                            NumberText.analyze(line, locale).canonical(),
                                            locale.radix));
                    values.add(new RationalValue(parts[0], parts[1]));
                } else {
                    var match = FRACTION.matcher(line);
                    if (!match.matches())
                        throw CalculationError.invalid(CalculationError.Code.INVALID_NUMBER);
                    BigInteger whole = integer(match.group(1), locale),
                            denominator = integer(match.group(3), locale);
                    BigInteger numerator =
                            match.group(2) == null
                                    ? whole
                                    : whole.abs()
                                            .multiply(denominator)
                                            .add(integer(match.group(2), locale))
                                            .multiply(
                                                    BigInteger.valueOf(
                                                            line.startsWith("-") ? -1 : 1));
                    values.add(new RationalValue(numerator, denominator));
                }
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw CalculationError.invalid(CalculationError.Code.INVALID_PASTE_LINE, i + 1);
            }
        }
        if (values.size() < 2) throw new IllegalArgumentException("Expected multiple values");
        return values.toArray(new RationalValue[0]);
    }

    private static BigInteger integer(String text, NumberText.Profile profile) {
        NumberText.Profile plain =
                new NumberText.Profile(profile.decimal, (char) 0, 0, 0, profile.radix);
        return RpnCalculator.parse(NumberText.selected(text, plain), profile.radix)
                .bigIntegerValue();
    }
}
