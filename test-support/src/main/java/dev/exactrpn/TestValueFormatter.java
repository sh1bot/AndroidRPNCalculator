// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

/** Evaluates fixtures before exercising the caller-side formatter. */
final class TestValueFormatter {
    static String format(UnifiedReal value, boolean newest, ValueFormatter.Notation notation) {
        return ValueFormatter.format(
                NumericEvaluator.read(value, NumericRequest.DEFAULT), newest, notation);
    }

    static String format(
            UnifiedReal value,
            boolean newest,
            ValueFormatter.Notation notation,
            FractionMode mode) {
        return ValueFormatter.format(
                NumericEvaluator.read(value, NumericRequest.DEFAULT), newest, notation, mode);
    }

    static boolean approximate(
            UnifiedReal value, boolean newest, ValueFormatter.Notation notation) {
        return approximate(value, newest, notation, FractionMode.DECIMAL);
    }

    static boolean approximate(
            UnifiedReal value,
            boolean newest,
            ValueFormatter.Notation notation,
            FractionMode mode) {
        return ValueFormatter.presentation(
                        NumericEvaluator.read(value, NumericRequest.DEFAULT),
                        newest,
                        notation,
                        mode)
                .approximate;
    }

    static String clipboard(UnifiedReal value, ValueFormatter.Notation notation) {
        return clipboard(value, notation, FractionMode.DECIMAL);
    }

    static String clipboard(
            UnifiedReal value, ValueFormatter.Notation notation, FractionMode mode) {
        return ValueFormatter.clipboard(
                NumericEvaluator.read(value, NumericRequest.PRECISE), notation, mode);
    }
}
