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
            clearButton,
            piButton,
            swapButton,
            sumButton,
            productButton;
    private boolean tau;
    private Consumer<Boolean> saveTau = enabled -> {};
    private boolean calculating, editing, selecting;
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

    public void setSelecting(boolean selecting) {
        this.selecting = selecting;
        updateModeKeys();
    }

    private void updateModeKeys() {
        clearButton.setText(
                selecting ? "" : editing ? "CE" : getContext().getString(R.string.clear));
        clearButton.setContentDescription(
                getContext()
                        .getString(
                                selecting
                                        ? R.string.copy_selected
                                        : editing ? R.string.clear_entry : R.string.clear_stack));
        android.graphics.drawable.Drawable copy =
                selecting ? getContext().getDrawable(R.drawable.ic_copy) : null;
        if (copy != null) copy.setTint(colors.text);
        clearButton.setCompoundDrawablesRelativeWithIntrinsicBounds(copy, null, null, null);
        deleteButton.setText(!selecting && editing ? "⌫" : getContext().getString(R.string.drop));
        deleteButton.setContentDescription(
                getContext()
                        .getString(
                                selecting
                                        ? R.string.drop_selected
                                        : editing
                                                ? R.string.backspace
                                                : R.string.drop_roll_description));
        deleteButton.setLongClickable(!selecting);
        swapButton.setText(getContext().getString(selecting ? R.string.pick : R.string.swap));
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
    }

    public void setCalculating(boolean active) {
        if (calculating == active) return;
        calculating = active;
        undoButton.setText(
                active
                        ? getContext().getString(R.string.cancel)
                        : getContext().getString(R.string.undo));
        undoButton.setContentDescription(
                active
                        ? getContext().getString(R.string.cancel_calculation)
                        : getContext().getString(R.string.undo_description));
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private Button button(String key, CharSequence label) {
        Button b = new Button(getContext());
        if (key.equals("CLEAR")) clearButton = b;
        if (key.equals("SWAP")) swapButton = b;
        if (key.equals("+")) sumButton = b;
        if (key.equals("×")) productButton = b;
        if (key.equals(Command.UNDO)) undoButton = b;
        if (key.equals("π")) piButton = b;
        b.setAllCaps(false);
        b.setTag(key);
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
        } else b.setText(key.matches("[0-9.]") ? numbers.entry(label.toString()) : label);
        if (key.matches("[0-9.]")) {
            b.setFontFeatureSettings("tnum");
            numberButtons.put(key, b);
        }
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
        boolean number = key.matches("[0-9.]");
        int color =
                key.equals(Command.ENTER)
                        ? colors.enterKey
                        : number ? colors.numberKey : colors.functionKey;
        if (key.equals(Command.ENTER)) b.setTextColor(colors.enterText);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(100));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple), shape, null));
        String description;
        switch (key) {
            case "SWAP":
                description = getContext().getString(R.string.swap_description);
                break;
            case Command.INVERSE:
                description = getContext().getString(R.string.inverse_off);
                break;
            case "!":
                description = getContext().getString(R.string.factorial_description);
                break;
            case "Factor":
                description = getContext().getString(R.string.factor_description);
                break;
            case "floor":
                description = getContext().getString(R.string.floor_description);
                break;
            case "log10":
                description = getContext().getString(R.string.log10_description);
                break;
            case "φ":
                description = getContext().getString(R.string.phi_description);
                break;
            case "mod":
                description = getContext().getString(R.string.modulo_description);
                break;
            case "rem":
                description = getContext().getString(R.string.remainder_description);
                break;
            case Command.UNDO:
                description = getContext().getString(R.string.undo_description);
                break;
            case Command.ENTER:
                description = getContext().getString(R.string.enter_description);
                break;
            case ".":
                description = getContext().getString(R.string.decimal_separator);
                break;
            case "BACK":
                description = getContext().getString(R.string.backspace);
                break;
            case "CLEAR":
                description = getContext().getString(R.string.clear_stack);
                break;
            case "CE":
                description = getContext().getString(R.string.clear_entry);
                break;
            case "xʸ":
                description = getContext().getString(R.string.power_description);
                break;
            case "eˣ":
                description = getContext().getString(R.string.exp_description);
                break;
            case "e":
                description = getContext().getString(R.string.e_description);
                break;
            case "π":
                description = getContext().getString(R.string.pi_description);
                break;
            case "1/x":
                description = getContext().getString(R.string.reciprocal_description);
                break;
            default:
                description = key;
        }
        b.setContentDescription(description);
        b.setOnClickListener(
                v ->
                        command.accept(
                                key.equals(Command.UNDO) && calculating
                                        ? Command.CANCEL
                                        : key.equals("CLEAR") ? "SMART_CLEAR" : effectiveKey(key)));
        if (key.equals("π"))
            b.setOnLongClickListener(
                    v -> {
                        tau = !tau;
                        updateConstant();
                        saveTau.accept(tau);
                        return true;
                    });
        if (key.matches("[0-9]"))
            b.setOnLongClickListener(
                    v -> {
                        command.accept("HOLD_DIGIT:" + key);
                        return true;
                    });
        if (key.equals(Command.UNDO))
            b.setOnLongClickListener(
                    v -> {
                        command.accept(calculating ? Command.CANCEL : Command.REDO);
                        return true;
                    });
        if (key.equals("BACK"))
            b.setOnLongClickListener(
                    v -> {
                        if (!selecting) command.accept("HOLD_DROP");
                        return true;
                    });
        if (key.equals("SWAP"))
            b.setOnLongClickListener(
                    v -> {
                        if (!selecting) command.accept("ROLL_UP");
                        return true;
                    });
        if (key.equals(Command.ENTER))
            b.setOnLongClickListener(
                    v -> {
                        command.accept(Command.PASTE);
                        return true;
                    });
        return b;
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

    private void fitLabel(Button button, int width, int height) {
        int minimum =
                Math.max(1, Math.round(10 * getResources().getDisplayMetrics().scaledDensity));
        String key = effectiveKey((String) button.getTag());
        int preferred = 16;
        if (key.matches("[0-9.]")) preferred = 26;
        else if (key.matches("[+−×÷±]")) preferred = 26;
        else if (key.equals("BACK") && button.getText().toString().equals("⌫")) preferred = 24;
        else if (key.equals("π")
                || key.equals("τ")
                || key.equals("e")
                || key.equals("φ")
                || key.equals("√")) preferred = 24;
        else if (key.equals("1/x")
                || key.equals("eˣ")
                || key.equals("xʸ")
                || key.equals("root")
                || key.equals("x²")
                || key.equals("!")
                || key.equals("floor")
                || key.equals("ceil")
                || key.equals("10ˣ")) preferred = 22;
        else if (key.equals("sin")
                || key.equals("cos")
                || key.equals("tan")
                || key.equals("ln")
                || key.equals("log10")
                || key.equals("asin")
                || key.equals("acos")
                || key.equals("atan")
                || key.equals("rem")
                || key.equals("mod")) preferred = 20;
        else if (key.equals(Command.ENTER)) preferred = 18;
        int preferredPixels =
                Math.round(preferred * getResources().getDisplayMetrics().scaledDensity);
        // Grow only up to a consistent type size; smaller keys may shrink to fit.
        int maximum =
                Math.max(
                        minimum,
                        Math.min(preferredPixels, Math.round(Math.min(width, height) * .55f)));
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
