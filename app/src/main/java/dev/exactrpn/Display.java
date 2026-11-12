// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Display {
    final List<String> rows;
    final List<FormattedValue> presentations, fullPresentations, compactPresentations;
    final List<NumericValue> values;
    final String entry, problem, stackId;
    final ValueFormatter.Notation notation;
    final boolean editing, raw;
    final FractionMode fractionMode;
    final NumberMode numberMode;
    final RadixMode radixMode;
    final int start, end;
    final RpnCalculator.AngleUnit angleUnit;

    private static final class Rows extends java.util.AbstractList<String>
            implements java.util.RandomAccess {
        private final List<FormattedValue> values;

        Rows(List<FormattedValue> values) {
            this.values = values;
        }

        @Override
        public String get(int index) {
            return values.get(index).canonical;
        }

        @Override
        public int size() {
            return values.size();
        }
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
                settings.angleUnit,
                settings.notation,
                settings.numberMode,
                settings.fractionMode,
                settings.radixMode,
                editor);
    }

    Display(
            Display stack,
            RpnCalculator.AngleUnit angle,
            ValueFormatter.Notation notation,
            NumberMode numberMode,
            FractionMode fractionMode,
            EntryEditor editor) {
        this(
                stack.values,
                stack.stackId,
                angle,
                notation,
                numberMode,
                fractionMode,
                stack.radixMode,
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
        ArrayList<FormattedValue> full = new ArrayList<>(values.size());
        ArrayList<FormattedValue> compact = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            NumericValue value = values.get(i);
            FormattedValue fraction =
                    ValueFormatter.exactFractionPresentation(value, fractionMode);
            full.add(
                    fraction == null
                            ? ValueFormatter.presentation(value, true, notation, fractionMode)
                            : fraction);
            compact.add(
                    ValueFormatter.presentation(
                            value, false, notation, FractionMode.DECIMAL));
        }
        fullPresentations = Collections.unmodifiableList(full);
        compactPresentations = Collections.unmodifiableList(compact);
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
        presentations = editing ? compactPresentations : fullPresentations;
        rows = new Rows(presentations);
    }

    Display(Display stack, EntryEditor editor) {
        values = stack.values;
        fullPresentations = stack.fullPresentations;
        compactPresentations = stack.compactPresentations;
        stackId = stack.stackId;
        notation = stack.notation;
        angleUnit = stack.angleUnit;
        numberMode = stack.numberMode;
        fractionMode = stack.fractionMode;
        radixMode = stack.radixMode;
        editing = editor.isEditing();
        presentations = editing ? compactPresentations : fullPresentations;
        rows = new Rows(presentations);
        entry = editor.entry();
        problem = editor.entryProblem();
        raw = editor.rawEntry();
        start = editor.selectionStart();
        end = editor.selectionEnd();
    }
}
