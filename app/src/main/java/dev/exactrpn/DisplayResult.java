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
    boolean readOnly, exactRequired;
    ArrayList<NumericValue> values = new ArrayList<>();

    static byte[] encode(DisplayResult result, EntryEditor editor) throws IOException {
        boolean[] exact = new boolean[result.values.size()];
        byte[] minimumEntry = null;
        if (!result.readOnly) {
            EntryEditor current = editor.copy();
            current.clearUndo();
            minimumEntry = current.save();
        }
        if (result.exactRequired) {
            java.util.Arrays.fill(exact, true);
        } else {
            int remaining =
                    CalculationLimits.TRANSFER_BYTES
                            - packetSize(result, minimumEntry, exact);
            // Keep recent numerical results first when optional exact data competes for space.
            for (int i = exact.length - 1; i >= 0; i--) {
                RationalValue rational = result.values.get(i).rational;
                int extra = rational == null ? 0 : rational.byteSize() + 8;
                if (extra <= remaining) {
                    exact[i] = true;
                    remaining -= extra;
                }
            }
        }
        byte[] entry = null;
        if (!result.readOnly) {
            int withoutEntry = packetSize(result, new byte[0], exact);
            entry = editor.save(CalculationLimits.TRANSFER_BYTES - withoutEntry);
        }
        return require(packet(result, entry, exact));
    }

    private static int packetSize(DisplayResult result, byte[] entry, boolean[] exact) {
        int size = 4 + 1 + textSize(result.stackId) + 4;
        for (int i = 0; i < result.values.size(); i++)
            size += NumericCodec.valueSize(result.values.get(i), exact[i]);
        if (!result.readOnly)
            size +=
                    1
                            + bytesSize(result.state)
                            + textSize(result.checkpoint)
                            + 4
                            + 4
                            + bytesSize(entry);
        return size;
    }

    private static byte[] packet(DisplayResult result, byte[] entry, boolean[] exact)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x44525040);
            out.writeBoolean(result.readOnly);
            writeText(out, result.stackId);
            out.writeInt(result.values.size());
            for (int i = 0; i < result.values.size(); i++)
                NumericCodec.writeValue(out, result.values.get(i), exact[i]);
            if (!result.readOnly) {
                out.writeByte(result.editorDisposition.ordinal());
                writeBytes(out, result.state);
                writeText(out, result.checkpoint);
                out.writeInt(result.operationError == null ? -1 : result.operationError.ordinal());
                out.writeInt(result.errorArgument);
                writeBytes(out, entry);
            }
        }
        return bytes.toByteArray();
    }

    static DisplayResult decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(require(bytes)))) {
            if (in.readInt() != 0x44525040) throw new IOException("Unknown result protocol");
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
                result.entryState = readBytes(in);
                if (result.state == null || result.entryState == null)
                    throw new IOException("Missing calculation state");
            }
            if (in.available() != 0) throw new IOException("Trailing result data");
            return result;
        }
    }
}
