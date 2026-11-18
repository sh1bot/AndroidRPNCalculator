// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.SubscriptSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The keypad view: control row, scientific function rows and primary rows, laid out from the {@link KeypadLayout} plan supplied by the workspace. */
public final class CalculatorKeypad extends ViewGroup {
    private final List<ViewGroup> primaryRows = new ArrayList<>();
    private final List<LinearLayout> scientificRows = new ArrayList<>();
    private final Button functions, complements;
    private final ThemeColors colors;
    private LocaleNumbers numbers;
    private RadixMode radixMode = RadixMode.DECIMAL;
    private final String[] keyDescriptions;
    private final Map<CalculatorKey, Button> buttons = new java.util.EnumMap<>(CalculatorKey.class);
    private final LinearLayout controls;
    private boolean tau;
    private Consumer<Boolean> saveTau = enabled -> {};
    private boolean mirrored;
    private Consumer<Boolean> saveMirror = enabled -> {};
    private boolean calculating;
    private InteractionMode mode = InteractionMode.STACK;
    private boolean expanded, inverse;
    private final BiConsumer<Boolean, Boolean> savePanel;
    /**
     * The function panel's short edge in key units: the function keys are two thirds of a
     * normal key on their short edge, so the panel spans 2 units — a column is 2 units wide,
     * and a squeezed band takes 2 of every 2 + primaryRows units of the keypad body.
     */
    private static final int FUNCTION_UNITS = 2;

    private KeypadLayout plan;
    private KeypadLayout.FunctionPlacement placement = KeypadLayout.FunctionPlacement.SQUEEZE;
    // The keypad's internal geometry, derived in onMeasure from its measured extent, the
    // plan's placement and the panel state; onLayout positions from these values.
    private int functionHeight, digitsX, functionX;
    private final Consumer<Command> command;
    private Runnable beforeInput = () -> {};

    void setBeforeInput(Runnable refresh) {
        beforeInput = refresh;
    }

    private final class KeyButton extends Button {
        Runnable click, hold;

        KeyButton() {
            super(CalculatorKeypad.this.getContext());
            setOnClickListener(
                    v -> {
                        beforeInput.run();
                        if (isEnabled() && click != null) click.run();
                    });
            setOnLongClickListener(
                    v -> {
                        beforeInput.run();
                        if (!isEnabled() || hold == null) return false;
                        hold.run();
                        return true;
                    });
        }
    }

    /** Keeps radix digits inside the decimal 3-by-3 footprint beside fixed operators. */
    private final class DigitBand extends ViewGroup {
        private final int columns, digitCount;

        DigitBand(CalculatorKey[] digits, int columns) {
            super(CalculatorKeypad.this.getContext());
            this.columns = columns;
            digitCount = digits.length;
            for (CalculatorKey digit : digits) addView(button(digit));
            addView(button(CalculatorKey.MULTIPLY));
            addView(button(CalculatorKey.SUBTRACT));
            addView(button(CalculatorKey.ADD));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
            int digitWidth = width * 3 / 4;
            int digitRows = digitCount / columns;
            for (int i = 0; i < digitCount; i++) {
                int column = i % columns, row = i / columns;
                measureBandButton(
                        (Button) getChildAt(i),
                        digitWidth * (column + 1) / columns - digitWidth * column / columns,
                        height * (row + 1) / digitRows - height * row / digitRows);
            }
            for (int i = 0; i < 3; i++)
                measureBandButton(
                        (Button) getChildAt(digitCount + i),
                        width - digitWidth,
                        height * (i + 1) / 3 - height * i / 3);
            setMeasuredDimension(width, height);
        }

        private void measureBandButton(Button button, int cellWidth, int cellHeight) {
            int gap = Math.min(dp(8), cellHeight / 6);
            // Buttons fill their cells. Squareness is not decided here:
            // KeypadLayout budgets the keypad's space, and buttons come out
            // square where that budget leaves square cells.
            int width = Math.max(1, cellWidth - gap), height = Math.max(1, cellHeight - gap);
            fitLabel(button, width, height);
            button.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left, height = bottom - top, digitWidth = width * 3 / 4;
            int digitRows = digitCount / columns;
            for (int i = 0; i < digitCount; i++) {
                int column = i % columns, row = i / columns;
                layoutBandButton(
                        (Button) getChildAt(i),
                        digitWidth * column / columns,
                        height * row / digitRows,
                        digitWidth * (column + 1) / columns,
                        height * (row + 1) / digitRows);
            }
            for (int i = 0; i < 3; i++)
                layoutBandButton(
                        (Button) getChildAt(digitCount + i),
                        digitWidth,
                        height * i / 3,
                        width,
                        height * (i + 1) / 3);
        }

        private void layoutBandButton(Button button, int left, int top, int right, int bottom) {
            int x = left + (right - left - button.getMeasuredWidth()) / 2;
            int y = top + (bottom - top - button.getMeasuredHeight()) / 2;
            button.layout(x, y, x + button.getMeasuredWidth(), y + button.getMeasuredHeight());
        }
    }

    public CalculatorKeypad(
            Context context,
            Consumer<Command> command,
            boolean expanded,
            BiConsumer<Boolean, Boolean> savePanel) {
        super(context);
        keyDescriptions = getResources().getStringArray(R.array.key_descriptions);
        this.command = command;
        this.expanded = expanded;
        this.savePanel = savePanel;
        colors = new ThemeColors(context);
        numbers = new LocaleNumbers(context.getResources().getConfiguration().getLocales().get(0));
        controls = new LinearLayout(context);
        // The three control buttons all take the derived control height, so there is no
        // baseline to align: with baseline alignment on, LinearLayout placed the ƒ()
        // and ƒ⁻¹() buttons from baselines sampled mid-measure, and they landed 1px
        // lower or higher depending on whether the function panel was open.
        controls.setBaselineAligned(false);
        controls.addView(button(CalculatorKey.UNDO), new LinearLayout.LayoutParams(0, dp(44), 1));
        functions = button(CalculatorKey.FUNCTIONS);
        complements = button(CalculatorKey.COMPLEMENTS);
        controls.addView(functions, new LinearLayout.LayoutParams(0, dp(44), 2));
        controls.addView(complements, new LinearLayout.LayoutParams(0, dp(44), 2));
        addView(controls);
        addRows(FUNCTION_KEYS, scientificRows);
        rebuildPrimaryRows();
        bindButtons();
        updateExpansion();
    }

    private String effectiveKey(String wire) {
        CalculatorKey key = CalculatorKey.find(wire);
        if (key == CalculatorKey.PI && tau) return CalculatorKey.TAU.wire;
        return inverse && key != null && key.inverse() != null ? key.inverse().wire : wire;
    }

    private CharSequence functionLabel(String wire) {
        CalculatorKey key = CalculatorKey.find(wire);
        if (key == CalculatorKey.LOG) {
            // The base is written in the current base, where it always reads
            // "10" -- the same convention as the stack's ×10 marker.
            SpannableString logarithm = new SpannableString("log10");
            logarithm.setSpan(
                    new RelativeSizeSpan(.7f), 3, logarithm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            logarithm.setSpan(
                    new SubscriptSpan(), 3, logarithm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return logarithm;
        }
        if (key == null) return wire;
        int resource =
                switch (key) {
                    case UNDO -> R.string.undo;
                    case ENTER -> R.string.enter;
                    case CLEAR -> R.string.clear;
                    case SWAP -> R.string.swap;
                    case FACTOR -> R.string.factor;
                    default -> 0;
                };
        if (resource != 0) return string(resource);
        return key.label;
    }

    public void setInverse(boolean active) {
        inverse = active;
        bindButtons();
        updateExpansion();
    }

    public void setTauPreference(boolean enabled, Consumer<Boolean> save) {
        tau = enabled;
        saveTau = save;
        bindButtons();
    }

    public void setMirrorPreference(boolean enabled, Consumer<Boolean> save) {
        mirrored = enabled;
        saveMirror = save;
        requestLayout();
    }

    boolean isMirrored() {
        return mirrored;
    }

    public void setNumberFormat(LocaleNumbers format) {
        if (numbers == format) return;
        numbers = format;
        if (radixMode != format.radixMode()) {
            radixMode = format.radixMode();
            rebuildPrimaryRows();
        }
        bindButtons();
    }

    public int primaryColumns() {
        return 4;
    }

    public int primaryRowCount() {
        return 5;
    }

    public void setMode(InteractionMode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        bindButtons();
    }

    private void bindButtons() {
        for (Map.Entry<CalculatorKey, Button> item : buttons.entrySet())
            bindButton(item.getKey(), item.getValue());
    }

    private String string(int id) {
        return getContext().getString(id);
    }

    private String string(int id, Object... args) {
        return getContext().getString(id, args);
    }

    private Runnable send(String wire) {
        return () -> command.accept(Command.key(wire));
    }

    // Presentation and both gestures are assigned together for the button's current role.
    private void bindButton(CalculatorKey definition, Button button) {
        String key = effectiveKey(definition.wire);
        CharSequence label = functionLabel(key);
        String description = keyDescription(key);
        int icon = 0;
        boolean editing = mode == InteractionMode.ENTRY, selecting = mode == InteractionMode.SELECT;
        Runnable click = send(key);
        Runnable hold = null;
        Operation operation = Operation.find(key);
        boolean enabled =
                !selecting || !key.equals("Factor") && (operation == null || operation.arity != 2);
        if (EntryEditor.isEntryKey(key)) {
            android.text.SpannableString digit =
                    new android.text.SpannableString(numbers.entry(label.toString()));
            label = numbers.styleDozenal(digit);
            if (key.equals(".")) {
                description =
                        string(
                                editing
                                        ? R.string.decimal_exponent_description
                                        : R.string.decimal_separator);
                if (editing) hold = send("HOLD_DECIMAL");
            } else {
                description =
                        editing
                                ? label.toString()
                                : string(
                                        R.string.digit_pick_description,
                                        label,
                                        key.equals("0") ? 10 : Character.digit(key.charAt(0), 16));
                hold = () -> command.accept(Command.holdDigit(key));
            }
        }
        switch (definition) {
            case FUNCTIONS, COMPLEMENTS -> {
                boolean complementary = definition == CalculatorKey.COMPLEMENTS;
                boolean open = expanded && inverse == complementary;
                description =
                        string(
                                complementary
                                        ? open
                                                ? R.string.close_complements
                                                : R.string.open_complements
                                        : open
                                                ? R.string.close_functions
                                                : R.string.open_functions);
                click = () -> togglePanel(complementary);
                if (complementary) {
                    // Long-press ƒ⁻¹() toggles the left-handed mirror, but only beside the
                    // stack, where mirroring has an effect. In a stacked layout the hold
                    // keeps the gesture's established meaning and toggles the panel.
                    hold =
                            () -> {
                                if (plan != null && plan.besideStack) {
                                    mirrored = !mirrored;
                                    saveMirror.accept(mirrored);
                                    requestLayout();
                                } else {
                                    togglePanel(true);
                                }
                            };
                }
            }
            case PI -> {
                description =
                        string(
                                tau
                                        ? R.string.tau_toggle_description
                                        : R.string.pi_toggle_description);
                hold =
                        () -> {
                            tau = !tau;
                            bindButtons();
                            saveTau.accept(tau);
                            requestLayout();
                        };
            }
            case RECIPROCAL -> {
                SpannableString fraction = new SpannableString(label);
                fraction.setSpan(new ReciprocalSpan(), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                label = fraction;
            }
            case LOG ->
                    description =
                            string(
                                    inverse
                                            ? R.string.base_power_description
                                            : R.string.logarithm_description,
                                    radixMode.radix);
            case CLEAR -> {
                label = selecting ? "" : editing ? "CE" : string(R.string.clear);
                icon = selecting ? R.drawable.ic_select_all : 0;
                description =
                        string(
                                selecting
                                        ? R.string.select_all_description
                                        : editing ? R.string.clear_entry : R.string.clear_stack);
                click = send(editing ? "CE" : "CLEAR");
            }
            case BACK -> {
                label = editing ? "⌫" : "";
                icon = editing ? 0 : R.drawable.ic_delete;
                description =
                        string(
                                selecting
                                        ? R.string.drop_selected
                                        : editing
                                                ? R.string.backspace
                                                : R.string.drop_roll_description);
                click = send(editing ? "BACK" : "DROP");
                if (!selecting) hold = send(editing ? "BACK" : "ROLL_DOWN");
            }
            case SWAP -> {
                // In selection mode the swap key copies the selection to the clipboard.
                enabled = true;
                if (selecting) {
                    label = "";
                    icon = R.drawable.ic_copy;
                    description = string(R.string.copy_selected);
                } else {
                    label = "⇅";
                    hold = send("ROLL_UP");
                }
            }
            case ADD, MULTIPLY, DIVIDE, SUBTRACT -> {
                enabled = true;
                if (selecting) {
                    switch (definition) {
                        case ADD -> {
                            label = "Σ";
                            description = string(R.string.sum_selected);
                        }
                        case MULTIPLY -> {
                            label = "Π";
                            description = string(R.string.product_selected);
                        }
                        case DIVIDE -> {
                            label = "μ";
                            description = string(R.string.mean_selected);
                        }
                        case SUBTRACT -> {
                            label = "σ";
                            description = string(R.string.standard_deviation_selected);
                        }
                        default -> {}
                    }
                }
            }
            case NEGATE -> hold = send(CalculatorKey.ABS.wire);
            case ENTER -> {
                label = string(selecting ? R.string.pick : R.string.enter);
                description =
                        string(selecting ? R.string.pick_selected : R.string.enter_description);
                if (!selecting) hold = send(Command.PASTE);
            }
            case UNDO -> {
                label =
                        calculating
                                ? string(R.string.cancel)
                                : selecting ? "" : string(R.string.undo);
                icon = selecting && !calculating ? R.drawable.ic_select_none : 0;
                description =
                        string(
                                calculating
                                        ? R.string.cancel_calculation
                                        : selecting
                                                ? R.string.select_none_description
                                                : R.string.undo_description);
                click = send(calculating ? Command.CANCEL : Command.UNDO);
                if (calculating || !selecting)
                    hold = send(calculating ? Command.CANCEL : Command.REDO);
            }
            default -> {}
        }
        button.setText(label);
        setButtonIcon(button, icon);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .38f);
        KeyButton bound = (KeyButton) button;
        bound.click = click;
        bound.hold = hold;
        button.setLongClickable(hold != null);
    }

    private void setButtonIcon(Button button, int resource) {
        android.graphics.drawable.Drawable icon =
                resource == 0 ? null : getContext().getDrawable(resource);
        if (icon != null) icon.setTint(colors.text);
        button.setForegroundGravity(Gravity.CENTER);
        button.setForeground(icon);
    }

    public void setCalculating(boolean active) {
        if (calculating == active) return;
        calculating = active;
        bindButton(CalculatorKey.UNDO, buttons.get(CalculatorKey.UNDO));
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private Button button(CalculatorKey definition) {
        String key = definition.wire;
        Button button = new KeyButton();
        button.setAllCaps(false);
        button.setTag(key);
        buttons.put(definition, button);
        styleKey(key, button);
        if (EntryEditor.isEntryKey(key)) {
            button.setTypeface(
                    android.graphics.Typeface.create(
                            "monospace", android.graphics.Typeface.NORMAL));
            button.setFontFeatureSettings("tnum");
        }
        bindButton(definition, button);
        return button;
    }

    private void styleKey(String key, Button b) {
        b.setTextColor(colors.text);
        b.setSingleLine(true);
        b.setHorizontallyScrolling(false);
        b.setIncludeFontPadding(false);
        b.setAutoSizeTextTypeUniformWithConfiguration(10, 26, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setElevation(0);
        b.setStateListAnimator(null);
        boolean number = EntryEditor.isEntryKey(key);
        int color =
                key.equals(Command.ENTER)
                        ? colors.enterKey
                        : number ? colors.numberKey : colors.functionKey;
        if (key.equals(Command.ENTER)) b.setTextColor(colors.enterText);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(100));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple), shape, null));
    }

    private String keyDescription(String wire) {
        CalculatorKey key = CalculatorKey.find(wire);
        String translated = key == null ? "" : keyDescriptions[key.ordinal()];
        return translated.isEmpty() ? wire : translated;
    }

    private void addRows(CalculatorKey[][] keys, List<LinearLayout> rows) {
        for (CalculatorKey[] entries : keys) {
            LinearLayout row = row(entries);
            rows.add(row);
            addView(row);
        }
    }

    private LinearLayout row(CalculatorKey[] entries) {
        LinearLayout row = new LinearLayout(getContext());
        for (CalculatorKey definition : entries) {
            FrameLayout cell = new FrameLayout(getContext());
            Button b = button(definition);
            cell.addView(b, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
            float weight =
                    radixMode != RadixMode.DECIMAL
                                    && entries.length == 3
                                    && (definition == CalculatorKey.DECIMAL
                                            || definition == CalculatorKey.ENTER)
                            ? 1.5f
                            : 1;
            row.addView(cell, new LinearLayout.LayoutParams(0, -1, weight));
        }
        return row;
    }

    private void rebuildPrimaryRows() {
        for (ViewGroup row : primaryRows) removeView(row);
        primaryRows.clear();
        for (CalculatorKey key : PRIMARY_KEYS) buttons.remove(key);
        String layout =
                switch (radixMode) {
                    case DECIMAL -> "789456123";
                    case DOZENAL -> "9AB678345012";
                    case HEX -> "CDEF89AB45670123";
                };
        CalculatorKey[] digits = new CalculatorKey[layout.length()];
        for (int i = 0; i < digits.length; i++)
            digits[i] = CalculatorKey.find(layout.substring(i, i + 1));
        LinearLayout commandRow =
                row(
                        new CalculatorKey[] {
                            CalculatorKey.CLEAR,
                            CalculatorKey.BACK,
                            CalculatorKey.SWAP,
                            CalculatorKey.DIVIDE
                        });
        DigitBand digitBand = new DigitBand(digits, radixMode == RadixMode.HEX ? 4 : 3);
        LinearLayout bottomRow =
                row(
                        radixMode == RadixMode.DECIMAL
                                ? new CalculatorKey[] {
                                    CalculatorKey.ZERO,
                                    CalculatorKey.DECIMAL,
                                    CalculatorKey.NEGATE,
                                    CalculatorKey.ENTER
                                }
                                : new CalculatorKey[] {
                                    CalculatorKey.DECIMAL, CalculatorKey.NEGATE, CalculatorKey.ENTER
                                });
        primaryRows.add(commandRow);
        primaryRows.add(digitBand);
        primaryRows.add(bottomRow);
        addView(commandRow);
        addView(digitBand);
        addView(bottomRow);
        requestLayout();
    }

    private static final java.util.Set<CalculatorKey> PRIMARY_KEYS =
            java.util.EnumSet.of(
                    CalculatorKey.CLEAR,
                    CalculatorKey.BACK,
                    CalculatorKey.SWAP,
                    CalculatorKey.DIVIDE,
                    CalculatorKey.MULTIPLY,
                    CalculatorKey.SUBTRACT,
                    CalculatorKey.ADD,
                    CalculatorKey.DECIMAL,
                    CalculatorKey.NEGATE,
                    CalculatorKey.ENTER,
                    CalculatorKey.ZERO,
                    CalculatorKey.ONE,
                    CalculatorKey.TWO,
                    CalculatorKey.THREE,
                    CalculatorKey.FOUR,
                    CalculatorKey.FIVE,
                    CalculatorKey.SIX,
                    CalculatorKey.SEVEN,
                    CalculatorKey.EIGHT,
                    CalculatorKey.NINE,
                    CalculatorKey.DIGIT_A,
                    CalculatorKey.DIGIT_B,
                    CalculatorKey.DIGIT_C,
                    CalculatorKey.DIGIT_D,
                    CalculatorKey.DIGIT_E,
                    CalculatorKey.DIGIT_F);

    private void togglePanel(boolean complementary) {
        boolean close = expanded && inverse == complementary;
        expanded = !close;
        setInverse(complementary);
        savePanel.accept(expanded, inverse);
    }

    public boolean isExpanded() {
        return expanded;
    }

    private static final CalculatorKey[][] FUNCTION_KEYS = {
        {
            CalculatorKey.SIN,
            CalculatorKey.COS,
            CalculatorKey.TAN,
            CalculatorKey.RECIPROCAL,
            CalculatorKey.FACTORIAL
        },
        {
            CalculatorKey.LN,
            CalculatorKey.LOG,
            CalculatorKey.SQRT,
            CalculatorKey.FLOOR,
            CalculatorKey.FACTOR
        },
        {
            CalculatorKey.PI,
            CalculatorKey.E,
            CalculatorKey.PHI,
            CalculatorKey.POWER,
            CalculatorKey.REMAINDER
        }
    };

    /** Rows become columns, so every function group keeps its own column in landscape. */
    private static CalculatorKey[][] transpose(CalculatorKey[][] keys) {
        CalculatorKey[][] result = new CalculatorKey[keys[0].length][keys.length];
        for (int r = 0; r < keys.length; r++)
            for (int c = 0; c < keys[r].length; c++) result[c][r] = keys[r][c];
        return result;
    }

    private void rebuildFunctionRows() {
        for (LinearLayout row : scientificRows) removeView(row);
        scientificRows.clear();
        // A column on either side is the transposed grid; a band keeps the row order.
        boolean column =
                placement == KeypadLayout.FunctionPlacement.LEFT
                        || placement == KeypadLayout.FunctionPlacement.RIGHT;
        addRows(column ? transpose(FUNCTION_KEYS) : FUNCTION_KEYS, scientificRows);
        updateExpansion();
    }

    /**
     * Takes the layout plan from the workspace. The plan carries the workspace geometry and
     * the placement; the keypad derives its internal geometry from them in {@link #onMeasure}.
     */
    void setGeometry(KeypadLayout plan) {
        this.plan = plan;
        if (placement == plan.functionPlacement) return;
        placement = plan.functionPlacement;
        rebuildFunctionRows();
    }

    private void updateExpansion() {
        for (LinearLayout row : scientificRows) row.setVisibility(expanded ? VISIBLE : GONE);
        setExpansionIndicator(functions, expanded && !inverse);
        setExpansionIndicator(complements, expanded && inverse);
        functions.setActivated(expanded && !inverse);
        complements.setActivated(expanded && inverse);
        functions.setTextColor(expanded && !inverse ? colors.accent : colors.text);
        complements.setTextColor(expanded && inverse ? colors.accent : colors.text);
        if (Build.VERSION.SDK_INT >= 30) {
            functions.setStateDescription(
                    string(expanded && !inverse ? R.string.expanded : R.string.collapsed));
            complements.setStateDescription(
                    string(expanded && inverse ? R.string.expanded : R.string.collapsed));
        }
        requestLayout();
    }

    private void setExpansionIndicator(Button button, boolean open) {
        // Each twisty is a disclosure arrow for its own set: while the set is open it
        // points in the direction of its content, and while closed it points the opposite
        // way, away from the content. The content sits below the button in SQUEEZE, above
        // it in ABOVE, to its left in LEFT and to its right in RIGHT — RIGHT is LEFT's
        // mirror, so its two directions swap.
        int resource =
                switch (placement) {
                    case LEFT -> open ? R.drawable.ic_expand_left : R.drawable.ic_expand_right;
                    case RIGHT -> open ? R.drawable.ic_expand_right : R.drawable.ic_expand_left;
                    case SQUEEZE -> open ? R.drawable.ic_expand_more : R.drawable.ic_expand_less;
                    case ABOVE -> open ? R.drawable.ic_expand_less : R.drawable.ic_expand_more;
                };
        android.graphics.drawable.Drawable indicator = getContext().getDrawable(resource);
        indicator.setTint(open ? colors.accent : colors.text);
        button.setCompoundDrawablePadding(dp(4));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, indicator, null);
    }

    private int preferredLabelSize(String wire) {
        CalculatorKey key = CalculatorKey.find(wire);
        return key == null ? 16 : key.preferredSp;
    }

    private void fitDeleteIcon(Button button, int width, int height, int maximum) {
        android.graphics.drawable.Drawable icon = getContext().getDrawable(R.drawable.ic_delete);
        icon.setTint(colors.text);
        int size = Math.min(maximum, Math.min(width, height));
        int left = (width - size) / 2, top = (height - size) / 2;
        button.setForeground(
                new android.graphics.drawable.InsetDrawable(
                        icon, left, top, width - size - left, height - size - top));
        button.setForegroundGravity(Gravity.FILL);
    }

    private void fitLabel(Button button, int width, int height) {
        int minimum =
                Math.max(
                        1,
                        Math.round(
                                TypedValue.applyDimension(
                                        TypedValue.COMPLEX_UNIT_SP,
                                        10,
                                        getResources().getDisplayMetrics())));
        String key = effectiveKey((String) button.getTag());
        int preferred = preferredLabelSize(key);
        int preferredPixels =
                Math.round(
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP,
                                preferred,
                                getResources().getDisplayMetrics()));
        // One label policy for every layout: the cap follows the button's own
        // size, so labels change only where the layout changes the buttons.
        int maximum =
                Math.max(
                        minimum,
                        Math.min(
                                preferredPixels, Math.round(Math.min(width, height) * .55f)));
        // Drop is a vector rather than text; give it the same sp-aware size budget.
        if (key.equals("BACK") && button.getForeground() != null)
            fitDeleteIcon(button, width, height, maximum);
        if (button.getAutoSizeMaxTextSize() != maximum
                || button.getAutoSizeMinTextSize() != minimum)
            button.setAutoSizeTextTypeUniformWithConfiguration(
                    minimum, maximum, 1, TypedValue.COMPLEX_UNIT_PX);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        // The plan, which the workspace always supplies before measuring, carries the
        // workspace geometry and the placement; the internal subdivision is derived here
        // from the measured extent, the placement and the panel state, using the policy's
        // own intermediates: the key unit is the height over primaryRows + ½ everywhere
        // except ABOVE, whose unit is width-derived.
        int planGap = plan.gap;
        int rows = primaryRowCount(), columns = primaryColumns();
        int digitsWidth, controlHeight, functionWidth;
        if (placement == KeypadLayout.FunctionPlacement.LEFT
                || placement == KeypadLayout.FunctionPlacement.RIGHT) {
            // A column beside the digits: beside the stack the keypad spans the full
            // height and the digits keep their natural width; above the keypad the band
            // is the keypad's whole height and opening the column spends keypad width.
            double unit = height / (rows + .5);
            controlHeight = (int) Math.round(unit / 2);
            int columnWidth = (int) Math.round(FUNCTION_UNITS * unit);
            functionWidth = expanded ? columnWidth : 0;
            functionHeight = expanded ? height - controlHeight : 0;
            digitsWidth =
                    plan.besideStack
                            ? (int) Math.round(columns * unit)
                            : expanded ? width - columnWidth - planGap : width;
        } else if (placement == KeypadLayout.FunctionPlacement.SQUEEZE) {
            // A band inside the body: it takes FUNCTION_UNITS of every FUNCTION_UNITS +
            // primaryRows units of the body below the control row.
            double unit = height / (rows + .5);
            controlHeight = (int) Math.round(unit / 2);
            functionWidth = expanded ? width : 0;
            functionHeight =
                    expanded
                            ? (height - controlHeight) * FUNCTION_UNITS / (FUNCTION_UNITS + rows)
                            : 0;
            digitsWidth = width;
        } else {
            // ABOVE: the band is ceded by the stack at the width-derived unit.
            double widthUnit = (double) width / columns;
            controlHeight = (int) Math.round(widthUnit / 2);
            functionWidth = expanded ? width : 0;
            functionHeight = expanded ? (int) Math.round(FUNCTION_UNITS * widthUnit) : 0;
            digitsWidth = width;
        }
        // Positions for onLayout: the digits hug the right of the view and a function
        // column hugs the left, with mirroring swapping the two sides; a band shares the
        // digits' left edge. Mirroring applies only beside the stack.
        this.digitsX = mirrored && plan.besideStack ? 0 : width - digitsWidth;
        this.functionX =
                switch (placement) {
                    case LEFT -> 0;
                    case RIGHT -> width - functionWidth;
                    case SQUEEZE, ABOVE -> this.digitsX;
                };
        int inset = Math.min(dp(8), controlHeight / 6);
        for (int i = 0; i < controls.getChildCount(); i++) {
            Button control = (Button) controls.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) control.getLayoutParams();
            lp.height = Math.max(1, controlHeight - inset);
            lp.topMargin = inset / 2;
            lp.bottomMargin = inset - inset / 2;
            lp.leftMargin = inset / 2;
            lp.rightMargin = inset / 2;
            fitLabel(control, digitsWidth * (i == 0 ? 1 : 2) / 5 - inset, lp.height);
        }
        controls.measure(
                MeasureSpec.makeMeasureSpec(digitsWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(controlHeight, MeasureSpec.EXACTLY));
        int body = Math.max(1, height - controlHeight);
        // The primary rows fill what the function panel leaves of the body: a LEFT column
        // leaves the whole body, a SQUEEZE band takes its height off it, and an ABOVE band
        // takes its height plus the one plan gap the view holds between band and controls.
        int primaryBody =
                switch (placement) {
                    case LEFT, RIGHT -> body;
                    case SQUEEZE -> Math.max(1, body - functionHeight);
                    case ABOVE -> Math.max(1, body - functionHeight - (functionHeight > 0 ? planGap : 0));
                };
        int unit = 0;
        for (int i = 0; i < primaryRows.size(); i++) {
            ViewGroup primary = primaryRows.get(i);
            int span = primary instanceof DigitBand ? 3 : 1;
            int rowHeight = primaryBody * (unit + span) / 5 - primaryBody * unit / 5;
            if (primary instanceof LinearLayout)
                measureRow((LinearLayout) primary, digitsWidth, rowHeight);
            else
                primary.measure(
                        MeasureSpec.makeMeasureSpec(digitsWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
            unit += span;
        }
        if (expanded) {
            int rowsHeight =
                    placement == KeypadLayout.FunctionPlacement.LEFT
                                    || placement == KeypadLayout.FunctionPlacement.RIGHT
                            ? body
                            : functionHeight;
            for (int i = 0, n = scientificRows.size(); i < n; i++) {
                int rowHeight = rowsHeight * (i + 1) / n - rowsHeight * i / n;
                measureRow(
                        scientificRows.get(i), Math.max(1, functionWidth), Math.max(1, rowHeight));
            }
        }
        setMeasuredDimension(width, height);
    }

    private void measureRow(LinearLayout row, int width, int height) {
        int gap = Math.min(dp(8), height / 6);
        float totalWeight = 0;
        for (int c = 0; c < row.getChildCount(); c++)
            totalWeight += ((LinearLayout.LayoutParams) row.getChildAt(c).getLayoutParams()).weight;
        for (int c = 0; c < row.getChildCount(); c++) {
            FrameLayout cell = (FrameLayout) row.getChildAt(c);
            float weight = ((LinearLayout.LayoutParams) cell.getLayoutParams()).weight;
            int cellWidth = Math.round(width * weight / totalWeight);
            Button button = (Button) cell.getChildAt(0);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
            lp.width = Math.max(1, cellWidth - gap);
            lp.height = Math.max(1, height - gap);
            fitLabel(button, lp.width, lp.height);
        }
        row.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, height), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int digitsWidth = controls.getMeasuredWidth();
        // Horizontal positions and the band height were derived in onMeasure.
        int digitsX = this.digitsX;
        int functionX = this.functionX;
        int functionHeight = this.functionHeight;
        // An ABOVE band sits at the very top of the view, one plan gap over the control row;
        // every other placement starts with the control row.
        int controlY =
                placement == KeypadLayout.FunctionPlacement.ABOVE && functionHeight > 0
                        ? functionHeight + plan.gap
                        : 0;
        controls.layout(
                digitsX, controlY, digitsX + digitsWidth, controlY + controls.getMeasuredHeight());
        if (expanded) {
            int fy =
                    placement == KeypadLayout.FunctionPlacement.ABOVE
                            ? 0
                            : controlY + controls.getMeasuredHeight();
            for (LinearLayout row : scientificRows) {
                row.layout(
                        functionX,
                        fy,
                        functionX + row.getMeasuredWidth(),
                        fy + row.getMeasuredHeight());
                fy += row.getMeasuredHeight();
            }
        }
        int y =
                controlY
                        + controls.getMeasuredHeight()
                        + (placement == KeypadLayout.FunctionPlacement.SQUEEZE ? functionHeight : 0);
        for (ViewGroup row : primaryRows) {
            row.layout(digitsX, y, digitsX + row.getMeasuredWidth(), y + row.getMeasuredHeight());
            y += row.getMeasuredHeight();
        }
    }
}
