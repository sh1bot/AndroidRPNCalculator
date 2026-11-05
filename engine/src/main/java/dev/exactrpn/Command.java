// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Parsed at the application boundary; punctuation inside entry text remains literal. */
public final class Command {
    public enum Kind {
        KEY,
        EDIT,
        SELECT,
        DROP_ROWS,
        PICK_ROWS,
        PASTE_RAW
    }

    public static final String ENTER = "ENTER",
            UNDO = "UNDO",
            REDO = "REDO",
            RESTORE = "RESTORE",
            CHECKPOINT = "CHECKPOINT",
            CANCEL = "CANCEL",
            INVERSE = "INVERSE",
            PASTE = "PASTE";
    private static final Set<String> PRESENTATION =
            new HashSet<>(
                    Arrays.asList("FORMAT", "NUMBER_FORMAT", "FRACTION", "ANGLE", CHECKPOINT));
    public final Kind kind;
    public final String wire;
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
            int[] rows) {
        this.kind = kind;
        this.wire = wire;
        this.text = text;
        this.start = start;
        this.end = end;
        this.raw = raw;
        this.explicitDecimal = explicitDecimal;
        this.rows = rows;
    }

    public static Command parse(String wire) {
        int colon = wire.indexOf(':');
        String prefix = colon < 0 ? wire : wire.substring(0, colon);
        String payload = colon < 0 ? "" : wire.substring(colon + 1);
        Kind kind;
        try {
            kind = Kind.valueOf(prefix);
        } catch (IllegalArgumentException ex) {
            kind = Kind.KEY;
        }
        if (kind == Kind.EDIT) {
            String[] fields = payload.split(":", 5);
            if (fields.length != 5) throw new IllegalArgumentException("Invalid edit command");
            return new Command(
                    kind,
                    wire,
                    fields[4],
                    Integer.parseInt(fields[0]),
                    Integer.parseInt(fields[1]),
                    Boolean.parseBoolean(fields[2]),
                    Boolean.parseBoolean(fields[3]),
                    new int[0]);
        }
        if (kind == Kind.SELECT) {
            String[] fields = payload.split(":", -1);
            if (fields.length != 2) throw new IllegalArgumentException("Invalid selection command");
            return new Command(
                    kind,
                    wire,
                    "",
                    Integer.parseInt(fields[0]),
                    Integer.parseInt(fields[1]),
                    false,
                    false,
                    new int[0]);
        }
        int[] rows =
                kind == Kind.DROP_ROWS || kind == Kind.PICK_ROWS
                        ? Arrays.stream(payload.split(",")).mapToInt(Integer::parseInt).toArray()
                        : new int[0];
        return new Command(kind, wire, payload, 0, 0, false, false, rows);
    }

    public int[] rows() {
        return rows.clone();
    }

    public boolean is(String key) {
        return kind == Kind.KEY && wire.equals(key);
    }

    public boolean preservesSelection() {
        return PRESENTATION.contains(wire)
                || kind == Kind.SELECT
                || kind == Kind.DROP_ROWS
                || kind == Kind.PICK_ROWS;
    }

    public boolean isEntryUpdate() {
        return kind == Kind.EDIT || kind == Kind.SELECT;
    }

    public void apply(
            RpnCalculator calculator, NumberText.Profile locale, NumberText.Profile format) {
        switch (kind) {
            case DROP_ROWS:
                calculator.dropRows(rows);
                break;
            case PICK_ROWS:
                calculator.pickRows(rows);
                break;
            case PASTE_RAW:
                calculator.pasteText(text, locale);
                break;
            case SELECT:
                calculator.select(start, end);
                break;
            case EDIT:
                calculator.editText(text, start, end, raw, explicitDecimal, format);
                break;
            default:
                calculator.key(wire);
        }
    }
}
