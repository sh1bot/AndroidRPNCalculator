// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import com.android.calculator2.UnifiedReal;

import java.util.HashMap;
import java.util.Map;

/** One definition of arithmetic, shared by live execution and saved expression replay. */
public enum Operation {
    PI("π", 0),
    TAU("τ", 0),
    E("e", 0),
    PHI("φ", 0),
    ADD("+", 2),
    SUBTRACT("−", 2),
    MULTIPLY("×", 2),
    DIVIDE("÷", 2),
    POWER("xʸ", 2),
    ROOT("root", 2),
    REMAINDER("rem", 2),
    MODULO("mod", 2),
    NEGATE("±", 1),
    SQRT("√", 1),
    SQUARE("x²", 1),
    FACTORIAL("!", 1),
    RECIPROCAL("1/x", 1),
    FLOOR("floor", 1),
    CEIL("ceil", 1),
    LOG10("log10", 1),
    TEN_POWER("10ˣ", 1),
    LN("ln", 1),
    EXP("eˣ", 1),
    SIN("sin", 1, true),
    COS("cos", 1, true),
    TAN("tan", 1, true),
    ASIN("asin", 1, true),
    ACOS("acos", 1, true),
    ATAN("atan", 1, true);

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

    Operation(String key, int arity) {
        this(key, arity, false);
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
