// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class RedoBoundaryTest {
    @Test
    void emptyStackNoOpsDoNotSkipThePendingEntryRedo() {
        for (String noOp : new String[] {"BACK", "DELETE", "CLEAR"}) {
            TestCalculator c = new TestCalculator();
            for (String key : new String[] {"1", "ENTER", "UNDO", "UNDO", noOp, "REDO"}) c.key(key);
            assertTrue(c.isEditing(), "Pending entry redo lost after " + noOp);
            assertEquals("1", c.entry());
            assertTrue(c.values().isEmpty());
            c.key("REDO");
            assertFalse(c.isEditing());
            assertEquals("1", c.values().get(0).bigIntegerValue().toString());
        }
    }

    @Test
    void redoRestoresEveryUndoneEntryAndOperationInOrder() throws Exception {
        for (boolean restart : new boolean[] {false, true}) {
            TestCalculator c = new TestCalculator();
            for (String key :
                    new String[] {"1", "2", "ENTER", "3", "4", "5", "+", "UNDO", "UNDO", "UNDO"})
                c.key(key);
            assertEquals("12", c.entry());
            assertTrue(c.values().isEmpty());
            if (restart) c = TestCalculator.load(c.save());
            c.key("REDO");
            assertFalse(c.isEditing());
            assertEquals("12", c.values().get(0).bigIntegerValue().toString());
            if (restart) c = TestCalculator.load(c.save());
            c.key("REDO");
            assertTrue(
                    c.isEditing(),
                    "Redo must restore the undone entry before applying its operator");
            assertEquals("345", c.entry());
            assertEquals(1, c.values().size());
            c.key("REDO");
            assertFalse(c.isEditing());
            assertEquals("357", c.values().get(0).bigIntegerValue().toString());
        }
    }
}
