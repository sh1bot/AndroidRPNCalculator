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
        int id;
        switch (code) {
            case NEED_VALUES:
                id = R.string.error_need_values;
                break;
            case ENTRY_TOO_LONG:
                id = R.string.error_entry_too_long;
                break;
            case ROW_MISSING:
                id = R.string.error_row_missing;
                break;
            case NUMBER_TOO_LARGE:
                id = R.string.error_number_too_large;
                break;
            case FACTORIAL_INTEGER:
                id = R.string.error_factorial_integer;
                break;
            case FACTORIAL_NONNEGATIVE:
                id = R.string.error_factorial_nonnegative;
                break;
            case FACTOR_INTEGER:
                id = R.string.error_factor_integer;
                break;
            case FACTOR_GREATER_THAN_ONE:
                id = R.string.error_factor_greater_than_one;
                break;
            case RATIONAL_REQUIRED:
                id = R.string.error_rational_required;
                break;
            case DIVIDE_BY_ZERO:
                id = R.string.error_divide_by_zero;
                break;
            case ZERO_ROOT_DEGREE:
                id = R.string.error_zero_root_degree;
                break;
            case EVEN_NEGATIVE_ROOT:
                id = R.string.error_even_negative_root;
                break;
            case AMBIGUOUS_NUMBER:
                id = R.string.error_ambiguous_number;
                break;
            case INVALID_NUMBER:
                id = R.string.error_invalid_number;
                break;
            case UNDEFINED_RESULT:
                id = R.string.error_undefined_result;
                break;
            case INTERNAL:
                id = R.string.error_internal;
                break;
            default:
                id = R.string.error_internal;
        }
        return context.getString(id, argument);
    }
}
