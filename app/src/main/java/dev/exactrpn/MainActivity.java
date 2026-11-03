// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import com.android.calculator2.UnifiedReal;

/** Evaluation is serialized off the main thread; rotations retain the exact objects. */
public final class MainActivity extends Activity {
    private Session session;
    private TextView stackView, entryView, status;
    private LinearLayout keys;
    private final Handler handler = new Handler(Looper.getMainLooper());
    static final class Session {
        final RpnCalculator calculator = new RpnCalculator();
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        volatile String stack = "Stack is empty", entry = "", error = "Radians · exact values";
        volatile boolean busy;
        volatile Future<?> task;
    }
    @Override public Object onRetainNonConfigurationInstance() { return session; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = (Session)getLastNonConfigurationInstance();
        if (session == null) session = new Session();
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(16,16,16,12); root.setBackgroundColor(Color.rgb(16,23,31));
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets bars = android.os.Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars()) : null;
            if (bars != null) root.setPadding(16+bars.left,16+bars.top,16+bars.right,12+bars.bottom);
            else root.setPadding(16,16+insets.getSystemWindowInsetTop(),16,12+insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = text(24); title.setText("EXACT / RPN"); root.addView(title);
        status = text(13); root.addView(status);
        ScrollView scroll = new ScrollView(this); stackView = text(21); stackView.setTextIsSelectable(true);
        stackView.setGravity(Gravity.END); scroll.addView(stackView);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        entryView = text(28); entryView.setGravity(Gravity.END); root.addView(entryView);
        keys = new LinearLayout(this); keys.setOrientation(1);
        String[][] rows = {{"UNDO","SWAP","DROP","CLEAR"},{"sin","cos","tan","π"},{"√","ln","exp","e"},{"7","8","9","÷"},{"4","5","6","×"},{"1","2","3","−"},{"0",".","±","+"},{"BACK","1/x","xʸ","ENTER"}};
        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            for (String key : row) {
                Button button = new Button(this); button.setText(key); button.setTextSize(15);
                button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(0,0,0,0);
                button.setContentDescription(key.equals("BACK") ? "Backspace" : key);
                button.setOnClickListener(v -> execute(key));
                line.addView(button,new LinearLayout.LayoutParams(0,(int)(44 * getResources().getDisplayMetrics().density),1));
            }
            keys.addView(line);
        }
        root.addView(keys); setContentView(root); refresh();
    }
    private TextView text(int size) { TextView t = new TextView(this); t.setTextSize(size); t.setTextColor(Color.rgb(228,237,242)); t.setPadding(4,8,4,8); return t; }
    private void execute(String key) {
        if (session.busy) return;
        session.busy = true; refresh();
        session.task = session.worker.submit(() -> {
            try {
                session.calculator.key(key);
                StringBuilder s = new StringBuilder();
                java.util.List<UnifiedReal> values = session.calculator.values();
                for (int i=0;i<values.size();i++) s.append(values.size()-i).append("   ").append(RpnCalculator.format(values.get(i))).append('\n');
                session.stack = s.length()==0 ? "Stack is empty" : s.toString();
                session.entry = session.calculator.entry(); session.error = "Radians · exact values";
            } catch (RuntimeException ex) { session.error = ex instanceof com.hp.creals.CR.AbortedException ? "Calculation cancelled" : "Error: " + (ex.getMessage()==null ? "Undefined result" : ex.getMessage()); }
            finally { session.busy = false; }
        });
    }
    private final Runnable tick = new Runnable() { public void run() { refresh(); handler.postDelayed(this,150); } };
    private void refresh() { stackView.setText(session.stack); entryView.setText(session.entry.isEmpty()?"▸":session.entry); status.setText(session.busy?"Calculating… Back to cancel":session.error); }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override public void onBackPressed() { if (session.busy && session.task!=null) session.task.cancel(true); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (isFinishing()) session.worker.shutdownNow(); super.onDestroy(); }
}
