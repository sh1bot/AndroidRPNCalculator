// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Android localisation for the catalog's platform-independent text tokens. */
final class KeyResources {
    static int resource(CalculatorKey.Text text) {
        return switch (text) {
            case NONE -> 0;
            case BACKSPACE -> R.string.backspace;
            case CEIL_DESCRIPTION -> R.string.ceil_description;
            case CLEAR -> R.string.clear;
            case CLEAR_ENTRY -> R.string.clear_entry;
            case CLEAR_STACK -> R.string.clear_stack;
            case DECIMAL_SEPARATOR -> R.string.decimal_separator;
            case DROP_ROLL_DESCRIPTION -> R.string.drop_roll_description;
            case DUPLICATE -> R.string.duplicate;
            case E_DESCRIPTION -> R.string.e_description;
            case ENTER -> R.string.enter;
            case ENTER_DESCRIPTION -> R.string.enter_description;
            case EXP_DESCRIPTION -> R.string.exp_description;
            case FACTOR -> R.string.factor;
            case FACTOR_DESCRIPTION -> R.string.factor_description;
            case FACTORIAL_DESCRIPTION -> R.string.factorial_description;
            case FLOOR_DESCRIPTION -> R.string.floor_description;
            case INVERSE_OFF -> R.string.inverse_off;
            case LOG10_DESCRIPTION -> R.string.log10_description;
            case MODULO_DESCRIPTION -> R.string.modulo_description;
            case PHI_DESCRIPTION -> R.string.phi_description;
            case PI_DESCRIPTION -> R.string.pi_description;
            case POWER_DESCRIPTION -> R.string.power_description;
            case RECIPROCAL_DESCRIPTION -> R.string.reciprocal_description;
            case REMAINDER_DESCRIPTION -> R.string.remainder_description;
            case ROOT_DESCRIPTION -> R.string.root_description;
            case SWAP -> R.string.swap;
            case SWAP_DESCRIPTION -> R.string.swap_description;
            case TAU_TOGGLE_DESCRIPTION -> R.string.tau_toggle_description;
            case TEN_POWER_DESCRIPTION -> R.string.ten_power_description;
            case UNDO -> R.string.undo;
            case UNDO_DESCRIPTION -> R.string.undo_description;
        };
    }
}
