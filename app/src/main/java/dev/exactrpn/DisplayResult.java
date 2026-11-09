// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.Transfer.readText;
import static dev.exactrpn.Transfer.writeText;

import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/** Explicit, transient display protocol; contains no Parcelable or Java object serialization. */
final class DisplayResult {
    private static final String[] TEXT = {
        "stackId",
        "entry",
        "problem",
        "angle",
        "notation",
        "checkpoint",
        "operationError",
        "compactTop"
    };
    private static final String[] INTS = {"numberMode", "fractionMode", "start", "end"};

    static byte[] encode(Bundle result, EntryEditor editor) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x44525035);
            boolean copy = result.containsKey("copyText");
            out.writeBoolean(copy);
            if (copy) {
                writeText(out, result.getString("copyText"));
                return Transfer.require(bytes.toByteArray());
            }
            byte[] state = result.getByteArray("state");
            out.writeInt(state.length);
            out.write(state);
            for (String key : TEXT) writeText(out, result.getString(key));
            ArrayList<String> list = result.getStringArrayList("rows");
            out.writeInt(list.size());
            for (String value : list) writeText(out, value);
            for (String key : INTS) out.writeInt(result.getInt(key));
            out.writeBoolean(result.getBoolean("editing"));
            out.writeBoolean(result.getBoolean("raw"));
            Transfer.writeBytes(
                    out, editor.save(CalculationLimits.TRANSFER_BYTES - bytes.size() - 4));
        }
        return Transfer.require(bytes.toByteArray());
    }

    static Bundle decode(byte[] bytes) throws IOException {
        try (DataInputStream in =
                new DataInputStream(new ByteArrayInputStream(Transfer.require(bytes)))) {
            if (in.readInt() != 0x44525035) throw new IOException("Unknown display protocol");
            Bundle result = new Bundle();
            if (in.readBoolean()) {
                result.putString("copyText", readText(in));
                if (in.read() != -1) throw new IOException("Trailing copy data");
                return result;
            }
            int size = in.readInt();
            if (size < 0 || size > in.available()) throw new IOException("Invalid snapshot length");
            byte[] state = new byte[size];
            in.readFully(state);
            result.putByteArray("state", state);
            for (String key : TEXT) result.putString(key, readText(in));
            int count = in.readInt();
            if (count < 0 || count > in.available() / 4)
                throw new IOException("Invalid display row count");
            ArrayList<String> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) list.add(readText(in));
            result.putStringArrayList("rows", list);
            for (String key : INTS) result.putInt(key, in.readInt());
            result.putBoolean("editing", in.readBoolean());
            result.putBoolean("raw", in.readBoolean());
            result.putByteArray("entryState", Transfer.readBytes(in));
            if (in.read() != -1) throw new IOException("Trailing display data");
            return result;
        }
    }
}
