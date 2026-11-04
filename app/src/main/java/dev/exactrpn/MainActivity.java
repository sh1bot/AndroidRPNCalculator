// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import com.android.calculator2.UnifiedReal;

/** Exact values and evaluation survive rotation; views consume immutable display snapshots. */
public final class MainActivity extends Activity {
    private Session session;
    private TextView entryView, status;
    private Button notationButton, cancelButton;
    private android.window.OnBackInvokedCallback backCallback;
    private LinearLayout stackRows;
    private ScrollView scroll;
    private Display rendered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    static final class Display {
        final List<String> rows;
        final String entry;
        final ValueFormatter.Notation notation;
        Display(List<String> r, String e, ValueFormatter.Notation n) { rows = r; entry = e; notation = n; }
    }
    static final class Session {
        RpnCalculator calculator = new RpnCalculator();
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        volatile Display display;
        volatile String error = "Radians · exact values";
        final Deque<String> pending = new ArrayDeque<>();
        volatile boolean busy, cancelRequested;
        volatile long busySince;
        volatile Thread calculatingThread;
        ValueFormatter.Notation notation;
        Session(ValueFormatter.Notation n) { notation = n; display = new Display(Collections.emptyList(), "", n); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return session; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = (Session)getLastNonConfigurationInstance();
        if (session == null) session = new Session(getPreferences(0).getBoolean("engineering", false)
            ? ValueFormatter.Notation.ENGINEERING : ValueFormatter.Notation.SCIENTIFIC);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(16),dp(16),dp(16),dp(12)); root.setBackgroundColor(Color.rgb(16,23,31));
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(16)+bars.left,dp(16)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom);
            } else root.setPadding(dp(16),dp(16)+insets.getSystemWindowInsetTop(),dp(16),dp(12)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(24); title.setText("EXACT / RPN"); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        notationButton = new Button(this); notationButton.setTextSize(13);
        notationButton.setOnClickListener(v -> execute("FORMAT")); header.addView(notationButton);
        Button help = new Button(this); help.setText("?"); help.setContentDescription("Keyboard shortcuts");
        help.setMinWidth(0); help.setMinimumWidth(0);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Keyboard shortcuts")
            .setMessage("0–9 and .: number entry\n+ − * /: arithmetic\nEnter or Space: enter / duplicate X\nBackspace: edit entry\nDelete: drop X\nEscape: cancel calculation / clear entry\nCtrl+Z: undo\nCtrl+D: duplicate X\nCtrl+S: swap X and Y\nCtrl+L: clear stack\nF9: change sign\n^: Y to the power X\nr: square root · i: reciprocal\ns/c/t: sin/cos/tan\nl: natural logarithm\np: π · e: Euler’s number · E: eˣ\n\nTrig functions use radians. Click/tap calculator buttons if your emulator does not forward a key.")
            .setPositiveButton("OK",null).show());
        header.addView(help,new LinearLayout.LayoutParams(dp(40),-2));
        root.addView(header);
        LinearLayout statusRow = new LinearLayout(this); statusRow.setGravity(Gravity.CENTER_VERTICAL);
        status = text(13); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        cancelButton = new Button(this); cancelButton.setText("Cancel"); cancelButton.setTextSize(13);
        cancelButton.setOnClickListener(v -> cancelCalculation()); statusRow.addView(cancelButton);
        root.addView(statusRow);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        stackRows = new LinearLayout(this); stackRows.setOrientation(1); stackRows.setGravity(Gravity.BOTTOM);
        scroll.addView(stackRows,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        entryView = text(32); entryView.setGravity(Gravity.END); digits(entryView); entryView.setTextIsSelectable(true);
        root.addView(entryView);
        CalculatorKeypad keys = new CalculatorKeypad(this, this::execute,
            getPreferences(0).getBoolean("scientificExpanded",false),
            expanded -> getPreferences(0).edit().putBoolean("scientificExpanded",expanded).apply());
        root.addView(keys); setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        refresh();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void digits(TextView t) { t.setTypeface(Typeface.MONOSPACE); t.setFontFeatureSettings("tnum"); }
    private TextView text(int size) { TextView t = new TextView(this); t.setTextSize(size); t.setTextColor(Color.rgb(228,237,242)); t.setPadding(dp(4),dp(6),dp(4),dp(6)); return t; }
    private void execute(String key) {
        boolean start;
        synchronized (session) {
            session.pending.addLast(key);
            start = !session.busy;
            if (start) { session.cancelRequested = false; session.busySince = SystemClock.elapsedRealtime(); session.busy = true; }
        }
        refresh();
        if (start) session.worker.submit(this::drainKeys);
    }
    private void drainKeys() {
        while (true) {
            String key;
            synchronized (session) {
                if (session.cancelRequested) session.pending.clear();
                if (session.pending.isEmpty()) {
                    session.calculatingThread = null; session.busy = false;
                    Thread.interrupted();
                    return;
                }
                key = session.pending.removeFirst();
                session.calculatingThread = Thread.currentThread();
            }
            try {
                checkCancelled();
                // Private candidate includes undo; cancellation during formatting rolls back too.
                RpnCalculator candidate = session.calculator.copy();
                ValueFormatter.Notation notation = session.notation;
                if (key.equals("FORMAT")) notation = notation == ValueFormatter.Notation.SCIENTIFIC
                    ? ValueFormatter.Notation.ENGINEERING : ValueFormatter.Notation.SCIENTIFIC;
                else candidate.key(key);
                List<UnifiedReal> values = candidate.values();
                List<String> rows = new ArrayList<>();
                for (int i=0;i<values.size();i++) {
                    checkCancelled();
                    rows.add(ValueFormatter.format(values.get(i),i==values.size()-1,notation));
                }
                synchronized (session) {
                    checkCancelled();
                    session.calculator = candidate; session.notation = notation;
                    session.display = new Display(Collections.unmodifiableList(rows),candidate.entry(),notation);
                    session.error = "Radians · exact values";
                }
            } catch (RuntimeException ex) {
                session.error = session.cancelRequested || ex instanceof com.hp.creals.CR.AbortedException
                    ? "Calculation cancelled" : "Error: " + (ex.getMessage()==null ? "Undefined result" : ex.getMessage());
                synchronized (session) { session.pending.clear(); }
            } finally { Thread.interrupted(); }
        }
    }
    private void checkCancelled() {
        if (session.cancelRequested || Thread.currentThread().isInterrupted()) throw new com.hp.creals.CR.AbortedException();
    }
    private void cancelCalculation() {
        synchronized (session) {
            if (!session.busy) return;
            session.cancelRequested = true;
            if (session.calculatingThread != null) session.calculatingThread.interrupt();
        }
        refresh();
    }
    private void handleBack() { if (session.busy) cancelCalculation(); else finish(); }
    private final Runnable tick = new Runnable() { public void run() { refresh(); handler.postDelayed(this,100); } };
    private void refresh() {
        Display display = session.display;
        if (rendered != display) {
            boolean stackChanged = rendered == null || !rendered.rows.equals(display.rows);
            if (stackChanged) {
                stackRows.removeAllViews();
                if (display.rows.isEmpty()) { TextView empty = text(16); empty.setText("Stack is empty"); empty.setTextColor(Color.rgb(133,150,164)); stackRows.addView(empty); }
                for (int i=0;i<display.rows.size();i++) {
                    int depth = display.rows.size()-i;
                    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.BOTTOM);
                    TextView label = text(12); digits(label); label.setText(depth + ":");
                    label.setGravity(Gravity.START | Gravity.BOTTOM); label.setTextColor(Color.rgb(133,150,164));
                    label.setContentDescription("Stack level " + depth);
                    row.addView(label,new LinearLayout.LayoutParams(dp(46),-2));
                    TextView value = text(21); digits(value); value.setGravity(Gravity.END); value.setText(display.rows.get(i));
                    value.setTextIsSelectable(true); row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                    stackRows.addView(row);
                }
            }
            entryView.setText(display.entry.isEmpty()?"▸":display.entry);
            notationButton.setText(display.notation == ValueFormatter.Notation.ENGINEERING ? "ENG" : "SCI");
            notationButton.setContentDescription("Display notation: " + (display.notation == ValueFormatter.Notation.ENGINEERING ? "engineering" : "scientific") + ". Tap to switch.");
            getPreferences(0).edit().putBoolean("engineering",display.notation == ValueFormatter.Notation.ENGINEERING).apply();
            rendered = display;
            // A second post runs after the updated rows have been measured. Do not repeatedly
            // scroll on polling ticks: the user must still be able to inspect older entries.
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        // Keep layout fixed and avoid flashing progress during brief entry evaluations.
        boolean showProgress = session.busy && (session.cancelRequested
            || SystemClock.elapsedRealtime() - session.busySince >= 250);
        status.setText(showProgress ? (session.cancelRequested ? "Cancelling…" : "Calculating… Back to cancel") : session.error);
        cancelButton.setVisibility(showProgress ? View.VISIBLE : View.INVISIBLE);
        notationButton.setEnabled(!session.busy);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        String key = KeyboardInput.command(keyCode,event.getUnicodeChar(),event.isCtrlPressed(),event.isAltPressed(),event.isMetaPressed());
        if (key == null) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (key.equals("ESCAPE")) { if (session.busy) cancelCalculation(); else execute("CE"); }
        else execute(key);
        return true;
    }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override public void onBackPressed() { handleBack(); }
    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if (isFinishing()) { cancelCalculation(); session.worker.shutdownNow(); }
        super.onDestroy();
    }
}
