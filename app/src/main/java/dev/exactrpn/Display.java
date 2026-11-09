// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

final class Display {
    final List<String> rows, fullRows, compactRows;
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
        rows = fullRows = compactRows = Collections.emptyList();
        entry = problem = "";
        stackId = "initial";
        this.notation = notation;
        angleUnit = angle;
        this.numberMode = numberMode;
        this.fractionMode = fractionMode;
        editing = raw = false;
        start = end = 0;
    }

    Display(DisplayResult result, EntryEditor editor) {
        fullRows = Collections.unmodifiableList(result.rows);
        String top = result.compactTop;
        compactRows =
                new AbstractList<String>() {
                    public int size() {
                        return fullRows.size();
                    }

                    public String get(int index) {
                        String value = fullRows.get(index);
                        return index == size() - 1 ? top : value;
                    }
                };
        stackId = result.stackId;
        entry = editor.entry();
        problem = editor.entryProblem();
        notation = result.notation;
        angleUnit = result.angle;
        numberMode = result.numberMode;
        fractionMode = result.fractionMode;
        editing = editor.isEditing();
        rows = editing ? compactRows : fullRows;
        raw = editor.rawEntry();
        start = editor.selectionStart();
        end = editor.selectionEnd();
    }

    Display(Display stack, EntryEditor editor) {
        fullRows = stack.fullRows;
        compactRows = stack.compactRows;
        stackId = stack.stackId;
        notation = stack.notation;
        angleUnit = stack.angleUnit;
        numberMode = stack.numberMode;
        fractionMode = stack.fractionMode;
        editing = editor.isEditing();
        rows = editing ? compactRows : fullRows;
        entry = editor.entry();
        problem = editor.entryProblem();
        raw = editor.rawEntry();
        start = editor.selectionStart();
        end = editor.selectionEnd();
    }
}
