// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;

/** Translates stable engine failures without parsing English exception messages. */
final class ErrorMessages {
    static String describe(Context context, Throwable failure) {
        if (failure instanceof RemoteCalculator.UserFailure) return failure.getMessage();
        if (failure instanceof CalculationError) {
            CalculationError error = (CalculationError) failure;
            return fromCode(context, error.code(), error.argument());
        }
        if (failure instanceof NumberFormatException)
            return context.getString(R.string.error_invalid_number);
        if (failure instanceof ArithmeticException)
            return context.getString(R.string.error_undefined_result);
        if (failure instanceof RemoteCalculator.InvalidCheckpoint)
            return context.getString(R.string.restore_failed);
        android.util.Log.e("ExactRPN", "Calculation failed", failure);
        return context.getString(R.string.error_internal);
    }

    static String fromCode(Context context, CalculationError.Code code, int argument) {
        int id =
                switch (code) {
                    case NEED_VALUES -> R.string.error_need_values;
                    case ENTRY_TOO_LONG -> R.string.error_entry_too_long;
                    case TOO_MANY_VALUES -> R.string.error_too_many_values;
                    case ROW_MISSING -> R.string.error_row_missing;
                    case NUMBER_TOO_LARGE -> R.string.error_number_too_large;
                    case FACTORIAL_INTEGER -> R.string.error_factorial_integer;
                    case FACTORIAL_NONNEGATIVE -> R.string.error_factorial_nonnegative;
                    case FACTOR_INTEGER -> R.string.error_factor_integer;
                    case FACTOR_GREATER_THAN_ONE -> R.string.error_factor_greater_than_one;
                    case PROBABLY_PRIME -> R.string.error_probably_prime;
                    case DEFINITELY_COMPOSITE -> R.string.error_definitely_composite;
                    case RATIONAL_REQUIRED -> R.string.error_rational_required;
                    case DIVIDE_BY_ZERO -> R.string.error_divide_by_zero;
                    case ZERO_ROOT_DEGREE -> R.string.error_zero_root_degree;
                    case EVEN_NEGATIVE_ROOT -> R.string.error_even_negative_root;
                    case AMBIGUOUS_NUMBER -> R.string.error_ambiguous_number;
                    case INVALID_PASTE_LINE -> R.string.error_invalid_paste_line;
                    case INVALID_NUMBER -> R.string.error_invalid_number;
                    case UNDEFINED_RESULT -> R.string.error_undefined_result;
                    case TRANSFER_TOO_LARGE -> R.string.error_transfer_too_large;
                    case INTERNAL -> R.string.error_internal;
                    default -> R.string.error_internal;
                };
        return context.getString(id, argument);
    }
}
