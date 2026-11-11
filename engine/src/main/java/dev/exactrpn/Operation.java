// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.HashMap;
import java.util.Map;

/** One definition of arithmetic, shared by live execution and saved expression replay. */
public enum Operation {
    PI(CalculatorKey.PI, 0),
    TAU(CalculatorKey.TAU, 0),
    E(CalculatorKey.E, 0),
    PHI(CalculatorKey.PHI, 0),
    ADD(CalculatorKey.ADD, 2),
    SUBTRACT(CalculatorKey.SUBTRACT, 2),
    MULTIPLY(CalculatorKey.MULTIPLY, 2),
    DIVIDE(CalculatorKey.DIVIDE, 2),
    POWER(CalculatorKey.POWER, 2),
    ROOT(CalculatorKey.ROOT, 2),
    REMAINDER(CalculatorKey.REMAINDER, 2),
    MODULO(CalculatorKey.MODULO, 2),
    NEGATE(CalculatorKey.NEGATE, 1),
    SQRT(CalculatorKey.SQRT, 1),
    SQUARE(CalculatorKey.SQUARE, 1),
    FACTORIAL(CalculatorKey.FACTORIAL, 1),
    RECIPROCAL(CalculatorKey.RECIPROCAL, 1),
    FLOOR(CalculatorKey.FLOOR, 1),
    CEIL(CalculatorKey.CEIL, 1),
    LOG10(CalculatorKey.LOG10, 1),
    TEN_POWER(CalculatorKey.TEN_POWER, 1),
    LOG12(CalculatorKey.LOG12, 1),
    TWELVE_POWER(CalculatorKey.TWELVE_POWER, 1),
    LOG16(CalculatorKey.LOG16, 1),
    SIXTEEN_POWER(CalculatorKey.SIXTEEN_POWER, 1),
    LN(CalculatorKey.LN, 1),
    EXP(CalculatorKey.EXP, 1),
    SIN(CalculatorKey.SIN, 1, true),
    COS(CalculatorKey.COS, 1, true),
    TAN(CalculatorKey.TAN, 1, true),
    ASIN(CalculatorKey.ASIN, 1, true),
    ACOS(CalculatorKey.ACOS, 1, true),
    ATAN(CalculatorKey.ATAN, 1, true);

    public final String key;
    public final int arity;
    private final boolean angle;
    private static final UnifiedReal DEGREES = new UnifiedReal(180),
            GRADIANS = new UnifiedReal(200);
    private static final Map<String, Operation> OPERATIONS = new HashMap<>();

    static {
        for (Operation operation : values()) OPERATIONS.put(operation.key, operation);
        OPERATIONS.put("exp", EXP); // Historical saved expressions.
    }

    Operation(CalculatorKey key, int arity) {
        this(key, arity, false);
    }

    Operation(CalculatorKey key, int arity, boolean angle) {
        this(key.wire, arity, angle);
    }

    Operation(String key, int arity, boolean angle) {
        this.key = key;
        this.arity = arity;
        this.angle = angle;
    }

    public static Operation find(String key) {
        return OPERATIONS.get(key);
    }

    public String savedName(RpnCalculator.AngleUnit unit) {
        return angle ? key + ":" + unit.name() : key;
    }

    private static UnifiedReal angleDivisor(RpnCalculator.AngleUnit unit) {
        return unit == RpnCalculator.AngleUnit.DEG ? DEGREES : GRADIANS;
    }

    private static UnifiedReal radians(UnifiedReal value, RpnCalculator.AngleUnit unit) {
        return unit == RpnCalculator.AngleUnit.RAD
                ? value
                : value.multiply(UnifiedReal.PI).divide(angleDivisor(unit));
    }

    private static UnifiedReal fromRadians(UnifiedReal value, RpnCalculator.AngleUnit unit) {
        return unit == RpnCalculator.AngleUnit.RAD
                ? value
                : value.divide(UnifiedReal.PI).multiply(angleDivisor(unit));
    }

    public UnifiedReal evaluate(UnifiedReal a, UnifiedReal b, RpnCalculator.AngleUnit unit) {
        if (arity > 0 && a == null || arity > 1 && b == null)
            throw new IllegalArgumentException("Missing operation operand");
        switch (this) {
            case PI:
                return UnifiedReal.PI;
            case TAU:
                return UnifiedReal.PI.multiply(UnifiedReal.TWO);
            case E:
                return UnifiedReal.E;
            case PHI:
                return UnifiedReal.ONE.add(new UnifiedReal(5).sqrt()).divide(UnifiedReal.TWO);
            case FLOOR:
                return ExactFunctions.floor(a);
            case CEIL:
                return ExactFunctions.floor(a.negate()).negate();
            case LOG10:
                return a.ln().divide(new UnifiedReal(10).ln());
            case TEN_POWER:
                return new UnifiedReal(10).pow(a);
            case LOG12:
                return a.ln().divide(new UnifiedReal(12).ln());
            case TWELVE_POWER:
                return new UnifiedReal(12).pow(a);
            case LOG16:
                return a.ln().divide(new UnifiedReal(16).ln());
            case SIXTEEN_POWER:
                return new UnifiedReal(16).pow(a);
            case ADD:
                return a.add(b);
            case SUBTRACT:
                return a.subtract(b);
            case MULTIPLY:
                return a.multiply(b);
            case DIVIDE:
                if (b.definitelyZero())
                    throw CalculationError.arithmetic(CalculationError.Code.DIVIDE_BY_ZERO);
                return a.divide(b);
            case POWER:
                return a.pow(b);
            case ROOT:
                return ExactFunctions.root(a, b);
            case REMAINDER:
            case MODULO:
                return ExactFunctions.remainder(a, b, this == MODULO);
            case NEGATE:
                return a.negate();
            case SQRT:
                return a.sqrt();
            case SQUARE:
                return a.multiply(a);
            case FACTORIAL:
                if (ExactFunctions.integer(a, "Factorial").signum() < 0)
                    throw CalculationError.invalid(CalculationError.Code.FACTORIAL_NONNEGATIVE);
                return a.fact();
            case RECIPROCAL:
                if (a.definitelyZero())
                    throw CalculationError.arithmetic(CalculationError.Code.DIVIDE_BY_ZERO);
                return a.inverse();
            case LN:
                return a.ln();
            case EXP:
                return a.exp();
            case SIN:
                return radians(a, unit).sin();
            case COS:
                return radians(a, unit).cos();
            case TAN:
                return radians(a, unit).tan();
            case ASIN:
                return fromRadians(a.asin(), unit);
            case ACOS:
                return fromRadians(a.acos(), unit);
            case ATAN:
                return fromRadians(a.atan(), unit);
            default:
                throw new AssertionError(this);
        }
    }
}
