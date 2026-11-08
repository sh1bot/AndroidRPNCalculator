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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Round primary keys become shorter capsules to make room for scientific functions. */
public final class CalculatorKeypad extends ViewGroup {
    private final List<LinearLayout> primaryRows = new ArrayList<>();
    private final List<LinearLayout> scientificRows = new ArrayList<>();
    private final Button functions, complements;
    private final ThemeColors colors;
    private LocaleNumbers numbers;
    private final Map<String, Button> numberButtons = new HashMap<>();
    private final LinearLayout controls;
    private Button deleteButton,
            undoButton,
            enterButton,
            clearButton,
            piButton,
            swapButton,
            sumButton,
            productButton;
    private boolean tau;
    private Consumer<Boolean> saveTau = enabled -> {};
    private boolean calculating, editing, selecting, allSelected;
    private boolean expanded, inverse;
    private final Map<String, Button> inverseButtons = new HashMap<>();
    private final BiConsumer<Boolean, Boolean> savePanel;
    private boolean functionsBeside;
    private int primaryWidth;
    private final Consumer<String> command;

    public CalculatorKeypad(
            Context context,
            Consumer<String> command,
            boolean expanded,
            BiConsumer<Boolean, Boolean> savePanel) {
        super(context);
        this.command = command;
        this.expanded = expanded;
        this.savePanel = savePanel;
        colors = new ThemeColors(context);
        numbers = new LocaleNumbers(context.getResources().getConfiguration().getLocales().get(0));
        controls = new LinearLayout(context);
        controls.addView(
                button(Command.UNDO, getContext().getString(R.string.undo)),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        functions = button("FUNCTIONS", "f()");
        complements = button("COMPLEMENTS", "f⁻¹()");
        functions.setOnClickListener(v -> togglePanel(false));
        complements.setOnClickListener(v -> togglePanel(true));
        controls.addView(functions, new LinearLayout.LayoutParams(0, dp(44), 2));
        controls.addView(complements, new LinearLayout.LayoutParams(0, dp(44), 2));
        addView(controls);
        addRows(
                new String[][] {
                    {"sin", "cos", "tan", "1/x", "!"},
                    {"ln", "log10", "√", "floor", "Factor"},
                    {"π", "e", "φ", "xʸ", "rem"}
                },
                scientificRows);
        addRows(new String[][] {{"CLEAR", "BACK", "SWAP", "÷"}}, primaryRows);
        addRows(
                new String[][] {
                    {"7", "8", "9", "×"},
                    {"4", "5", "6", "−"},
                    {"1", "2", "3", "+"},
                    {".", "0", "±", Command.ENTER}
                },
                primaryRows);
        setEditing(false);
        updateConstant();
        updateExpansion();
    }

    private String effectiveKey(String key) {
        if (key.equals("π") && tau) return "τ";
        if (!inverse) return key;
        switch (key) {
            case "sin":
                return "asin";
            case "cos":
                return "acos";
            case "tan":
                return "atan";
            case "ln":
                return "eˣ";
            case "√":
                return "x²";
            case "xʸ":
                return "root";
            case "rem":
                return "mod";
            case "floor":
                return "ceil";
            case "log10":
                return "10ˣ";
            default:
                return key;
        }
    }

    private CharSequence functionLabel(String key) {
        switch (key) {
            case Command.INVERSE:
                return "INV";
            case "xʸ":
                return "yˣ";
            case "root":
                return "ˣ√y";
            case "!":
                return "x!";
            case "log10":
                SpannableString logarithm = new SpannableString("log10");
                logarithm.setSpan(
                        new RelativeSizeSpan(.7f), 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                logarithm.setSpan(new SubscriptSpan(), 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return logarithm;
            case "floor":
                return "⌊x⌋";
            case "ceil":
                return "⌈x⌉";
            default:
                return key;
        }
    }

    private String functionDescription(String key) {
        switch (key) {
            case "floor":
                return getContext().getString(R.string.floor_description);
            case "ceil":
                return getContext().getString(R.string.ceil_description);
            case "log10":
                return getContext().getString(R.string.log10_description);
            case "10ˣ":
                return getContext().getString(R.string.ten_power_description);
            case "mod":
                return getContext().getString(R.string.modulo_description);
            case "rem":
                return getContext().getString(R.string.remainder_description);
            default:
                return key;
        }
    }

    public void setInverse(boolean active) {
        inverse = active;
        for (Map.Entry<String, Button> item : inverseButtons.entrySet()) {
            String key = item.getKey();
            Button button = item.getValue();
            String effective = effectiveKey(key);
            button.setText(functionLabel(effective));
            button.setContentDescription(
                    key.equals(Command.INVERSE)
                            ? getContext()
                                    .getString(active ? R.string.inverse_on : R.string.inverse_off)
                            : effective.equals("root")
                                    ? getContext().getString(R.string.root_description)
                                    : functionDescription(effective));
            if (key.equals(Command.INVERSE)) {
                button.setTextColor(active ? colors.accent : colors.text);
                if (Build.VERSION.SDK_INT >= 30)
                    button.setStateDescription(
                            active
                                    ? getContext().getString(R.string.on)
                                    : getContext().getString(R.string.off));
            }
        }
        updateSelectionFunctions();
        updateExpansion();
    }

    public void setTauPreference(boolean enabled, Consumer<Boolean> save) {
        tau = enabled;
        saveTau = save;
        updateConstant();
    }

    private void updateConstant() {
        piButton.setText(tau ? "τ" : "π");
        piButton.setContentDescription(
                getContext()
                        .getString(
                                tau
                                        ? R.string.tau_toggle_description
                                        : R.string.pi_toggle_description));
        requestLayout();
    }

    public void setNumberFormat(LocaleNumbers format) {
        if (numbers == format) return;
        numbers = format;
        for (Map.Entry<String, Button> item : numberButtons.entrySet())
            item.getValue().setText(numbers.entry(item.getKey()));
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
        for (Map.Entry<String, Button> item : numberButtons.entrySet()) {
            String digit = item.getKey();
            if (digit.equals(".")) {
                item.getValue().setLongClickable(editing);
                item.getValue()
                        .setContentDescription(
                                getContext()
                                        .getString(
                                                editing
                                                        ? R.string.decimal_exponent_description
                                                        : R.string.decimal_separator));
            }
            if (digit.matches("[0-9]"))
                item.getValue()
                        .setContentDescription(
                                editing
                                        ? numbers.entry(digit)
                                        : getContext()
                                                .getString(
                                                        R.string.digit_pick_description,
                                                        numbers.entry(digit),
                                                        digit.equals("0")
                                                                ? 10
                                                                : Integer.parseInt(digit)));
        }
        updateModeKeys();
    }

    public void setSelecting(boolean selecting, boolean allSelected) {
        this.selecting = selecting;
        this.allSelected = allSelected;
        updateModeKeys();
    }

    private void updateModeKeys() {
        clearButton.setText(
                selecting ? "" : editing ? "CE" : getContext().getString(R.string.clear));
        clearButton.setContentDescription(
                getContext()
                        .getString(
                                selecting
                                        ? R.string.select_all_description
                                        : editing ? R.string.clear_entry : R.string.clear_stack));
        setButtonIcon(clearButton, selecting ? R.drawable.ic_select_all : 0);
        deleteButton.setText(!selecting && editing ? "⌫" : "");
        setButtonIcon(deleteButton, !selecting && editing ? 0 : R.drawable.ic_delete);
        deleteButton.setContentDescription(
                getContext()
                        .getString(
                                selecting
                                        ? R.string.drop_selected
                                        : editing
                                                ? R.string.backspace
                                                : R.string.drop_roll_description));
        deleteButton.setLongClickable(!selecting);
        swapButton.setText("⇅");
        swapButton.setEnabled(!selecting);
        swapButton.setAlpha(selecting ? .38f : 1f);
        setButtonIcon(swapButton, 0);
        swapButton.setContentDescription(
                getContext()
                        .getString(selecting ? R.string.pick_selected : R.string.swap_description));
        swapButton.setLongClickable(!selecting);
        sumButton.setText(selecting ? "Σ" : "+");
        sumButton.setContentDescription(
                selecting ? getContext().getString(R.string.sum_selected) : "+");
        productButton.setText(selecting ? "Π" : "×");
        productButton.setContentDescription(
                selecting ? getContext().getString(R.string.product_selected) : "×");
        enterButton.setText(getContext().getString(selecting ? R.string.pick : R.string.enter));
        enterButton.setContentDescription(
                getContext()
                        .getString(
                                selecting ? R.string.pick_selected : R.string.enter_description));
        enterButton.setLongClickable(!selecting);
        updateSelectionFunctions();
        updateUndoButton();
    }

    private void updateSelectionFunctions() {
        for (LinearLayout row : scientificRows) {
            for (int i = 0; i < row.getChildCount(); i++) {
                Button button = (Button) ((FrameLayout) row.getChildAt(i)).getChildAt(0);
                String key = effectiveKey((String) button.getTag());
                Operation operation = Operation.find(key);
                button.setEnabled(
                        !selecting
                                || !key.equals("Factor")
                                        && (operation == null || operation.arity != 2));
            }
        }
        for (LinearLayout row : scientificRows)
            for (int i = 0; i < row.getChildCount(); i++) {
                Button button = (Button) ((FrameLayout) row.getChildAt(i)).getChildAt(0);
                button.setAlpha(button.isEnabled() ? 1f : .38f);
            }
        for (LinearLayout row : primaryRows)
            for (int i = 0; i < row.getChildCount(); i++) {
                Button button = (Button) ((FrameLayout) row.getChildAt(i)).getChildAt(0);
                String key = (String) button.getTag();
                Operation operation = Operation.find(key);
                if (operation != null && operation.arity == 2)
                    button.setEnabled(!selecting || key.equals("+") || key.equals("×"));
                if (operation != null && operation.arity == 2)
                    button.setAlpha(button.isEnabled() ? 1f : .38f);
            }
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
        updateUndoButton();
    }

    private void updateUndoButton() {
        undoButton.setText(
                calculating
                        ? getContext().getString(R.string.cancel)
                        : selecting ? "" : getContext().getString(R.string.undo));
        setButtonIcon(undoButton, selecting && !calculating ? R.drawable.ic_select_none : 0);
        undoButton.setContentDescription(
                getContext()
                        .getString(
                                calculating
                                        ? R.string.cancel_calculation
                                        : selecting
                                                ? R.string.select_none_description
                                                : R.string.undo_description));
        undoButton.setLongClickable(!selecting || calculating);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private Button button(String key, CharSequence label) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setTag(key);
        registerButton(key, button);
        labelButton(key, label, button);
        styleKey(key, button);
        button.setContentDescription(keyDescription(key));
        button.setOnClickListener(
                v -> command.accept(key.equals("CLEAR") ? "SMART_CLEAR" : effectiveKey(key)));
        boolean longPressKey =
                RpnCalculator.isEntryKey(key)
                        || switch (key) {
                            case "π", Command.UNDO, "BACK", "SWAP", Command.ENTER -> true;
                            default -> false;
                        };
        if (longPressKey) button.setOnLongClickListener(v -> longPress(key));
        return button;
    }

    private void registerButton(String key, Button b) {
        if (key.equals("CLEAR")) clearButton = b;
        if (key.equals("SWAP")) swapButton = b;
        if (key.equals("+")) sumButton = b;
        if (key.equals("×")) productButton = b;
        if (key.equals(Command.UNDO)) undoButton = b;
        if (key.equals(Command.ENTER)) enterButton = b;
        if (key.equals("π")) piButton = b;
        if (Arrays.asList(
                        Command.INVERSE,
                        "sin",
                        "cos",
                        "tan",
                        "ln",
                        "√",
                        "xʸ",
                        "log10",
                        "rem",
                        "floor")
                .contains(key)) inverseButtons.put(key, b);
    }

    private void labelButton(String key, CharSequence label, Button b) {
        if (key.equals("xʸ")
                || key.equals(Command.INVERSE)
                || key.equals("!")
                || key.equals("log10")
                || key.equals("floor")) label = functionLabel(key);
        if (key.equals("Factor")) label = getContext().getString(R.string.factor);
        if (key.equals("1/x")) {
            SpannableString fraction = new SpannableString("1/x");
            fraction.setSpan(new ReciprocalSpan(), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setText(fraction);
        } else b.setText(RpnCalculator.isEntryKey(key) ? numbers.entry(label.toString()) : label);
        if (RpnCalculator.isEntryKey(key)) {
            b.setFontFeatureSettings("tnum");
            numberButtons.put(key, b);
        }
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
        boolean number = RpnCalculator.isEntryKey(key);
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

    private String keyDescription(String key) {
        int resource =
                switch (key) {
                    case "SWAP" -> R.string.swap_description;
                    case Command.INVERSE -> R.string.inverse_off;
                    case "!" -> R.string.factorial_description;
                    case "Factor" -> R.string.factor_description;
                    case "floor" -> R.string.floor_description;
                    case "log10" -> R.string.log10_description;
                    case "φ" -> R.string.phi_description;
                    case "mod" -> R.string.modulo_description;
                    case "rem" -> R.string.remainder_description;
                    case Command.UNDO -> R.string.undo_description;
                    case Command.ENTER -> R.string.enter_description;
                    case "." -> R.string.decimal_separator;
                    case "BACK" -> R.string.backspace;
                    case "CLEAR" -> R.string.clear_stack;
                    case "CE" -> R.string.clear_entry;
                    case "xʸ" -> R.string.power_description;
                    case "eˣ" -> R.string.exp_description;
                    case "e" -> R.string.e_description;
                    case "π" -> R.string.pi_description;
                    case "1/x" -> R.string.reciprocal_description;
                    default -> 0;
                };
        return resource == 0 ? key : getContext().getString(resource);
    }

    private boolean longPress(String key) {
        switch (key) {
            case "π":
                tau = !tau;
                updateConstant();
                saveTau.accept(tau);
                break;
            case ".":
                command.accept("HOLD_DECIMAL");
                break;
            case Command.UNDO:
                if (calculating || !selecting)
                    command.accept(calculating ? Command.CANCEL : Command.REDO);
                break;
            case "BACK":
                if (!selecting) command.accept("HOLD_DROP");
                break;
            case "SWAP":
                if (!selecting) command.accept("ROLL_UP");
                break;
            case Command.ENTER:
                command.accept(Command.PASTE);
                break;
            default:
                command.accept("HOLD_DIGIT:" + key);
        }
        return true;
    }

    private void addRows(String[][] keys, List<LinearLayout> rows) {
        for (String[] entries : keys) {
            LinearLayout row = new LinearLayout(getContext());
            for (String key : entries) {
                String label =
                        key.equals("BACK")
                                ? "⌫"
                                : key.equals("CLEAR")
                                        ? getContext().getString(R.string.clear)
                                        : key.equals(Command.UNDO)
                                                ? getContext().getString(R.string.undo)
                                                : key.equals("SWAP")
                                                        ? getContext().getString(R.string.swap)
                                                        : key.equals(Command.ENTER)
                                                                ? getContext()
                                                                        .getString(R.string.enter)
                                                                : key.equals("DUP")
                                                                        ? getContext()
                                                                                .getString(
                                                                                        R.string
                                                                                                .duplicate)
                                                                        : key;
                FrameLayout cell = new FrameLayout(getContext());
                Button b = button(key, label);
                if (key.equals("BACK")) deleteButton = b;
                cell.addView(b, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
                row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
            }
            rows.add(row);
            addView(row);
        }
    }

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
        functions.setContentDescription(
                getContext()
                        .getString(
                                expanded && !inverse
                                        ? R.string.close_functions
                                        : R.string.open_functions));
        complements.setContentDescription(
                getContext()
                        .getString(
                                expanded && inverse
                                        ? R.string.close_complements
                                        : R.string.open_complements));
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

    private int preferredLabelSize(String key) {
        if (RpnCalculator.isEntryKey(key)) return 26;
        return switch (key) {
            case "+", "−", "×", "÷", "±", "CLEAR", "CE", "BACK" -> 26;
            case "SWAP" -> selecting ? 16 : 26;
            case "π", "τ", "e", "φ", "√" -> 24;
            case "1/x", "eˣ", "xʸ", "root", "x²", "!", "floor", "ceil", "10ˣ" -> 22;
            case "sin", "cos", "tan", "ln", "log10", "asin", "acos", "atan", "rem", "mod" -> 20;
            case Command.ENTER -> 18;
            default -> 16;
        };
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
        int rows = 5 + (expanded && !functionsBeside ? 3 : 0);
        for (int i = 0; i < primaryRows.size(); i++) {
            int index = i + (expanded && !functionsBeside ? 3 : 0);
            measureRow(
                    primaryRows.get(i),
                    digitsWidth,
                    body * (index + 1) / rows - body * index / rows,
                    !expanded);
        }
        if (expanded)
            for (int i = 0; i < scientificRows.size(); i++) {
                int functionWidth = functionsBeside ? width - digitsWidth - dp(12) : width;
                int rowHeight =
                        functionsBeside ? body / 5 : body * (i + 1) / rows - body * i / rows;
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
        int cellWidth = width / row.getChildCount();
        for (int c = 0; c < row.getChildCount(); c++) {
            Button button = (Button) ((FrameLayout) row.getChildAt(c)).getChildAt(0);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
            lp.width = Math.max(1, cellWidth - gap);
            lp.height = Math.max(1, height - gap);
            if (round) lp.width = lp.height = Math.min(lp.width, lp.height);
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
        for (LinearLayout row : primaryRows) {
            row.layout(digitsX, y, getWidth(), y + row.getMeasuredHeight());
            y += row.getMeasuredHeight();
        }
    }
}
