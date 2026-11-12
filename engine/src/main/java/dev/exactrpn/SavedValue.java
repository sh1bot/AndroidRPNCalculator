// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;

/** Typed exact expression nodes for persistence. */
final class SavedValue {
    final Operation operation;
    final RpnCalculator.AngleUnit angle;
    final RationalValue rational;
    final SavedValue left, right;

    private SavedValue(
            Operation operation,
            RpnCalculator.AngleUnit angle,
            RationalValue rational,
            SavedValue left,
            SavedValue right) {
        this.operation = operation;
        this.angle = angle;
        this.rational = rational;
        this.left = left;
        this.right = right;
    }

    static SavedValue rational(RationalValue value) {
        return new SavedValue(null, null, value, null, null);
    }

    static SavedValue rational(UnifiedReal value) {
        BoundedRational rational = value.boundedRationalValue();
        if (rational == null) throw new IllegalArgumentException("Value is not rational");
        return rational(new RationalValue(rational.numerator(), rational.denominator()));
    }

    static SavedValue operation(
            Operation operation,
            RpnCalculator.AngleUnit angle,
            SavedValue left,
            SavedValue right) {
        return new SavedValue(
                java.util.Objects.requireNonNull(operation),
                java.util.Objects.requireNonNull(angle),
                null,
                left,
                right);
    }

    static SavedValue of(
            UnifiedReal value,
            Operation operation,
            RpnCalculator.AngleUnit angle,
            SavedValue left,
            SavedValue right) {
        BoundedRational bounded = value.boundedRationalValue();
        if (bounded != null) {
            RationalValue rational =
                    new RationalValue(bounded.numerator(), bounded.denominator());
            if (rational.byteSize() < 60000) return rational(rational);
        }
        if (operation == null) throw new IllegalArgumentException("Missing saved operation");
        return operation(operation, angle, left, right);
    }

    UnifiedReal evaluate(UnifiedReal a, UnifiedReal b) {
        if (rational != null)
            return new UnifiedReal(
                    new BoundedRational(rational.numerator, rational.denominator));
        return operation.evaluate(a, b, angle);
    }
}
