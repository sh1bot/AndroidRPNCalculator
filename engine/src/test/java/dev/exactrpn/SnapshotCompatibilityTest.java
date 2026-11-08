// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Base64;

final class SnapshotCompatibilityTest {
    // Captured from the previously shipped writer; never regenerate with the current codec.
    private static final String[] LEGACY = {
        "UlBOMQADUkFEAC4ALAAAAAMAAAADAAAAAAAAAAEAAAAAAAMxMjMBAAAAAAMAAAADAC4ALAAAAAMAAAAD",
        "UlBOMgADUkFEAC4ALAAAAAMAAAADAAAAAQABcgAENDIvMf//////////AAAAAgAAAAAAAAADAAAAAQAAAAAAAAAAAAAAAAAAAAAALgAsAAAAAwAAAAMAAAAAAAI0MgEAAAAAAgAAAAIALgAsAAAAAwAAAAMAAAAAAAAAAAAAAAAAAAAAAC4ALAAAAAMAAAAD",
        "UlBOMwAAAJBSUE4yAANSQUQALgAsAAAAAwAAAAMAAAABAAFyAAQ0Mi8x//////////8AAAACAAAAAAAAAAMAAAABAAAAAAAAAAAAAAAAAAAAAAAuACwAAAADAAAAAwAAAAAAAjQyAQAAAAACAAAAAgAuACwAAAADAAAAAwAAAAAAAAAAAAAAAAAAAAAALgAsAAAAAwAAAAMAAAB8UlBOMgADUkFEAC4ALAAAAAMAAAADAAAAAAAAAAEAAAABAAAAAwAAAAAAATEBAAAAAAEAAAABAC4ALAAAAAMAAAADAAAAAAAAAAAAAAAAAAAAAAAuACwAAAADAAAAAwAAAAAAAjE3AQAAAAACAAAAAgAuACwAAAADAAAAAw==",
        "UlBONAAAAJBSUE4yAANSQUQALgAsAAAAAwAAAAMAAAABAAFyAAQ0Mi8x//////////8AAAACAAAAAAAAAAMAAAABAAAAAAAAAAAAAAAAAAAAAAAuACwAAAADAAAAAwAAAAAAAjQyAQAAAAACAAAAAgAuACwAAAADAAAAAwAAAAAAAAAAAAAAAAAAAAAALgAsAAAAAwAAAAMAAAB8UlBOMgADUkFEAC4ALAAAAAMAAAADAAAAAAAAAAEAAAABAAAAAwAAAAAAATEBAAAAAAEAAAABAC4ALAAAAAMAAAADAAAAAAAAAAAAAAAAAAAAAAAuACwAAAADAAAAAwAAAAAAAjE3AQAAAAACAAAAAgAuACwAAAADAAAAAwE="
    };

    @Test
    void shippedFormatsMigrateWithoutLosingValuesOrEntryHistory() throws Exception {
        for (int i = 0; i < LEGACY.length; i++) {
            RpnCalculator calculator = RpnCalculator.load(Base64.getDecoder().decode(LEGACY[i]));
            assertEquals(i == 0 ? "123" : i == 1 ? "" : "1", calculator.entry());
            if (i != 0) assertEquals("42", calculator.values().get(0).bigIntegerValue().toString());
            RpnCalculator roundTrip = RpnCalculator.load(calculator.save());
            assertEquals(calculator.entry(), roundTrip.entry());
            if (i >= 2) {
                EntryEditor editor = roundTrip.detachEntryEditor();
                editor.key("REDO");
                assertEquals("17", editor.entry());
                editor.key("UNDO");
                assertEquals("1", editor.entry());
            }
        }
    }

    @Test
    void ordinaryEngineSnapshotsExposeTheirEditHistoryToTheLocalEditor() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("1");
        calculator.key("2");
        calculator.key("3");
        EntryEditor editor = RpnCalculator.load(calculator.save()).detachEntryEditor();
        editor.key("UNDO");
        assertEquals("12", editor.entry());
        editor.key("REDO");
        assertEquals("123", editor.entry());
    }

    @Test
    void replacingAnEntryDoesNotNestOrGrowTheArithmeticSection() throws Exception {
        RpnCalculator calculator = new RpnCalculator();
        calculator.key("π");
        byte[] first = calculator.save();
        EntryEditor entry = new EntryEditor();
        entry.key("7");
        byte[] second = RpnCalculator.saveWithEntry(first, entry);
        byte[] third = RpnCalculator.saveWithEntry(second, entry);
        assertArrayEquals(second, third);
        assertEquals("7", RpnCalculator.load(third).entry());
        assertEquals(calculator.values().size(), RpnCalculator.load(third).values().size());
    }
}
