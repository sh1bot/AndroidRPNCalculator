// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import com.android.calculator2.UnifiedReal;

abstract class RegressionSupport {
    protected final ValueFormatter.Notation sci = ValueFormatter.Notation.SCIENTIFIC;
    protected final ValueFormatter.Notation eng = ValueFormatter.Notation.ENGINEERING;
    protected final UnifiedReal huge = RpnCalculator.parse("123456789012345678901234567890");
    protected final UnifiedReal third = UnifiedReal.ONE.divide(new UnifiedReal(3));
    protected final UnifiedReal rootTwo = UnifiedReal.TWO.sqrt();

    protected static String normalize(
            String text, char decimal, char grouping, int primary, int secondary) {
        return NumberText.analyze(
                        text, new NumberText.Profile(decimal, grouping, primary, secondary))
                .canonical();
    }

    static void keys(TestCalculator c, String... keys) {
        for (String k : keys) c.key(k);
    }

    static UnifiedReal top(TestCalculator c) {
        return c.values().get(c.values().size() - 1);
    }

    static void number(TestCalculator c, String n) {
        for (char ch : n.toCharArray()) c.key(String.valueOf(ch));
    }
}
