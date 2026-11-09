// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.ArrayList;
import java.util.List;

/** Shared JVM/device fixtures; never included in the application. */
final class CalculatorTestSupport {
    static TestCalculator copy(TestCalculator calculator) {
        try {
            return TestCalculator.load(calculator.save());
        } catch (java.io.IOException ex) {
            throw new AssertionError("Could not restore test fixture", ex);
        }
    }

    static void paste(TestCalculator calculator, String canonical) {
        RpnCalculator.parse(canonical);
        calculator.editText(
                canonical,
                canonical.length(),
                canonical.length(),
                false,
                false,
                NumberText.COMPUTER);
    }

    static void paste(RpnCalculator calculator, String canonical) {
        EntryEditor draft = new EntryEditor();
        draft.editText(
                canonical,
                canonical.length(),
                canonical.length(),
                false,
                false,
                NumberText.COMPUTER);
        calculator.useEntryEditor(draft.entryForCommit());
    }

    static List<String> formattedValues(
            TestCalculator calculator, ValueFormatter.Notation notation, FractionMode fractions) {
        List<UnifiedReal> values = calculator.values();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++)
            result.add(
                    ValueFormatter.format(
                            values.get(i),
                            !calculator.isEditing() && i == values.size() - 1,
                            notation,
                            fractions));
        return result;
    }
}
