// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Mathematical/protocol failures, without a language or Android resources. */
public final class ErrorCodes {
    private ErrorCodes() {}

    public static CalculationError.Code code(Throwable error) {
        if (error instanceof CalculationError) return ((CalculationError) error).code();
        if (error instanceof NumberFormatException) return CalculationError.Code.INVALID_NUMBER;
        if (error instanceof ArithmeticException) return CalculationError.Code.UNDEFINED_RESULT;
        return CalculationError.Code.INTERNAL;
    }

    public static int argument(Throwable error) {
        return error instanceof CalculationError ? ((CalculationError) error).argument() : 0;
    }
}
