// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Entry and row selection are mutually exclusive; calculation progress is independent. */
public enum InteractionMode {
    STACK,
    ENTRY,
    SELECT;

    enum SelectionAction {
        NONE,
        CLEAR_SELECTION,
        SELECT_ALL,
        PICK,
        DROP,
        SUM,
        PRODUCT,
        MEAN,
        SD,
        COPY,
        IGNORE,
        UNARY,
        UNAVAILABLE
    }

    static SelectionAction selectionAction(String key) {
        return switch (key) {
            case Command.UNDO -> SelectionAction.CLEAR_SELECTION;
            case "SMART_CLEAR", "CLEAR" -> SelectionAction.SELECT_ALL;
            case Command.ENTER -> SelectionAction.PICK;
            case "BACK", "DROP", "DELETE" -> SelectionAction.DROP;
            case "+" -> SelectionAction.SUM;
            case "×" -> SelectionAction.PRODUCT;
            case "÷" -> SelectionAction.MEAN;
            case "−" -> SelectionAction.SD;
            case "SWAP" -> SelectionAction.COPY;
            case "HOLD_DROP", "ROLL_UP" -> SelectionAction.IGNORE;
            case "Factor" -> SelectionAction.UNAVAILABLE;
            default -> {
                Operation operation = Operation.find(key);
                yield operation == null
                        ? SelectionAction.NONE
                        : operation.arity == 1
                                ? SelectionAction.UNARY
                                : SelectionAction.UNAVAILABLE;
            }
        };
    }
}
