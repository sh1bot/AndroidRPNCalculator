// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Bundle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Explicit, transient display protocol; contains no Parcelable or Java object serialization. */
final class DisplayResult {
    private static final String[] TEXT = {
        "stackId", "entry", "problem", "angle", "notation", "checkpoint"
    };
    private static final String[] LISTS = {"rows", "clipboard", "compactRows"};
    private static final String[] INTS = {"numberMode", "fractionMode", "start", "end"};

    static void write(File file, Bundle result) throws IOException {
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(0x44525031);
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
    }

    static Bundle read(File file) throws IOException {
        try (DataInputStream in =
                new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != 0x44525031) throw new IOException("Unknown display protocol");
            Bundle result = new Bundle();
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
        if (length < 0 || length > 64 * 1024 * 1024)
            throw new IOException("Invalid display string length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
