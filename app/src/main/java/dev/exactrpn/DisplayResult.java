// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Explicit, transient display protocol; contains no Parcelable or Java object serialization. */
final class DisplayResult {
    private static final String[] TEXT = {
        "stackId", "entry", "problem", "angle", "notation", "checkpoint", "operationError"
    };
    private static final String[] LISTS = {"rows", "compactRows"};
    private static final String[] INTS = {"numberMode", "fractionMode", "start", "end"};

    static byte[] encode(Bundle result) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x44525033);
            boolean copy = result.containsKey("copyText");
            out.writeBoolean(copy);
            if (copy) {
                writeText(out, result.getString("copyText"));
                return bytes.toByteArray();
            }
            byte[] state = result.getByteArray("state");
            out.writeInt(state.length);
            out.write(state);
            for (String key : TEXT) writeText(out, result.getString(key));
            for (String key : LISTS) {
                ArrayList<String> list = result.getStringArrayList(key);
                out.writeInt(list.size());
                for (String value : list) writeText(out, value);
            }
            for (String key : INTS) out.writeInt(result.getInt(key));
            out.writeBoolean(result.getBoolean("editing"));
            out.writeBoolean(result.getBoolean("raw"));
        }
        return bytes.toByteArray();
    }

    static Bundle decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 0x44525033) throw new IOException("Unknown display protocol");
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
            for (String key : LISTS) {
                int count = in.readInt();
                if (count < 0 || count > 1000000)
                    throw new IOException("Invalid display row count");
                ArrayList<String> list = new ArrayList<>();
                for (int i = 0; i < count; i++) list.add(readText(in));
                result.putStringArrayList(key, list);
            }
            for (String key : INTS) result.putInt(key, in.readInt());
            result.putBoolean("editing", in.readBoolean());
            result.putBoolean("raw", in.readBoolean());
            if (in.read() != -1) throw new IOException("Trailing display data");
            return result;
        }
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 64 * 1024 * 1024 || length > in.available())
            throw new IOException("Invalid display string length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
