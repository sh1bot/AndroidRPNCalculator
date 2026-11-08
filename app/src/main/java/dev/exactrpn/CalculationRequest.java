// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static dev.exactrpn.Transfer.*;

import java.io.*;

/** One typed request schema, shared by the UI and arithmetic process. */
final class CalculationRequest {
    final String key;
    String locale = "en-US", inputId, sessionId;
    byte[] input, entryDraft;
    boolean entryBranch;
    RpnCalculator.AngleUnit angle = RpnCalculator.AngleUnit.RAD;
    ValueFormatter.Notation notation = ValueFormatter.Notation.SCIENTIFIC;
    NumberMode numberMode = NumberMode.LOCALE;
    FractionMode fractionMode = FractionMode.DECIMAL;
    private Command parsed;

    CalculationRequest(String key) {
        this.key = key;
    }

    Command command() {
        if (parsed == null) parsed = Command.parse(key);
        return parsed;
    }

    byte[] encode(String session, String checkpoint, boolean includeInput) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x52515231);
        writeText(out, key);
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
        if (in.readInt() != 0x52515231) throw new IOException("Unknown request format");
        CalculationRequest result = new CalculationRequest(readText(in));
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
        if (result.key == null
                || result.locale == null
                || result.sessionId == null
                || in.available() != 0) throw new IOException("Invalid request data");
        return result;
    }
}
