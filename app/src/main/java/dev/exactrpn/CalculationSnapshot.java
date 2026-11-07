// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Bundle;

/** Immutable accepted state; independent of the arithmetic process and its next operation. */
final class CalculationSnapshot {
    final String id;
    final byte[] bytes;
    final boolean empty;

    CalculationSnapshot(String id, byte[] bytes, boolean empty) {
        this.id = id;
        this.bytes = bytes;
        this.empty = empty;
    }

    void input(Bundle request) {
        request.putString("inputId", id);
        request.putByteArray("input", bytes);
    }
}
