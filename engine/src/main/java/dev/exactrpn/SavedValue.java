// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.BoundedRational;
import com.android.calculator2.UnifiedReal;

import java.math.BigInteger;

/** Exact expression nodes for persistence; never reconstruct a value from display text. */
final class SavedValue {
    final String op, literal;
    final SavedValue left, right;

    SavedValue(String op, String literal, SavedValue left, SavedValue right) {
        this.op = op;
        this.literal = literal;
        this.left = left;
        this.right = right;
    }

    static SavedValue of(UnifiedReal value, String op, SavedValue left, SavedValue right) {
        BoundedRational rational = value.boundedRationalValue();
        return rational != null && rational.toString().length() < 60000
                ? new SavedValue("r", rational.toString(), null, null)
                : new SavedValue(op, "", left, right);
    }

    UnifiedReal evaluate(UnifiedReal a, UnifiedReal b) {
        if (op.equals("r")) {
            String[] parts = literal.split("/", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid rational");
            BigInteger denominator = new BigInteger(parts[1]);
            if (denominator.signum() == 0) throw new IllegalArgumentException("Zero denominator");
            return new UnifiedReal(new BoundedRational(new BigInteger(parts[0]), denominator));
        }
        String[] parts = op.split(":", -1);
        if (parts.length > 2) throw new IllegalArgumentException("Unknown saved operation");
        Operation operation = Operation.find(parts[0]);
        if (operation == null) throw new IllegalArgumentException("Unknown saved operation");
        RpnCalculator.AngleUnit unit =
                parts.length == 2
                        ? RpnCalculator.AngleUnit.valueOf(parts[1])
                        : RpnCalculator.AngleUnit.RAD;
        return operation.evaluate(a, b, unit);
    }
}
