// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.HashMap;
import java.util.Map;

/** Key identity and presentation metadata shared by arithmetic, hardware input and Android. */
public enum CalculatorKey {
    PI("π", null, 24, Text.PI_DESCRIPTION, null, "p", 0),
    TAU("τ", null, 24, Text.TAU_TOGGLE_DESCRIPTION, null, "", 0),
    E("e", null, 24, Text.E_DESCRIPTION, null, "e", 0),
    PHI("φ", null, 24, Text.PHI_DESCRIPTION, null, "", 0),
    ADD("+", null, 26, Text.NONE, null, "+", 0, 157),
    SUBTRACT("−", null, 26, Text.NONE, null, "-−", 0, 156),
    MULTIPLY("×", null, 26, Text.NONE, null, "*×", 0, 155),
    DIVIDE("÷", null, 26, Text.NONE, null, "/÷", 0, 154),
    POWER("xʸ", "yˣ", 22, Text.POWER_DESCRIPTION, "ROOT", "^", 0),
    ROOT("root", "ˣ√y", 22, Text.ROOT_DESCRIPTION, null, "", 0),
    REMAINDER("rem", null, 20, Text.REMAINDER_DESCRIPTION, "MODULO", "%", 0),
    MODULO("mod", null, 20, Text.MODULO_DESCRIPTION, null, "", 0),
    NEGATE("±", null, 26, Text.NONE, null, "", 0, 139),
    SQRT("√", null, 24, Text.NONE, "SQUARE", "r", 0),
    SQUARE("x²", null, 22, Text.NONE, null, "", 0),
    FACTORIAL("!", "x!", 22, Text.FACTORIAL_DESCRIPTION, null, "!", 0),
    RECIPROCAL("1/x", null, 22, Text.RECIPROCAL_DESCRIPTION, null, "i", 0),
    FLOOR("floor", "⌊x⌋", 22, Text.FLOOR_DESCRIPTION, "CEIL", "", 0),
    CEIL("ceil", "⌈x⌉", 22, Text.CEIL_DESCRIPTION, null, "", 0),
    LOG10("log10", null, 20, Text.LOG10_DESCRIPTION, "TEN_POWER", "", 0),
    TEN_POWER("10ˣ", null, 22, Text.TEN_POWER_DESCRIPTION, null, "", 0),
    LN("ln", null, 20, Text.NONE, "EXP", "l", 0),
    EXP("eˣ", null, 20, Text.EXP_DESCRIPTION, null, "E", 0),
    SIN("sin", null, 20, Text.NONE, "ASIN", "s", 0),
    COS("cos", null, 20, Text.NONE, "ACOS", "c", 0),
    TAN("tan", null, 20, Text.NONE, "ATAN", "t", 0),
    ASIN("asin", null, 20, Text.NONE, null, "", 0),
    ACOS("acos", null, 20, Text.NONE, null, "", 0),
    ATAN("atan", null, 20, Text.NONE, null, "", 0),
    UNDO(Command.UNDO, Text.UNDO, 16, Text.UNDO_DESCRIPTION, null, "", 54),
    REDO(Command.REDO, null, 16, Text.NONE, null, "", 53),
    ENTER(Command.ENTER, Text.ENTER, 18, Text.ENTER_DESCRIPTION, null, "", 0, 66, 160, 62),
    CLEAR("CLEAR", Text.CLEAR, 26, Text.CLEAR_STACK, null, "", 0),
    CE("CE", "CE", 26, Text.CLEAR_ENTRY, null, "", 0),
    BACK("BACK", "⌫", 26, Text.BACKSPACE, null, "", 0, 67),
    DELETE("DELETE", null, 26, Text.BACKSPACE, null, "", 0, 112),
    DROP("DROP", null, 26, Text.DROP_ROLL_DESCRIPTION, null, "", 0),
    SWAP("SWAP", Text.SWAP, 26, Text.SWAP_DESCRIPTION, null, "", 47),
    DUP("DUP", Text.DUPLICATE, 16, Text.NONE, null, "", 32),
    SMART_CLEAR("SMART_CLEAR", null, 26, Text.CLEAR_ENTRY, null, "", 40),
    PASTE(Command.PASTE, null, 18, Text.NONE, null, "", 50),
    LEFT("LEFT", null, 16, Text.NONE, null, "", 0, 21),
    RIGHT("RIGHT", null, 16, Text.NONE, null, "", 0, 22),
    HOME("HOME", null, 16, Text.NONE, null, "", 122, 122),
    END("END", null, 16, Text.NONE, null, "", 123, 123),
    ESCAPE("ESCAPE", null, 16, Text.NONE, null, "", 0, 111),
    DECIMAL(".", null, 26, Text.DECIMAL_SEPARATOR, null, ".", 0, 158),
    FACTOR("Factor", Text.FACTOR, 16, Text.FACTOR_DESCRIPTION, null, "f", 0),
    INVERSE(Command.INVERSE, "INV", 16, Text.INVERSE_OFF, null, "", 0),
    FUNCTIONS("FUNCTIONS", "f()", 16, Text.NONE, null, "", 0),
    COMPLEMENTS("COMPLEMENTS", "f⁻¹()", 16, Text.NONE, null, "", 0),
    ROLL_UP("ROLL_UP", null, 16, Text.NONE, null, "", 0),
    ROLL_DOWN("ROLL_DOWN", null, 16, Text.NONE, null, "", 0),
    ZERO("0", null, 26, Text.NONE, null, "", 0, 144),
    ONE("1", null, 26, Text.NONE, null, "", 0, 145),
    TWO("2", null, 26, Text.NONE, null, "", 0, 146),
    THREE("3", null, 26, Text.NONE, null, "", 0, 147),
    FOUR("4", null, 26, Text.NONE, null, "", 0, 148),
    FIVE("5", null, 26, Text.NONE, null, "", 0, 149),
    SIX("6", null, 26, Text.NONE, null, "", 0, 150),
    SEVEN("7", null, 26, Text.NONE, null, "", 0, 151),
    EIGHT("8", null, 26, Text.NONE, null, "", 0, 152),
    NINE("9", null, 26, Text.NONE, null, "", 0, 153);

    public enum Text {
        NONE,
        BACKSPACE,
        CEIL_DESCRIPTION,
        CLEAR,
        CLEAR_ENTRY,
        CLEAR_STACK,
        DECIMAL_SEPARATOR,
        DROP_ROLL_DESCRIPTION,
        DUPLICATE,
        E_DESCRIPTION,
        ENTER,
        ENTER_DESCRIPTION,
        EXP_DESCRIPTION,
        FACTOR,
        FACTOR_DESCRIPTION,
        FACTORIAL_DESCRIPTION,
        FLOOR_DESCRIPTION,
        INVERSE_OFF,
        LOG10_DESCRIPTION,
        MODULO_DESCRIPTION,
        PHI_DESCRIPTION,
        PI_DESCRIPTION,
        POWER_DESCRIPTION,
        RECIPROCAL_DESCRIPTION,
        REMAINDER_DESCRIPTION,
        ROOT_DESCRIPTION,
        SWAP,
        SWAP_DESCRIPTION,
        TAU_TOGGLE_DESCRIPTION,
        TEN_POWER_DESCRIPTION,
        UNDO,
        UNDO_DESCRIPTION
    }

    public final String wire, label;
    public final Text labelText, description;
    public final int preferredSp;
    private final String inverseName, unicode;
    private final int ctrl;
    private final int[] codes;
    private static final Map<String, CalculatorKey> WIRES = new HashMap<>();
    private static final Map<Integer, CalculatorKey> CODES = new HashMap<>(),
            UNICODE = new HashMap<>(),
            CONTROL = new HashMap<>();

    static {
        for (CalculatorKey key : values()) {
            WIRES.put(key.wire, key);
            for (int code : key.codes) CODES.put(code, key);
            for (int i = 0; i < key.unicode.length(); i++)
                UNICODE.put((int) key.unicode.charAt(i), key);
            if (key.ctrl != 0) CONTROL.put(key.ctrl, key);
        }
    }

    CalculatorKey(
            String wire,
            Object label,
            int size,
            Text description,
            String inverse,
            String unicode,
            int ctrl,
            int... codes) {
        this.wire = wire;
        this.label = label instanceof String ? (String) label : wire;
        this.labelText = label instanceof Text ? (Text) label : Text.NONE;
        this.description = description;
        preferredSp = size;
        inverseName = inverse;
        this.unicode = unicode;
        this.ctrl = ctrl;
        this.codes = codes;
    }

    public CalculatorKey inverse() {
        return inverseName == null ? null : valueOf(inverseName);
    }

    public static CalculatorKey find(String wire) {
        return WIRES.get(wire);
    }

    public static String keyboard(int code, int unicode, boolean ctrl) {
        CalculatorKey key = (ctrl ? CONTROL : CODES).get(code);
        if (key == null && !ctrl) {
            int digit = Character.digit(unicode, 10);
            key = digit >= 0 ? WIRES.get(Integer.toString(digit)) : UNICODE.get(unicode);
        }
        return key == null ? null : key.wire;
    }
}
