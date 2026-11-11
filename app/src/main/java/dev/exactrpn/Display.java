// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Display {
    final List<String> rows, fullRows, compactRows;
    final List<NumericValue> values;
    final String entry, problem, stackId;
    final ValueFormatter.Notation notation;
    final boolean editing, raw;
    final FractionMode fractionMode;
    final NumberMode numberMode;
    final RadixMode radixMode;
    final int start, end;
    final RpnCalculator.AngleUnit angleUnit;

    Display(
            ValueFormatter.Notation notation,
            RpnCalculator.AngleUnit angle,
            NumberMode numberMode,
            FractionMode fractionMode) {
        this(notation, angle, numberMode, fractionMode, RadixMode.DECIMAL);
    }

    Display(
            ValueFormatter.Notation notation,
            RpnCalculator.AngleUnit angle,
            NumberMode numberMode,
            FractionMode fractionMode,
            RadixMode radixMode) {
        this(
                Collections.emptyList(),
                "initial",
                angle,
                notation,
                numberMode,
                fractionMode,
                radixMode,
                new EntryEditor());
    }

    Display(DisplayResult result, EntryEditor editor, Display settings) {
        this(
                Collections.unmodifiableList(result.values),
                result.stackId,
                result.angle,
                settings.notation,
                settings.numberMode,
                settings.fractionMode,
                settings.radixMode,
                editor);
    }

    Display(
            Display stack,
            ValueFormatter.Notation notation,
            NumberMode numberMode,
            FractionMode fractionMode,
            EntryEditor editor) {
        this(stack, notation, numberMode, fractionMode, stack.radixMode, editor);
    }

    Display(
            Display stack,
            ValueFormatter.Notation notation,
            NumberMode numberMode,
            FractionMode fractionMode,
            RadixMode radixMode,
            EntryEditor editor) {
        this(
                stack.values,
                stack.stackId,
                stack.angleUnit,
                notation,
                numberMode,
                fractionMode,
                radixMode,
                editor);
    }

    private Display(
            List<NumericValue> values,
            String stackId,
            RpnCalculator.AngleUnit angle,
            ValueFormatter.Notation notation,
            NumberMode numberMode,
            FractionMode fractionMode,
            RadixMode radixMode,
            EntryEditor editor) {
        this.values = values;
        ArrayList<String> full = new ArrayList<>(values.size());
        ArrayList<String> compact = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            NumericValue value = values.get(i);
            String formatted =
                    ValueFormatter.format(value, true, notation, fractionMode);
            String fraction = ValueFormatter.exactFraction(value, fractionMode);
            full.add(fraction == null ? formatted : fraction);
            compact.add(ValueFormatter.format(value, false, notation));
        }
        fullRows = Collections.unmodifiableList(full);
        compactRows = Collections.unmodifiableList(compact);
        this.stackId = stackId;
        this.notation = notation;
        angleUnit = angle;
        this.numberMode = numberMode;
        this.fractionMode = fractionMode;
        this.radixMode = radixMode;
        entry = editor.entry();
        problem = editor.entryProblem();
        editing = editor.isEditing();
        raw = editor.rawEntry();
        start = editor.selectionStart();
        end = editor.selectionEnd();
        rows = editing ? compactRows : fullRows;
    }

    Display(Display stack, EntryEditor editor) {
        values = stack.values;
        fullRows = stack.fullRows;
        compactRows = stack.compactRows;
        stackId = stack.stackId;
        notation = stack.notation;
        angleUnit = stack.angleUnit;
        numberMode = stack.numberMode;
        fractionMode = stack.fractionMode;
        radixMode = stack.radixMode;
        editing = editor.isEditing();
        rows = editing ? compactRows : fullRows;
        entry = editor.entry();
        problem = editor.entryProblem();
        raw = editor.rawEntry();
        start = editor.selectionStart();
        end = editor.selectionEnd();
    }
}
