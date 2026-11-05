// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Bundle;

import java.util.Collections;
import java.util.List;

final class Display {
    final List<String> rows, clipboard, compactRows;
    final String entry, problem, stackId;
    final ValueFormatter.Notation notation;
    final boolean editing, raw;
    final FractionMode fractionMode;
    final NumberMode numberMode;
    final int start, end;
    final RpnCalculator.AngleUnit angleUnit;

    Display(
            ValueFormatter.Notation notation,
            RpnCalculator.AngleUnit angle,
            NumberMode numberMode,
            FractionMode fractionMode) {
        rows = clipboard = compactRows = Collections.emptyList();
        entry = problem = "";
        stackId = "initial";
        this.notation = notation;
        angleUnit = angle;
        this.numberMode = numberMode;
        this.fractionMode = fractionMode;
        editing = raw = false;
        start = end = 0;
    }

    Display(Bundle result) {
        rows = Collections.unmodifiableList(result.getStringArrayList("rows"));
        clipboard = Collections.unmodifiableList(result.getStringArrayList("clipboard"));
        compactRows = Collections.unmodifiableList(result.getStringArrayList("compactRows"));
        stackId = result.getString("stackId");
        entry = result.getString("entry");
        problem = result.getString("problem");
        notation = ValueFormatter.Notation.valueOf(result.getString("notation"));
        angleUnit = RpnCalculator.AngleUnit.valueOf(result.getString("angle"));
        numberMode = NumberMode.fromId(result.getInt("numberMode"));
        fractionMode = FractionMode.fromId(result.getInt("fractionMode"));
        editing = result.getBoolean("editing");
        raw = result.getBoolean("raw");
        start = result.getInt("start");
        end = result.getInt("end");
    }
}
