// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Typed actions shared by the editor, keypad and arithmetic process. */
public final class Command {
    public enum Kind {
        KEY,
        EDIT,
        SELECT,
        DROP_ROWS(true),
        PICK_ROWS(true),
        SUM_ROWS(true),
        PRODUCT_ROWS(true),
        READ_VALUES(true),
        UNARY_ROWS(true),
        HOLD_DIGIT,
        PASTE_RAW,
        PUSH_VALUES;
        final boolean takesRows;

        Kind() {
            this(false);
        }

        Kind(boolean takesRows) {
            this.takesRows = takesRows;
        }
    }

    public static final String ENTER = "ENTER",
            UNDO = "UNDO",
            REDO = "REDO",
            RESTORE = "RESTORE",
            CANCEL = "CANCEL",
            PASTE = "PASTE";
    private static final Set<String> PRESENTATION =
            new HashSet<>(Arrays.asList("FORMAT", "NUMBER_FORMAT", "FRACTION", "ANGLE"));
    public final Kind kind;
    // Only KEY actions have a key identifier; entry text is never interpreted as a command.
    public final String wire;
    public final Operation operation;
    private RationalValue[] values = new RationalValue[0];
    public final String text;
    public final int start, end;
    public final boolean raw, explicitDecimal;
    private final int[] rows;

    private Command(
            Kind kind,
            String wire,
            String text,
            int start,
            int end,
            boolean raw,
            boolean explicitDecimal,
            int[] rows,
            Operation operation) {
        this.kind = kind;
        this.wire = wire;
        this.text = text;
        this.start = start;
        this.end = end;
        this.raw = raw;
        this.explicitDecimal = explicitDecimal;
        this.rows = rows.clone();
        this.operation = operation;
    }

    public static Command key(String key) {
        return new Command(
                Kind.KEY,
                java.util.Objects.requireNonNull(key),
                "",
                0,
                0,
                false,
                false,
                new int[0],
                null);
    }

    public static Command edit(String text, int start, int end, boolean raw, boolean decimal) {
        return new Command(
                Kind.EDIT,
                "",
                java.util.Objects.requireNonNull(text),
                start,
                end,
                raw,
                decimal,
                new int[0],
                null);
    }

    public static Command select(int start, int end) {
        return new Command(Kind.SELECT, "", "", start, end, false, false, new int[0], null);
    }

    public static Command paste(String text) {
        return new Command(
                Kind.PASTE_RAW,
                "",
                java.util.Objects.requireNonNull(text),
                0,
                0,
                false,
                false,
                new int[0],
                null);
    }

    public static Command holdDigit(String digit) {
        if (digit.length() != 1 || digit.charAt(0) < '0' || digit.charAt(0) > '9')
            throw new IllegalArgumentException("Invalid digit shortcut");
        return new Command(Kind.HOLD_DIGIT, "", digit, 0, 0, false, false, new int[0], null);
    }

    public static Command rows(Kind kind, int... rows) {
        if (!kind.takesRows || kind == Kind.UNARY_ROWS)
            throw new IllegalArgumentException("Not a row action");
        return new Command(kind, "", "", 0, 0, false, false, rows, null);
    }

    public static Command pushValues(RationalValue[] values) {
        Command command =
                new Command(Kind.PUSH_VALUES, "", "", 0, 0, false, false, new int[0], null);
        command.values = values.clone();
        return command;
    }

    public RationalValue[] values() {
        return values.clone();
    }

    public static Command unaryRows(Operation operation, int... rows) {
        if (operation == null || operation.arity != 1)
            throw new IllegalArgumentException("Not a unary operation");
        return new Command(Kind.UNARY_ROWS, "", "", 0, 0, false, false, rows, operation);
    }

    public Command forEntry(boolean editing) {
        if (kind != Kind.KEY) return this;
        String key = entryKey(wire, editing);
        return key == null ? null : key.equals(wire) ? this : key(key);
    }

    public int[] rows() {
        return rows.clone();
    }

    public boolean is(String key) {
        return kind == Kind.KEY && wire.equals(key);
    }

    public static String entryKey(String key, boolean editing) {
        return switch (key) {
            case "HOLD_DECIMAL" -> editing ? key : null;
            case "SMART_CLEAR" -> editing ? "CE" : "CLEAR";
            case "HOLD_DROP" -> editing ? "BACK" : "ROLL_DOWN";
            default -> key;
        };
    }

    public static String cursorDirection(String key) {
        String direction = key.startsWith("SELECT_") ? key.substring(7) : key;
        return switch (direction) {
            case "LEFT", "RIGHT", "HOME", "END" -> direction;
            default -> null;
        };
    }

    public static boolean clearsEntry(String key) {
        return key.equals("CE") || key.equals("CLEAR") || key.equals("CANCEL_ENTRY");
    }

    public boolean isDisplayOnly() {
        return kind == Kind.KEY && PRESENTATION.contains(wire);
    }

    public boolean preservesSelection() {
        return isDisplayOnly() || kind == Kind.SELECT || kind.takesRows || kind == Kind.HOLD_DIGIT;
    }

    public boolean isEntryUpdate() {
        return kind == Kind.EDIT || kind == Kind.SELECT;
    }

    public void apply(EntryEditor editor, NumberText.Profile locale, NumberText.Profile format) {
        switch (kind) {
            case EDIT -> editor.editText(text, start, end, raw, explicitDecimal, format);
            case SELECT -> editor.select(start, end);
            case PASTE_RAW -> editor.pasteClipboard(text, locale);
            case HOLD_DIGIT -> editor.key(text);
            default -> {
                if (!editor.key(wire)) throw new IllegalArgumentException("Not an entry command");
            }
        }
    }

    public void apply(RpnCalculator calculator) {
        switch (kind) {
            case UNARY_ROWS:
                calculator.transformRows(operation, rows);
                break;
            case DROP_ROWS:
                calculator.dropRows(rows);
                break;
            case PICK_ROWS:
                calculator.pickRows(rows);
                break;
            case SUM_ROWS:
                calculator.sumRows(rows);
                break;
            case PRODUCT_ROWS:
                calculator.productRows(rows);
                break;
            case HOLD_DIGIT:
                calculator.holdDigit(text);
                break;
            case PUSH_VALUES:
                calculator.pushValues(values);
                break;
            case PASTE_RAW:
                throw new IllegalArgumentException("Clipboard parsing belongs to the caller");
            case SELECT:
            case EDIT:
                throw new IllegalArgumentException("Entry updates belong to the UI editor");
            default:
                calculator.key(wire);
        }
    }
}
