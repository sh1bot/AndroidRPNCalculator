// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.Transfer.*;

import java.io.*;
import java.util.ArrayList;

/** Arithmetic reply: numerical data and state, independent of presentation. */
final class DisplayResult {
    enum EditorDisposition {
        KEEP,
        RESTORE
    }

    EditorDisposition editorDisposition = EditorDisposition.RESTORE;
    String stackId, checkpoint;
    CalculationError.Code operationError;
    int errorArgument;
    byte[] state, entryState;
    boolean readOnly;
    ArrayList<NumericValue> values = new ArrayList<>();
    RpnCalculator.AngleUnit angle = RpnCalculator.AngleUnit.RAD;

    static byte[] encode(DisplayResult result, EntryEditor editor) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x44525039);
            out.writeBoolean(result.readOnly);
            writeText(out, result.stackId);
            out.writeInt(result.values.size());
            for (NumericValue value : result.values) NumericCodec.writeValue(out, value);
            if (!result.readOnly) {
                out.writeByte(result.editorDisposition.ordinal());
                writeBytes(out, result.state);
                writeText(out, result.checkpoint);
                out.writeInt(result.operationError == null ? -1 : result.operationError.ordinal());
                out.writeInt(result.errorArgument);
                out.writeByte(result.angle.ordinal());
                writeBytes(out, editor.save(CalculationLimits.TRANSFER_BYTES - bytes.size() - 4));
            }
        }
        return require(bytes.toByteArray());
    }

    static DisplayResult decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(require(bytes)))) {
            if (in.readInt() != 0x44525039) throw new IOException("Unknown result protocol");
            DisplayResult result = new DisplayResult();
            result.readOnly = in.readBoolean();
            result.stackId = readText(in);
            int count = in.readInt();
            if (count < 0 || count > in.available() / 10)
                throw new IOException("Invalid value count");
            for (int i = 0; i < count; i++) result.values.add(NumericCodec.readValue(in));
            if (!result.readOnly) {
                result.editorDisposition = readEnum(in, EditorDisposition.values());
                result.state = readBytes(in);
                result.checkpoint = readText(in);
                int code = in.readInt();
                if (code < -1 || code >= CalculationError.Code.values().length)
                    throw new IOException("Invalid error code");
                result.operationError = code == -1 ? null : CalculationError.Code.values()[code];
                result.errorArgument = in.readInt();
                result.angle = readEnum(in, RpnCalculator.AngleUnit.values());
                result.entryState = readBytes(in);
                if (result.state == null || result.entryState == null)
                    throw new IOException("Missing calculation state");
            }
            if (in.available() != 0) throw new IOException("Trailing result data");
            return result;
        }
    }
}
