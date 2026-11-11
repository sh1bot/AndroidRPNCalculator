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

/** Round primary keys become shorter capsules to make room for scientific functions. */
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
    private boolean calculating;
    private InteractionMode mode = InteractionMode.STACK;
    private boolean expanded, inverse;
    private final BiConsumer<Boolean, Boolean> savePanel;
    private boolean functionsBeside;
    private int primaryWidth;
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
            int width = Math.max(1, cellWidth - gap), height = Math.max(1, cellHeight - gap);
            if (!expanded) width = height = Math.min(width, height);
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
        controls.addView(button(CalculatorKey.UNDO), new LinearLayout.LayoutParams(0, dp(44), 1));
        functions = button(CalculatorKey.FUNCTIONS);
        complements = button(CalculatorKey.COMPLEMENTS);
        controls.addView(functions, new LinearLayout.LayoutParams(0, dp(44), 2));
        controls.addView(complements, new LinearLayout.LayoutParams(0, dp(44), 2));
        addView(controls);
        addRows(
                new CalculatorKey[][] {
                    {
                        CalculatorKey.SIN,
                        CalculatorKey.COS,
                        CalculatorKey.TAN,
                        CalculatorKey.RECIPROCAL,
                        CalculatorKey.FACTORIAL
                    },
                    {
                        CalculatorKey.LN,
                        CalculatorKey.LOG10,
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
                },
                scientificRows);
        rebuildPrimaryRows();
        bindButtons();
        updateExpansion();
    }

    private String effectiveKey(String wire) {
        CalculatorKey key = CalculatorKey.find(wire);
        if (key == CalculatorKey.PI && tau) return CalculatorKey.TAU.wire;
        if (key == CalculatorKey.LOG10) {
            int base = radixMode.radix;
            if (!inverse) return base == 10 ? key.wire : "log" + base;
            return base == 10 ? key.inverse().wire : base + "ˣ";
        }
        return inverse && key != null && key.inverse() != null ? key.inverse().wire : wire;
    }

    private CharSequence functionLabel(String wire) {
        CalculatorKey key = CalculatorKey.find(wire);
        if (wire.equals("log12") || wire.equals("log16") || key == CalculatorKey.LOG10) {
            String base = wire.equals("log12") ? "12" : wire.equals("log16") ? "16" : "10";
            SpannableString logarithm = new SpannableString("log" + base);
            logarithm.setSpan(
                    new RelativeSizeSpan(.7f), 3, logarithm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            logarithm.setSpan(
                    new SubscriptSpan(), 3, logarithm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return logarithm;
        }
        if (wire.equals("12ˣ") || wire.equals("16ˣ")) return wire;
        if (key == null) return wire;
        int resource =
                switch (key) {
                    case UNDO -> R.string.undo;
                    case ENTER -> R.string.enter;
                    case CLEAR -> R.string.clear;
                    case SWAP -> R.string.swap;
                    case DUP -> R.string.duplicate;
                    case FACTOR -> R.string.factor;
                    default -> 0;
                };
        if (resource != 0) return getContext().getString(resource);
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

    // Presentation and both gestures are assigned together for the button's current role.
    private void bindButton(CalculatorKey definition, Button button) {
        String key = effectiveKey(definition.wire);
        CharSequence label = functionLabel(key);
        String description = keyDescription(key);
        int icon = 0;
        boolean editing = mode == InteractionMode.ENTRY, selecting = mode == InteractionMode.SELECT;
        Runnable click = () -> command.accept(Command.key(key));
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
                        getContext()
                                .getString(
                                        editing
                                                ? R.string.decimal_exponent_description
                                                : R.string.decimal_separator);
                if (editing) hold = () -> command.accept(Command.key("HOLD_DECIMAL"));
            } else {
                description =
                        editing
                                ? label.toString()
                                : getContext()
                                        .getString(
                                                R.string.digit_pick_description,
                                                label,
                                                key.equals("0")
                                                        ? 10
                                                        : Character.digit(key.charAt(0), 16));
                hold = () -> command.accept(Command.holdDigit(key));
            }
        }
        switch (definition) {
            case FUNCTIONS, COMPLEMENTS -> {
                boolean complementary = definition == CalculatorKey.COMPLEMENTS;
                boolean open = expanded && inverse == complementary;
                description =
                        getContext()
                                .getString(
                                        complementary
                                                ? open
                                                        ? R.string.close_complements
                                                        : R.string.open_complements
                                                : open
                                                        ? R.string.close_functions
                                                        : R.string.open_functions);
                click = () -> togglePanel(complementary);
            }
            case PI -> {
                description =
                        getContext()
                                .getString(
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
            case LOG10 ->
                    description =
                            getContext()
                                    .getString(
                                            inverse
                                                    ? R.string.base_power_description
                                                    : R.string.logarithm_description,
                                            radixMode.radix);
            case CLEAR -> {
                label = selecting ? "" : editing ? "CE" : getContext().getString(R.string.clear);
                icon = selecting ? R.drawable.ic_select_all : 0;
                description =
                        getContext()
                                .getString(
                                        selecting
                                                ? R.string.select_all_description
                                                : editing
                                                        ? R.string.clear_entry
                                                        : R.string.clear_stack);
                click = () -> command.accept(Command.key(editing ? "CE" : "CLEAR"));
            }
            case BACK -> {
                label = editing ? "⌫" : "";
                icon = editing ? 0 : R.drawable.ic_delete;
                description =
                        getContext()
                                .getString(
                                        selecting
                                                ? R.string.drop_selected
                                                : editing
                                                        ? R.string.backspace
                                                        : R.string.drop_roll_description);
                click = () -> command.accept(Command.key(editing ? "BACK" : "DROP"));
                if (!selecting)
                    hold = () -> command.accept(Command.key(editing ? "BACK" : "ROLL_DOWN"));
            }
            case SWAP -> {
                label = "⇅";
                enabled = !selecting;
                if (!selecting) hold = () -> command.accept(Command.key("ROLL_UP"));
            }
            case ADD, MULTIPLY -> {
                enabled = true;
                if (selecting) {
                    label = definition == CalculatorKey.ADD ? "Σ" : "Π";
                    description =
                            getContext()
                                    .getString(
                                            definition == CalculatorKey.ADD
                                                    ? R.string.sum_selected
                                                    : R.string.product_selected);
                }
            }
            case ENTER -> {
                label = getContext().getString(selecting ? R.string.pick : R.string.enter);
                description =
                        getContext()
                                .getString(
                                        selecting
                                                ? R.string.pick_selected
                                                : R.string.enter_description);
                if (!selecting) hold = () -> command.accept(Command.key(Command.PASTE));
            }
            case UNDO -> {
                label =
                        calculating
                                ? getContext().getString(R.string.cancel)
                                : selecting ? "" : getContext().getString(R.string.undo);
                icon = selecting && !calculating ? R.drawable.ic_select_none : 0;
                description =
                        getContext()
                                .getString(
                                        calculating
                                                ? R.string.cancel_calculation
                                                : selecting
                                                        ? R.string.select_none_description
                                                        : R.string.undo_description);
                click =
                        () ->
                                command.accept(
                                        Command.key(calculating ? Command.CANCEL : Command.UNDO));
                if (calculating || !selecting)
                    hold =
                            () ->
                                    command.accept(
                                            Command.key(
                                                    calculating ? Command.CANCEL : Command.REDO));
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
        CalculatorKey[] digits =
                switch (radixMode) {
                    case DECIMAL ->
                            new CalculatorKey[] {
                                CalculatorKey.SEVEN,
                                CalculatorKey.EIGHT,
                                CalculatorKey.NINE,
                                CalculatorKey.FOUR,
                                CalculatorKey.FIVE,
                                CalculatorKey.SIX,
                                CalculatorKey.ONE,
                                CalculatorKey.TWO,
                                CalculatorKey.THREE
                            };
                    case DOZENAL ->
                            new CalculatorKey[] {
                                CalculatorKey.EIGHT,
                                CalculatorKey.NINE,
                                CalculatorKey.DIGIT_A,
                                CalculatorKey.DIGIT_B,
                                CalculatorKey.FOUR,
                                CalculatorKey.FIVE,
                                CalculatorKey.SIX,
                                CalculatorKey.SEVEN,
                                CalculatorKey.ZERO,
                                CalculatorKey.ONE,
                                CalculatorKey.TWO,
                                CalculatorKey.THREE
                            };
                    case HEX ->
                            new CalculatorKey[] {
                                CalculatorKey.DIGIT_C,
                                CalculatorKey.DIGIT_D,
                                CalculatorKey.DIGIT_E,
                                CalculatorKey.DIGIT_F,
                                CalculatorKey.EIGHT,
                                CalculatorKey.NINE,
                                CalculatorKey.DIGIT_A,
                                CalculatorKey.DIGIT_B,
                                CalculatorKey.FOUR,
                                CalculatorKey.FIVE,
                                CalculatorKey.SIX,
                                CalculatorKey.SEVEN,
                                CalculatorKey.ZERO,
                                CalculatorKey.ONE,
                                CalculatorKey.TWO,
                                CalculatorKey.THREE
                            };
                };
        LinearLayout commandRow =
                row(
                        new CalculatorKey[] {
                            CalculatorKey.CLEAR,
                            CalculatorKey.BACK,
                            CalculatorKey.SWAP,
                            CalculatorKey.DIVIDE
                        });
        DigitBand digitBand = new DigitBand(digits, radixMode == RadixMode.DECIMAL ? 3 : 4);
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

    void setGeometry(boolean beside, int primaryWidth) {
        functionsBeside = beside;
        this.primaryWidth = primaryWidth;
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
                    getContext()
                            .getString(
                                    expanded && !inverse ? R.string.expanded : R.string.collapsed));
            complements.setStateDescription(
                    getContext()
                            .getString(
                                    expanded && inverse ? R.string.expanded : R.string.collapsed));
        }
        requestLayout();
    }

    private void setExpansionIndicator(Button button, boolean open) {
        android.graphics.drawable.Drawable indicator =
                getContext()
                        .getDrawable(open ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
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
        // A small folded-only increase; expanded function labels keep their existing sizes.
        if (!expanded) preferred += preferred >= 24 ? 2 : 1;
        int preferredPixels =
                Math.round(
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP,
                                preferred,
                                getResources().getDisplayMetrics()));
        // Restore one third of the former 55%-to-80% headroom, with modest sp caps.
        float sizeFraction = expanded ? .55f : .55f + (.8f - .55f) / 3;
        int maximum =
                Math.max(
                        minimum,
                        Math.min(
                                preferredPixels,
                                Math.round(Math.min(width, height) * sizeFraction)));
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
        int digitsWidth = functionsBeside ? Math.min(primaryWidth, width) : width;
        int controlHeight = Math.min(dp(48), Math.max(1, height / 6));
        int gap = Math.min(dp(8), controlHeight / 6);
        for (int i = 0; i < controls.getChildCount(); i++) {
            Button control = (Button) controls.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) control.getLayoutParams();
            lp.height = Math.max(1, controlHeight - gap);
            lp.topMargin = gap / 2;
            lp.bottomMargin = gap - gap / 2;
            lp.leftMargin = gap / 2;
            lp.rightMargin = gap / 2;
            fitLabel(control, digitsWidth * (i == 0 ? 1 : 2) / 5 - gap, lp.height);
        }
        controls.measure(
                MeasureSpec.makeMeasureSpec(digitsWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(controlHeight, MeasureSpec.EXACTLY));
        int body = Math.max(1, height - controlHeight);
        int functionBody = expanded && !functionsBeside ? body * 36 / 100 : 0;
        int primaryBody = body - functionBody;
        int unit = 0;
        for (int i = 0; i < primaryRows.size(); i++) {
            ViewGroup primary = primaryRows.get(i);
            int span = primary instanceof DigitBand ? 3 : 1;
            int rowHeight = primaryBody * (unit + span) / 5 - primaryBody * unit / 5;
            if (primary instanceof LinearLayout)
                measureRow((LinearLayout) primary, digitsWidth, rowHeight, !expanded);
            else
                primary.measure(
                        MeasureSpec.makeMeasureSpec(digitsWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
            unit += span;
        }
        if (expanded)
            for (int i = 0; i < scientificRows.size(); i++) {
                int functionWidth = functionsBeside ? width - digitsWidth - dp(12) : width;
                int rowHeight =
                        functionsBeside
                                ? body / 5
                                : functionBody * (i + 1) / 3 - functionBody * i / 3;
                measureRow(
                        scientificRows.get(i),
                        Math.max(1, functionWidth),
                        Math.max(1, rowHeight),
                        false);
            }
        setMeasuredDimension(width, height);
    }

    private void measureRow(LinearLayout row, int width, int height, boolean round) {
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
            if (round && weight == 1) lp.width = lp.height = Math.min(lp.width, lp.height);
            fitLabel(button, lp.width, lp.height);
        }
        row.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, height), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int digitsWidth = controls.getMeasuredWidth();
        int digitsX = getWidth() - digitsWidth;
        controls.layout(digitsX, 0, getWidth(), controls.getMeasuredHeight());
        int y = controls.getMeasuredHeight();
        if (expanded) {
            int fy = y;
            for (LinearLayout row : scientificRows) {
                int x = functionsBeside ? 0 : digitsX;
                row.layout(x, fy, x + row.getMeasuredWidth(), fy + row.getMeasuredHeight());
                fy += row.getMeasuredHeight();
            }
            if (!functionsBeside) y = fy;
        }
        for (ViewGroup row : primaryRows) {
            row.layout(digitsX, y, getWidth(), y + row.getMeasuredHeight());
            y += row.getMeasuredHeight();
        }
    }
}
