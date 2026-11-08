// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Modifier policy; actual bindings live in the shared key catalog. */
public final class KeyboardInput {
    private KeyboardInput() {}

    public static String command(int code, int unicode, boolean ctrl, boolean alt, boolean meta) {
        return alt || meta ? null : CalculatorKey.keyboard(code, unicode, ctrl);
    }
}
