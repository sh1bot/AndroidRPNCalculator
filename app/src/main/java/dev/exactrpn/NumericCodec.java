// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.Transfer.*;

import java.io.*;
import java.math.BigInteger;

final class NumericCodec {
    private NumericCodec() {}

    static void writeInteger(DataOutputStream out, BigInteger value) throws IOException {
        writeBytes(out, value.toByteArray());
    }

    private static int integerSize(BigInteger value) {
        return bytesSize(value.toByteArray());
    }

    static int valueSize(NumericValue value, boolean includeExact) {
        int size = integerSize(value.num) + 4 + 4 + 1 + 4 + 1;
        if (includeExact && value.rational != null)
            size +=
                    integerSize(value.rational.numerator)
                            + integerSize(value.rational.denominator);
        return size;
    }

    static BigInteger readInteger(DataInputStream in) throws IOException {
        byte[] bytes = readBytes(in);
        if (bytes == null || bytes.length == 0) throw new IOException("Missing integer");
        return new BigInteger(bytes);
    }

    static void writeRational(DataOutputStream out, RationalValue value) throws IOException {
        writeInteger(out, value.numerator);
        writeInteger(out, value.denominator);
    }

    static RationalValue readRational(DataInputStream in) throws IOException {
        try {
            return new RationalValue(readInteger(in), readInteger(in));
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("Invalid rational", ex);
        }
    }

    static void writeValue(DataOutputStream out, NumericValue value, boolean includeExact)
            throws IOException {
        writeInteger(out, value.num);
        out.writeInt(value.base);
        out.writeInt(value.scale);
        out.writeBoolean(value.exact);
        out.writeInt(value.zeroBound);
        out.writeBoolean(includeExact && value.rational != null);
        if (includeExact && value.rational != null) writeRational(out, value.rational);
    }

    static NumericValue readValue(DataInputStream in) throws IOException {
        try {
            BigInteger num = readInteger(in);
            int base = in.readInt(), scale = in.readInt();
            boolean exact = in.readBoolean();
            int bound = in.readInt();
            return new NumericValue(
                    num, base, scale, exact, bound, in.readBoolean() ? readRational(in) : null);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid numerical result", ex);
        }
    }

    static void writeRequest(DataOutputStream out, NumericRequest request) throws IOException {
        writeInteger(out, request.limit);
        out.writeInt(request.base);
        out.writeInt(request.exactBytes);
        out.writeBoolean(request.requireRational);
    }

    static NumericRequest readRequest(DataInputStream in) throws IOException {
        try {
            return new NumericRequest(
                    readInteger(in), in.readInt(), in.readInt(), in.readBoolean());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid numerical request", ex);
        }
    }
}
