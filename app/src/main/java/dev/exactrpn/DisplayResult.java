// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.Transfer.*;

import java.io.*;
import java.util.ArrayList;

/** Typed arithmetic reply. Only the outer Messenger envelope uses an Android Bundle. */
final class DisplayResult {
    enum EditorDisposition {
        KEEP,
        RESTORE
    }

    EditorDisposition editorDisposition = EditorDisposition.RESTORE;
    String stackId, checkpoint, copyText;
    String operationError = "", compactTop = "";
    byte[] state, entryState;
    ArrayList<String> rows = new ArrayList<>();
    RpnCalculator.AngleUnit angle = RpnCalculator.AngleUnit.RAD;
    ValueFormatter.Notation notation = ValueFormatter.Notation.SCIENTIFIC;
    NumberMode numberMode = NumberMode.LOCALE;
    FractionMode fractionMode = FractionMode.DECIMAL;

    static byte[] encode(DisplayResult result, EntryEditor editor) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x44525038);
            boolean copy = result.copyText != null;
            out.writeBoolean(copy);
            if (copy) {
                writeText(out, result.copyText);
            } else {
                out.writeByte(result.editorDisposition.ordinal());
                writeBytes(out, result.state);
                writeText(out, result.stackId);
                writeText(out, result.checkpoint);
                writeText(out, result.operationError);
                writeText(out, result.compactTop);
                out.writeByte(result.angle.ordinal());
                out.writeByte(result.notation.ordinal());
                out.writeByte(result.numberMode.ordinal());
                out.writeByte(result.fractionMode.ordinal());
                out.writeInt(result.rows.size());
                for (String value : result.rows) writeText(out, value);
                writeBytes(out, editor.save(CalculationLimits.TRANSFER_BYTES - bytes.size() - 4));
            }
        }
        return require(bytes.toByteArray());
    }

    static DisplayResult decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(require(bytes)))) {
            if (in.readInt() != 0x44525038) throw new IOException("Unknown display protocol");
            DisplayResult result = new DisplayResult();
            if (in.readBoolean()) {
                result.copyText = readText(in);
                if (result.copyText == null) throw new IOException("Missing clipboard text");
            } else {
                result.editorDisposition = readEnum(in, EditorDisposition.values());
                result.state = readBytes(in);
                result.stackId = readText(in);
                result.checkpoint = readText(in);
                result.operationError = readText(in);
                result.compactTop = readText(in);
                result.angle = readEnum(in, RpnCalculator.AngleUnit.values());
                result.notation = readEnum(in, ValueFormatter.Notation.values());
                result.numberMode = readEnum(in, NumberMode.values());
                result.fractionMode = readEnum(in, FractionMode.values());
                int count = in.readInt();
                if (count < 0 || count > in.available() / 4)
                    throw new IOException("Invalid display row count");
                result.rows.ensureCapacity(count);
                for (int i = 0; i < count; i++) result.rows.add(readText(in));
                result.entryState = readBytes(in);
                if (result.state == null || result.entryState == null)
                    throw new IOException("Missing calculation state");
            }
            if (in.read() != -1) throw new IOException("Trailing display data");
            return result;
        }
    }
}
