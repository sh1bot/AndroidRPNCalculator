// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Immutable accepted state; independent of the arithmetic process and its next operation. */
final class CalculationSnapshot {
    final String id;
    final byte[] bytes;

    CalculationSnapshot(String id, byte[] bytes) {
        this.id = id;
        this.bytes = bytes;
    }

    void input(CalculationRequest request) {
        request.inputId = id;
        request.input = bytes;
    }
}
