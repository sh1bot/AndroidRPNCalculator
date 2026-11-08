// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.HashMap;
import java.util.Map;

/** Key identity and presentation metadata shared by arithmetic, hardware input and Android. */
public enum CalculatorKey {
    PI,
    TAU,
    E,
    PHI,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    POWER,
    ROOT,
    REMAINDER,
    MODULO,
    NEGATE,
    SQRT,
    SQUARE,
    FACTORIAL,
    RECIPROCAL,
    FLOOR,
    CEIL,
    LOG10,
    TEN_POWER,
    LN,
    EXP,
    SIN,
    COS,
    TAN,
    ASIN,
    ACOS,
    ATAN,
    UNDO,
    REDO,
    ENTER,
    CLEAR,
    CE,
    BACK,
    DELETE,
    DROP,
    SWAP,
    DUP,
    SMART_CLEAR,
    PASTE,
    LEFT,
    RIGHT,
    HOME,
    END,
    ESCAPE,
    DECIMAL,
    FACTOR,
    INVERSE,
    FUNCTIONS,
    COMPLEMENTS,
    ROLL_UP,
    ROLL_DOWN,
    ZERO,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE;
    public final String wire, label;
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

    CalculatorKey() {
        String[] row = Data.ROWS[ordinal()].split("\\|", -1);
        if (!name().equals(row[0])) throw new IllegalStateException("Key table order");
        wire = field(row, 1, name());
        label = field(row, 2, wire);
        preferredSp = Integer.parseInt(field(row, 3, "26"));
        inverseName = field(row, 4, "");
        unicode = field(row, 5, "");
        ctrl = Integer.parseInt(field(row, 6, "0"));
        String hardware = field(row, 7, "");
        String[] keys = hardware.isEmpty() ? new String[0] : hardware.split(",");
        codes = new int[keys.length];
        for (int i = 0; i < keys.length; i++) codes[i] = Integer.parseInt(keys[i]);
    }

    private static String field(String[] row, int index, String fallback) {
        return index < row.length && !row[index].isEmpty() ? row[index] : fallback;
    }

    // ID | wire (defaults to ID) | label | size (26sp) | inverse | characters | Ctrl | key codes.
    // IDs make row order explicit and checked; trailing defaults are omitted.
    private static final class Data {
        static final String[] ROWS =
                """
                PI|π||24||p
                TAU|τ||24
                E|e||24||e
                PHI|φ||24
                ADD|+||||+||157
                SUBTRACT|−||||-−||156
                MULTIPLY|×||||*×||155
                DIVIDE|÷||||/÷||154
                POWER|xʸ|yˣ|22|ROOT|^
                ROOT|root|ˣ√y|22
                REMAINDER|rem||20|MODULO|%
                MODULO|mod||20
                NEGATE|±||||||139
                SQRT|√||24|SQUARE|r
                SQUARE|x²||22
                FACTORIAL|!|x!|22||!
                RECIPROCAL|1/x||22||i
                FLOOR|floor|⌊x⌋|22|CEIL
                CEIL|ceil|⌈x⌉|22
                LOG10|log10||20|TEN_POWER
                TEN_POWER|10ˣ||22
                LN|ln||20|EXP|l
                EXP|eˣ||20||E
                SIN|sin||20|ASIN|s
                COS|cos||20|ACOS|c
                TAN|tan||20|ATAN|t
                ASIN|asin||20
                ACOS|acos||20
                ATAN|atan||20
                UNDO|||16|||54
                REDO|||16|||53
                ENTER|||18||||66,160,62
                CLEAR
                CE
                BACK||⌫|||||67
                DELETE|||||||112
                DROP
                SWAP||||||47
                DUP|||16|||32
                SMART_CLEAR||||||40
                PASTE|||18|||50
                LEFT|||16||||21
                RIGHT|||16||||22
                HOME|||16|||122|122
                END|||16|||123|123
                ESCAPE|||16||||111
                DECIMAL|.||||.||158
                FACTOR|Factor||16||f
                INVERSE||INV|16
                FUNCTIONS||f()|16
                COMPLEMENTS||f⁻¹()|16
                ROLL_UP|||16
                ROLL_DOWN|||16
                ZERO|0||||||144
                ONE|1||||||145
                TWO|2||||||146
                THREE|3||||||147
                FOUR|4||||||148
                FIVE|5||||||149
                SIX|6||||||150
                SEVEN|7||||||151
                EIGHT|8||||||152
                NINE|9||||||153
                """
                        .split("\n");
    }

    public CalculatorKey inverse() {
        return inverseName.isEmpty() ? null : valueOf(inverseName);
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
