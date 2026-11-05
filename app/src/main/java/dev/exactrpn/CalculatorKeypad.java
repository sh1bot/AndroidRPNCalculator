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
import android.text.style.SuperscriptSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Round primary keys become shorter capsules to make room for scientific functions. */
public final class CalculatorKeypad extends LinearLayout {
    private final List<LinearLayout> primaryRows = new ArrayList<>();
    private final List<LinearLayout> scientificRows = new ArrayList<>();
    private final Button expand;
    private final ThemeColors colors;
    private LocaleNumbers numbers;
    private final Map<String,Button> numberButtons=new HashMap<>();
    private final LinearLayout controls;
    private Button deleteButton, undoButton, clearButton;
    private boolean calculating;
    private boolean expanded, inverse;
    private final Map<String,Button> inverseButtons=new HashMap<>();
    private final Consumer<Boolean> saveExpanded;
    private final Consumer<String> command;

    public CalculatorKeypad(Context context, Consumer<String> command, boolean expanded, Consumer<Boolean> saveExpanded) {
        super(context); this.command = command; this.expanded = expanded; this.saveExpanded = saveExpanded;
        colors=new ThemeColors(context);
        numbers = new LocaleNumbers(context.getResources().getConfiguration().getLocales().get(0));
        setOrientation(VERTICAL);
        controls = new LinearLayout(context);
        controls.addView(button("CLEAR", "Clear"), new LinearLayout.LayoutParams(0,dp(44),1));
        expand = button("", "");
        expand.setOnClickListener(v -> { this.expanded = !this.expanded; updateExpansion(); saveExpanded.accept(this.expanded); });
        controls.addView(expand,new LinearLayout.LayoutParams(0,dp(44),3));
        addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        addRows(new String[][] {{"INVERSE","sin","cos","tan","1/x"},{"ln","√","xʸ","π","e"},{"rem","!","Factor","ROLL_UP","ROLL_DOWN"}}, scientificRows);
        addRows(new String[][] {{"UNDO","SWAP","±","÷"}}, primaryRows);
        addRows(new String[][] {{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{".","0","BACK","ENTER"}}, primaryRows);
        setEditing(false);
        updateExpansion();
    }
    private String effectiveKey(String key) {
        if(!inverse) return key;
        switch(key) {
            case "sin": return "asin"; case "cos": return "acos"; case "tan": return "atan";
            case "ln": return "eˣ"; case "√": return "x²"; case "xʸ": return "root"; case "rem": return "mod";
            default: return key;
        }
    }
    private String functionLabel(String key) {
        switch(key) { case "INVERSE": return "INV"; case "xʸ": return "yˣ"; case "root": return "ˣ√y"; case "!": return "x!"; case "ROLL_UP": return "Roll ↑"; case "ROLL_DOWN": return "Roll ↓"; default: return key; }
    }
    public void setInverse(boolean active) {
        inverse=active;
        for(Map.Entry<String,Button> item:inverseButtons.entrySet()) {
            String key=item.getKey(); Button button=item.getValue(); String effective=effectiveKey(key);
            button.setText(functionLabel(effective));
            button.setContentDescription(key.equals("INVERSE") ? "Inverse functions " + (active ? "on" : "off") : effective.equals("root") ? "Xth root of Y" : effective);
            if(key.equals("INVERSE")) {
                button.setTextColor(active ? colors.accent : colors.text);
                if(Build.VERSION.SDK_INT>=30) button.setStateDescription(active ? "On" : "Off");
            }
        }
        requestLayout();
    }
    public void setNumberFormat(LocaleNumbers format) {
        if(numbers==format) return;
        numbers=format;
        for(Map.Entry<String,Button> item:numberButtons.entrySet()) item.getValue().setText(numbers.entry(item.getKey()));
    }
    public void setEditing(boolean editing) {
        clearButton.setText(editing ? "CE" : "Clear");
        clearButton.setContentDescription(editing ? "Clear entry" : "Clear stack");
        deleteButton.setText(editing ? "⌫" : "Drop");
        deleteButton.setContentDescription(editing ? "Backspace" : "Drop X");
    }
    public void setCalculating(boolean active) {
        if (calculating == active) return;
        calculating = active;
        undoButton.setText(active ? "Cancel" : "Undo");
        undoButton.setContentDescription(active ? "Cancel calculation" : "Undo; long press to redo");
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String key, String label) {
        Button b = new Button(getContext());
        if(key.equals("CLEAR")) clearButton=b; b.setAllCaps(false); b.setTag(key);
        if(Arrays.asList("INVERSE","sin","cos","tan","ln","√","xʸ","rem").contains(key)) inverseButtons.put(key,b);
        if (key.equals("xʸ") || key.equals("INVERSE") || key.equals("!") || key.equals("ROLL_UP") || key.equals("ROLL_DOWN")) label=functionLabel(key);
        if (key.equals("1/x")) {
            // Keep the smaller x on the normal baseline, rather than below the slash.
            SpannableString fraction = new SpannableString("1/x");
            fraction.setSpan(new SuperscriptSpan(),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fraction.setSpan(new RelativeSizeSpan(.7f),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fraction.setSpan(new RelativeSizeSpan(.7f),2,3,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setText(fraction);
        } else b.setText(key.matches("[0-9.]") ? numbers.entry(label) : label);
        if(key.matches("[0-9.]")) { b.setFontFeatureSettings("tnum"); numberButtons.put(key,b); }
        b.setTextColor(colors.text);
        b.setSingleLine(true); b.setHorizontallyScrolling(false); b.setIncludeFontPadding(false);
        b.setAutoSizeTextTypeUniformWithConfiguration(10,26,1,TypedValue.COMPLEX_UNIT_SP);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(2),0,dp(2),0); b.setElevation(0); b.setStateListAnimator(null);
        boolean number = key.matches("[0-9.]");
        int color = key.equals("ENTER") ? colors.enterKey : number ? colors.numberKey : colors.functionKey;
        if (key.equals("ENTER")) b.setTextColor(colors.enterText);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(100));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple),shape,null));
        String description;
        switch (key) {
            case "ROLL_UP": description="Roll stack up; top displayed value becomes X"; break;
            case "ROLL_DOWN": description="Roll stack down; X moves to the top displayed row"; break;
            case "INVERSE": description="Inverse functions off"; break;
            case "!": description="Factorial of X"; break;
            case "Factor": description="Replace X with prime factors, smallest first"; break;
            case "rem": description="Y remainder X"; break;
            case "UNDO": description="Undo; long press to redo"; break;
            case "ENTER": description="Enter; long press to paste"; break;
            case ".": description="Decimal separator"; break;
            case "BACK": description="Backspace"; break;
            case "CLEAR": description="Clear stack"; break;
            case "CE": description="Clear entry"; break;
            case "xʸ": description="Y to the power of X"; break;
            case "eˣ": description="e to the power of X"; break;
            case "e": description="Euler’s number"; break;
            case "π": description="Pi"; break;
            case "1/x": description="Reciprocal of X"; break;
            default: description=key;
        }
        b.setContentDescription(description);
        b.setOnClickListener(v -> command.accept(key.equals("UNDO") && calculating ? "CANCEL" : key.equals("CLEAR") ? "SMART_CLEAR" : effectiveKey(key)));
        if (key.equals("UNDO")) b.setOnLongClickListener(v -> { command.accept(calculating ? "CANCEL" : "REDO"); return true; });
        if (key.equals("ENTER")) b.setOnLongClickListener(v -> { command.accept("PASTE"); return true; });
        return b;
    }
    private void addRows(String[][] keys, List<LinearLayout> rows) {
        for (String[] entries : keys) {
            LinearLayout row = new LinearLayout(getContext());
            for (String key : entries) {
                String label = key.equals("BACK") ? "⌫" : key.equals("CLEAR") ? "Clear" : key.equals("UNDO") ? "Undo" : key.equals("SWAP") ? "Swap" : key.equals("ENTER") ? "Enter" : key.equals("DUP") ? "Dup" : key;
                FrameLayout cell = new FrameLayout(getContext());
                Button b = button(key,label);
                if (key.equals("BACK")) deleteButton = b;
                if (key.equals("UNDO")) undoButton = b;
                cell.addView(b,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
                row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
            }
            rows.add(row); addView(row,new LinearLayout.LayoutParams(-1,dp(56)));
        }
    }
    private void updateExpansion() {
        for (LinearLayout row : scientificRows) row.setVisibility(expanded ? VISIBLE : GONE);
        expand.setText(expanded ? "Functions ▴" : "Functions ▾");
        expand.setContentDescription(expanded ? "Hide scientific functions" : "Show scientific functions");
        if (Build.VERSION.SDK_INT >= 30) expand.setStateDescription(expanded ? "Expanded" : "Collapsed");
        requestLayout();
    }
    private void fitLabel(Button button, int width, int height) {
        int minimum = Math.max(1,Math.round(10*getResources().getDisplayMetrics().scaledDensity));
        String key=effectiveKey((String)button.getTag());
        int preferred=16;
        if(key.matches("[0-9.]")) preferred=26;
        else if(key.matches("[+−×÷±]")) preferred=26;
        else if(key.equals("BACK") && button.getText().toString().equals("⌫")) preferred=24;
        else if(key.equals("π") || key.equals("e") || key.equals("√")) preferred=24;
        else if(key.equals("1/x") || key.equals("eˣ") || key.equals("xʸ") || key.equals("root") || key.equals("x²") || key.equals("!")) preferred=22;
        else if(key.equals("sin") || key.equals("cos") || key.equals("tan") || key.equals("ln") || key.equals("asin") || key.equals("acos") || key.equals("atan") || key.equals("rem") || key.equals("mod")) preferred=20;
        else if(key.equals("ENTER")) preferred=18;
        int preferredPixels=Math.round(preferred*getResources().getDisplayMetrics().scaledDensity);
        // Grow only up to a consistent type size; smaller keys may shrink to fit.
        int maximum = Math.max(minimum,Math.min(preferredPixels,Math.round(Math.min(width,height)*.55f)));
        if (button.getAutoSizeMaxTextSize()!=maximum || button.getAutoSizeMinTextSize()!=minimum)
            button.setAutoSizeTextTypeUniformWithConfiguration(minimum,maximum,1,TypedValue.COMPLEX_UNIT_PX);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.max(1,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
        int columns = 1;
        for (LinearLayout row : primaryRows) columns = Math.max(columns,row.getChildCount());
        int naturalCell = width/columns;
        int naturalHeight = dp(48)+naturalCell*primaryRows.size();
        int available = MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED
            ? naturalHeight+dp(112) : MeasureSpec.getSize(heightSpec);
        // Reserve room for the stack. Both modes get the SAME footprint, derived
        // from the primary grid and actual parent constraints, not screen size.
        int height = Math.min(naturalHeight,Math.max(0,available-Math.min(dp(112),available/3)));
        int controlHeight = Math.min(dp(48),height/Math.max(1,primaryRows.size()+1));
        controls.getLayoutParams().height = controlHeight;
        int controlGap = Math.min(dp(8),controlHeight/6);
        for (int i=0;i<controls.getChildCount();i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)controls.getChildAt(i).getLayoutParams();
            lp.height = Math.max(1,controlHeight-controlGap);
            lp.topMargin = controlGap/2; lp.bottomMargin = controlGap-controlGap/2;
            lp.leftMargin = controlGap/2; lp.rightMargin = controlGap/2;
            fitLabel((Button)controls.getChildAt(i),width/controls.getChildCount(),lp.height);
        }
        List<LinearLayout> activeRows = new ArrayList<>();
        for(int i=1;i<getChildCount();i++) {
            View row=getChildAt(i);
            if(row.getVisibility()!=GONE) activeRows.add((LinearLayout)row);
        }
        int bodyHeight = Math.max(0,height-controlHeight);
        int count = Math.max(1,activeRows.size());
        for (int r=0;r<activeRows.size();r++) {
            LinearLayout row = activeRows.get(r);
            int rowHeight = bodyHeight/count+(r<bodyHeight%count ? 1 : 0);
            row.getLayoutParams().height = rowHeight;
            int cellWidth = width/Math.max(1,row.getChildCount());
            int gap = Math.min(dp(8),rowHeight/6);
            boolean round = !expanded && primaryRows.contains(row);
            for (int c=0;c<row.getChildCount();c++) {
                FrameLayout cell = (FrameLayout)row.getChildAt(c);
                Button button = (Button)cell.getChildAt(0);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)button.getLayoutParams();
                lp.height = Math.max(1,rowHeight-gap);
                lp.width = Math.max(1,cellWidth-gap);
                if (round) lp.width = lp.height = Math.min(lp.width,lp.height);
                fitLabel(button,lp.width,lp.height);
            }
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}



