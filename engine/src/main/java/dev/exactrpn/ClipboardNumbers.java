// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Caller-side clipboard parsing, completed before any arithmetic request is sent. */
public final class ClipboardNumbers {
    private ClipboardNumbers() {}

    private static final java.util.regex.Pattern FRACTION =
            java.util.regex.Pattern.compile("([+-]?[0-9]+)(?: ([0-9]+))?/([0-9]+)");

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
                                            NumberText.analyze(line, locale).canonical()));
                    values.add(new RationalValue(parts[0], parts[1]));
                } else {
                    var match = FRACTION.matcher(line);
                    if (!match.matches())
                        throw CalculationError.invalid(CalculationError.Code.INVALID_NUMBER);
                    BigInteger whole = RpnCalculator.parse(match.group(1)).bigIntegerValue(),
                            denominator = RpnCalculator.parse(match.group(3)).bigIntegerValue();
                    BigInteger numerator =
                            match.group(2) == null
                                    ? whole
                                    : whole.abs()
                                            .multiply(denominator)
                                            .add(
                                                    RpnCalculator.parse(match.group(2))
                                                            .bigIntegerValue())
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
}
