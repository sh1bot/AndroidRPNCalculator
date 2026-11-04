// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Round primary keys become shorter capsules to make room for scientific functions. */
public final class CalculatorKeypad extends LinearLayout {
    private final List<LinearLayout> primaryRows = new ArrayList<>();
    private final List<LinearLayout> scientificRows = new ArrayList<>();
    private final List<Button> primaryButtons = new ArrayList<>();
    private final List<Button> scientificButtons = new ArrayList<>();
    private final Button expand;
    private boolean expanded;
    private final Consumer<Boolean> saveExpanded;
    private final Consumer<String> command;

    public CalculatorKeypad(Context context, Consumer<String> command, boolean expanded, Consumer<Boolean> saveExpanded) {
        super(context); this.command = command; this.expanded = expanded; this.saveExpanded = saveExpanded;
        setOrientation(VERTICAL);
        LinearLayout controls = new LinearLayout(context);
        controls.addView(button("SWAP", "Swap"), new LinearLayout.LayoutParams(0,dp(44),1));
        controls.addView(button("DROP", "Drop"), new LinearLayout.LayoutParams(0,dp(44),1));
        expand = button("", "");
        expand.setOnClickListener(v -> { this.expanded = !this.expanded; updateExpansion(); saveExpanded.accept(this.expanded); });
        controls.addView(expand,new LinearLayout.LayoutParams(0,dp(44),2));
        addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        addRows(new String[][] {{"sin","cos","tan","π"},{"√","ln","eˣ","e"},{"1/x","xʸ","DUP","CE"}}, scientificRows, scientificButtons);
        addRows(new String[][] {{"CLEAR","±","BACK","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"UNDO","0",".","ENTER"}}, primaryRows, primaryButtons);
        updateExpansion();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String key, String label) {
        Button b = new Button(getContext()); b.setAllCaps(false); b.setText(label);
        b.setTextSize(label.length()>3 ? 14 : 21); b.setTextColor(Color.rgb(229,237,245));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(2),0,dp(2),0); b.setElevation(0); b.setStateListAnimator(null);
        boolean number = key.matches("[0-9.]");
        int color = key.equals("ENTER") ? Color.rgb(147,201,246) : number ? Color.rgb(35,46,59) : Color.rgb(51,68,87);
        if (key.equals("ENTER")) b.setTextColor(Color.rgb(12,31,48));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(100));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(65,255,255,255)),shape,null));
        String description;
        switch (key) {
            case "BACK": description="Backspace"; break;
            case "CLEAR": description="Clear stack"; break;
            case "CE": description="Clear entry"; break;
            case "eˣ": description="e to the power of X"; break;
            case "e": description="Euler’s number"; break;
            case "π": description="Pi"; break;
            case "1/x": description="Reciprocal of X"; break;
            default: description=key;
        }
        b.setContentDescription(description); b.setOnClickListener(v -> command.accept(key));
        return b;
    }
    private void addRows(String[][] keys, List<LinearLayout> rows, List<Button> buttons) {
        for (String[] entries : keys) {
            LinearLayout row = new LinearLayout(getContext());
            for (String key : entries) {
                String label = key.equals("BACK") ? "⌫" : key.equals("CLEAR") ? "Clear" : key.equals("UNDO") ? "Undo" : key.equals("ENTER") ? "Enter" : key.equals("DUP") ? "Dup" : key;
                FrameLayout cell = new FrameLayout(getContext());
                Button b = button(key,label); buttons.add(b);
                if (key.matches("[0-9.]")) b.setTextSize(26);
                cell.addView(b,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
                row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
            }
            rows.add(row); addView(row,new LinearLayout.LayoutParams(-1,dp(56)));
        }
    }
    private void updateExpansion() {
        for (LinearLayout row : scientificRows) row.setVisibility(expanded ? VISIBLE : GONE);
        expand.setText(expanded ? "Functions ▴" : "Functions ▾"); expand.setTextSize(14);
        expand.setContentDescription(expanded ? "Hide scientific functions" : "Show scientific functions");
        if (Build.VERSION.SDK_INT >= 30) expand.setStateDescription(expanded ? "Expanded" : "Collapsed");
        requestLayout();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int cellWidth = Math.max(dp(40), MeasureSpec.getSize(widthSpec)/4);
        int budget = Math.min(dp(500),Math.round(getResources().getDisplayMetrics().heightPixels * .62f));
        if (MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED) budget = Math.min(budget,MeasureSpec.getSize(heightSpec));
        int height = expanded ? Math.min(dp(56),Math.max(dp(40),(budget-dp(48))/8))
            : Math.min(cellWidth,Math.max(dp(40),(budget-dp(48))/5));
        for (LinearLayout row : primaryRows) row.getLayoutParams().height = height;
        for (LinearLayout row : scientificRows) row.getLayoutParams().height = height;
        for (Button b : primaryButtons) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)b.getLayoutParams();
            lp.height = Math.max(dp(32),height-dp(8));
            lp.width = expanded ? Math.max(dp(32),cellWidth-dp(8)) : lp.height;
        }
        for (Button b : scientificButtons) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)b.getLayoutParams();
            lp.height = Math.max(dp(32),height-dp(8)); lp.width = Math.max(dp(32),cellWidth-dp(8));
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
