// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded Binder payloads. No file descriptors or platform-specific fallback. */
final class Transfer {
    static byte[] require(byte[] bytes) throws IOException {
        if (bytes == null) throw new IOException("Missing transfer payload");
        CalculationLimits.require(bytes.length, CalculationLimits.TRANSFER_BYTES);
        return bytes;
    }

    static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes == null ? -1 : bytes.length);
        if (bytes != null) out.write(bytes);
    }

    static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) return null;
        if (length < 0 || length > in.available()) throw new IOException("Invalid transfer length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    static void writeText(DataOutputStream out, String text) throws IOException {
        writeBytes(out, text == null ? null : text.getBytes(StandardCharsets.UTF_8));
    }

    static String readText(DataInputStream in) throws IOException {
        byte[] bytes = readBytes(in);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    static <T> T readEnum(DataInputStream in, T[] values) throws IOException {
        int ordinal = in.readUnsignedByte();
        if (ordinal >= values.length) throw new IOException("Invalid transfer mode");
        return values[ordinal];
    }
}
