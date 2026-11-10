// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.android.calculator2.UnifiedReal;

import org.junit.jupiter.api.Test;

final class RadixModeTest {
    @Test
    void parsesDigitsFractionsAndRadixExponentsExactly() {
        exact("255", "FF", 16);
        exact("0.5", "0.8", 16);
        exact("0.5", "0.6", 12);
        exact("256", "1P2", 16);
        exact("144", "1P2", 12);
    }

    @Test
    void formatsExactValuesInTheRequestedAlphabet() {
        NumericValue hex =
                NumericEvaluator.read(
                        RpnCalculator.parse("255"), NumericRequest.display(RadixMode.HEX));
        assertEquals("FF", ValueFormatter.format(hex, true, ValueFormatter.Notation.SCIENTIFIC));

        UnifiedReal half = RpnCalculator.parse("0.5");
        NumericValue dozenal =
                NumericEvaluator.read(half, NumericRequest.precise(RadixMode.DOZENAL));
        assertEquals("0.6", ValueFormatter.clipboard(dozenal, ValueFormatter.Notation.SCIENTIFIC));
        assertEquals("↊↋", RadixMode.DOZENAL.digits("AB"));
    }

    @Test
    void editorPersistsItsRadixAndRejectsDigitsOutsideIt() throws Exception {
        assertFalse(EntryEditor.isEntryKey("e"), "Euler's constant remains a command");
        assertTrue(EntryEditor.isEntryKey("E"), "uppercase E is a hexadecimal digit");

        EntryEditor editor = new EntryEditor();
        editor.setInputProfile(new NumberText.Profile('.', '\u2009', 4, 4, 16));
        editor.key("F");
        editor.key(".");
        editor.key("8");
        EntryEditor restored = EntryEditor.load(editor.save());
        assertEquals(16, restored.inputProfile.radix);
        assertEquals("F.8", restored.entry());
        assertFalse(restored.rawEntry());

        restored.setInputProfile(new NumberText.Profile('.', '\u2009', 3, 3, 12));
        restored.key("F");
        assertEquals("F.8", restored.entry(), "invalid dozenal digit is ignored");
    }

    @Test
    void clipboardParsingUsesTheSelectedRadix() {
        NumberText.Profile hex = new NumberText.Profile('.', '\u2009', 4, 4, 16);
        RationalValue[] values = ClipboardNumbers.parse("FF\n1/2", hex);
        assertEquals(new java.math.BigInteger("255"), values[0].numerator);
        assertEquals(new java.math.BigInteger("1"), values[1].numerator);
        assertEquals(new java.math.BigInteger("2"), values[1].denominator);
    }

    private static void exact(String decimal, String text, int radix) {
        assertEquals(
                java.util.Arrays.toString(ExactFunctions.parts(RpnCalculator.parse(decimal))),
                java.util.Arrays.toString(ExactFunctions.parts(RpnCalculator.parse(text, radix))));
    }
}
