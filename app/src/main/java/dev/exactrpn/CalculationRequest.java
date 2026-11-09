// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.Transfer.*;

import java.io.*;

/** One typed request schema, shared by the UI and arithmetic process. */
final class CalculationRequest {
    final Command command;
    String locale = "en-US", inputId, sessionId;
    byte[] input, entryDraft;
    boolean entryBranch;
    RpnCalculator.AngleUnit angle = RpnCalculator.AngleUnit.RAD;
    ValueFormatter.Notation notation = ValueFormatter.Notation.SCIENTIFIC;
    NumberMode numberMode = NumberMode.LOCALE;
    FractionMode fractionMode = FractionMode.DECIMAL;

    CalculationRequest(Command command) {
        this.command = command;
    }

    byte[] encode(String session, String checkpoint, boolean includeInput) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x52515232);
        writeCommand(out, command);
        writeText(out, locale);
        writeText(out, checkpoint);
        writeText(out, session);
        writeBytes(out, includeInput ? input : null);
        writeBytes(out, entryDraft);
        out.writeBoolean(entryBranch);
        out.writeByte(angle.ordinal());
        out.writeByte(notation.ordinal());
        out.writeByte(numberMode.ordinal());
        out.writeByte(fractionMode.ordinal());
        return require(bytes.toByteArray());
    }

    static CalculationRequest decode(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(require(bytes)));
        if (in.readInt() != 0x52515232) throw new IOException("Unknown request format");
        CalculationRequest result = new CalculationRequest(readCommand(in));
        result.locale = readText(in);
        result.inputId = readText(in);
        result.sessionId = readText(in);
        result.input = readBytes(in);
        result.entryDraft = readBytes(in);
        result.entryBranch = in.readBoolean();
        result.angle = readEnum(in, RpnCalculator.AngleUnit.values());
        result.notation = readEnum(in, ValueFormatter.Notation.values());
        result.numberMode = readEnum(in, NumberMode.values());
        result.fractionMode = readEnum(in, FractionMode.values());
        if (result.locale == null || result.sessionId == null || in.available() != 0)
            throw new IOException("Invalid request data");
        return result;
    }

    private static void writeCommand(DataOutputStream out, Command command) throws IOException {
        out.writeByte(command.kind.ordinal());
        switch (command.kind) {
            case KEY -> writeText(out, command.wire);
            case EDIT -> {
                writeText(out, command.text);
                out.writeInt(command.start);
                out.writeInt(command.end);
                out.writeBoolean(command.raw);
                out.writeBoolean(command.explicitDecimal);
            }
            case SELECT -> {
                out.writeInt(command.start);
                out.writeInt(command.end);
            }
            case PASTE_RAW, HOLD_DIGIT -> writeText(out, command.text);
            default -> {
                if (command.kind == Command.Kind.UNARY_ROWS)
                    out.writeByte(command.operation.ordinal());
                int[] rows = command.rows();
                out.writeInt(rows.length);
                for (int row : rows) out.writeInt(row);
            }
        }
    }

    private static Command readCommand(DataInputStream in) throws IOException {
        Command.Kind kind = readEnum(in, Command.Kind.values());
        try {
            return switch (kind) {
                case KEY -> Command.key(readText(in));
                case EDIT ->
                        Command.edit(
                                readText(in),
                                in.readInt(),
                                in.readInt(),
                                in.readBoolean(),
                                in.readBoolean());
                case SELECT -> Command.select(in.readInt(), in.readInt());
                case PASTE_RAW -> Command.paste(readText(in));
                case HOLD_DIGIT -> Command.holdDigit(readText(in));
                default -> {
                    Operation operation =
                            kind == Command.Kind.UNARY_ROWS
                                    ? readEnum(in, Operation.values())
                                    : null;
                    int count = in.readInt();
                    if (count < 0 || count > in.available() / 4)
                        throw new IOException("Invalid row count");
                    int[] rows = new int[count];
                    for (int i = 0; i < count; i++) rows[i] = in.readInt();
                    yield operation == null
                            ? Command.rows(kind, rows)
                            : Command.unaryRows(operation, rows);
                }
            };
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IOException("Invalid command", ex);
        }
    }
}
