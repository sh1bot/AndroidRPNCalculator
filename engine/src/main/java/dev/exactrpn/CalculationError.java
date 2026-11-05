// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Locale-neutral failures. Android maps codes and arguments to translated format strings. */
public interface CalculationError {
    enum Code {
        NEED_VALUES,
        ENTRY_TOO_LONG,
        ROW_MISSING,
        NUMBER_TOO_LARGE,
        FACTORIAL_INTEGER,
        FACTORIAL_NONNEGATIVE,
        FACTOR_INTEGER,
        FACTOR_GREATER_THAN_ONE,
        RATIONAL_REQUIRED,
        DIVIDE_BY_ZERO,
        ZERO_ROOT_DEGREE,
        EVEN_NEGATIVE_ROOT,
        AMBIGUOUS_NUMBER,
        INVALID_NUMBER,
        UNDEFINED_RESULT,
        INTERNAL
    }

    Code code();

    int argument();

    static IllegalArgumentException invalid(Code code) {
        return invalid(code, 0);
    }

    static IllegalArgumentException invalid(Code code, int argument) {
        return new Invalid(code, argument);
    }

    static ArithmeticException arithmetic(Code code) {
        return new Arithmetic(code);
    }

    final class Invalid extends IllegalArgumentException implements CalculationError {
        private final Code code;
        private final int argument;

        Invalid(Code code, int argument) {
            super(code.name());
            this.code = code;
            this.argument = argument;
        }

        public Code code() {
            return code;
        }

        public int argument() {
            return argument;
        }
    }

    final class Arithmetic extends ArithmeticException implements CalculationError {
        private final Code code;

        Arithmetic(Code code) {
            super(code.name());
            this.code = code;
        }

        public Code code() {
            return code;
        }

        public int argument() {
            return 0;
        }
    }
}
