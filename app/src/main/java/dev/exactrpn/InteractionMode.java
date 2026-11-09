// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Entry and row selection are mutually exclusive; calculation progress is independent. */
public enum InteractionMode {
    STACK,
    ENTRY,
    SELECT
}
