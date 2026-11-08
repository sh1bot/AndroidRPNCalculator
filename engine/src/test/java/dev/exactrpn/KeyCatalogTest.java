// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class KeyCatalogTest {
    @Test
    void catalogIdentitiesAndArithmeticImplementationsAgree() {
        for (CalculatorKey key : CalculatorKey.values())
            assertSame(key, CalculatorKey.find(key.wire));
        for (Operation operation : Operation.values()) {
            CalculatorKey key = CalculatorKey.valueOf(operation.name());
            assertEquals(key.wire, operation.key);
            if (key.inverse() != null)
                assertEquals(operation.arity, Operation.find(key.inverse().wire).arity);
        }
    }
}
