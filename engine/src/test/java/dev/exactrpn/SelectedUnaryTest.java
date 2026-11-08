package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SelectedUnaryTest {
    @Test
    void atomicInPlaceUndoRedoAndReplay() throws Exception {
        RpnCalculator c = new RpnCalculator();
        for (String n : new String[] {"4", "9", "16"}) {
            c.paste(n);
            c.key("ENTER");
        }
        Command.parse("UNARY_ROWS:√:0,2").apply(c, NumberText.COMPUTER, NumberText.COMPUTER);
        assertEquals("2", c.values().get(0).bigIntegerValue().toString());
        assertEquals("9", c.values().get(1).bigIntegerValue().toString());
        c = RpnCalculator.load(c.save());
        c.key("UNDO");
        assertEquals("4", c.values().get(0).bigIntegerValue().toString());
        c.key("REDO");
        assertEquals("4", c.values().get(2).bigIntegerValue().toString());
    }

    @Test
    void failureOnLaterRowChangesNothing() throws Exception {
        RpnCalculator c = new RpnCalculator();
        c.paste("4");
        c.key("ENTER");
        c.paste("-1");
        c.key("ENTER");
        byte[] before = c.save();
        assertThrows(ArithmeticException.class, () -> c.transformRows(Operation.SQRT, 0, 1));
        assertArrayEquals(before, c.save());
        assertThrows(IllegalArgumentException.class, () -> c.transformRows(Operation.ADD, 0, 1));
        assertArrayEquals(before, c.save());
    }
}
