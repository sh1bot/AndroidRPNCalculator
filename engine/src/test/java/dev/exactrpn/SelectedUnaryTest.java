package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SelectedUnaryTest {
    @Test
    void atomicInPlaceUndoRedoAndReplay() throws Exception {
        TestCalculator c = new TestCalculator();
        for (String n : new String[] {"4", "9", "16"}) {
            CalculatorTestSupport.paste(c, n);
            c.key("ENTER");
        }
        c.apply(
                Command.unaryRows(Operation.find("√"), 0, 2),
                NumberText.COMPUTER,
                NumberText.COMPUTER);
        assertEquals("2", c.values().get(0).bigIntegerValue().toString());
        assertEquals("9", c.values().get(1).bigIntegerValue().toString());
        c = TestCalculator.load(c.save());
        c.arithmeticKey("UNDO");
        assertEquals("4", c.values().get(0).bigIntegerValue().toString());
        c.arithmeticKey("REDO");
        assertEquals("4", c.values().get(2).bigIntegerValue().toString());
    }

    @Test
    void failureOnLaterRowChangesNothing() throws Exception {
        TestCalculator c = new TestCalculator();
        CalculatorTestSupport.paste(c, "4");
        c.key("ENTER");
        CalculatorTestSupport.paste(c, "-1");
        c.key("ENTER");
        byte[] before = c.save();
        assertThrows(ArithmeticException.class, () -> c.transformRows(Operation.SQRT, 0, 1));
        assertArrayEquals(before, c.save());
        assertThrows(IllegalArgumentException.class, () -> c.transformRows(Operation.ADD, 0, 1));
        assertArrayEquals(before, c.save());
    }
}
