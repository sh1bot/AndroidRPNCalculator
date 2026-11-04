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
    private EntryField entryView;
    private TextView status;
    private Button notationButton, angleButton, numberFormatButton, fractionButton;
    private CalculatorKeypad keys;
    private LocaleNumbers numbers, localNumbers, computerNumbers, ungroupedNumbers;
    private boolean changingEntry, entryShowsRaw;
    private String shownError = "";
    private android.window.OnBackInvokedCallback backCallback;
    private LinearLayout stackRows;
    private ScrollView scroll;
    private Display rendered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    static final class EntryField extends EditText {
        java.util.function.BiConsumer<Integer,Integer> selectionChanged;
        Runnable paste;
        EntryField(android.content.Context context) { super(context); }
        @Override protected void onSelectionChanged(int start,int end) {
            super.onSelectionChanged(start,end);
            if(selectionChanged!=null) selectionChanged.accept(start,end);
        }
        @Override public boolean onTextContextMenuItem(int id) {
            if((id==android.R.id.paste || id==android.R.id.pasteAsPlainText) && paste!=null) { paste.run(); return true; }
            return super.onTextContextMenuItem(id);
        }
    }
    static final class Display {
        final List<String> rows,clipboard,compactRows;
        final String entry,problem;
        final ValueFormatter.Notation notation;
        final boolean editing,raw,fractions;
        final int numberMode;
        final int start,end;
        final RpnCalculator.AngleUnit angleUnit;
        Display(List<String> rows,List<String> clipboard,List<String> compactRows,RpnCalculator calculator,ValueFormatter.Notation notation,int numberMode,boolean fractions) {
            this.rows=rows; this.clipboard=clipboard; this.compactRows=compactRows; this.notation=notation; this.numberMode=numberMode; this.fractions=fractions;
            entry=calculator.entry(); editing=calculator.isEditing(); raw=calculator.rawEntry();
            start=calculator.selectionStart(); end=calculator.selectionEnd(); problem=calculator.entryProblem();
            angleUnit=calculator.angleUnit();
        }
    }
    static final class Session {
        RpnCalculator calculator = new RpnCalculator();
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        volatile Display display;
        volatile String error = "";
        final Deque<String> pending = new ArrayDeque<>();
        volatile boolean busy, cancelRequested, background;
        CalculatorStore store;
        boolean loaded;
        volatile long busySince;
        volatile Thread calculatingThread;
        ValueFormatter.Notation notation;
        int numberMode;
        boolean fractions;
        Session(ValueFormatter.Notation n, RpnCalculator.AngleUnit angle,int numberMode,boolean fractions) {
            notation = n; this.numberMode=numberMode; this.fractions=fractions; calculator.setAngleUnit(angle);
            display = new Display(Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),calculator,n,numberMode,fractions);
        }
    }
    private LocaleNumbers numberFormat(int mode) {
        return mode==1 ? computerNumbers : mode==2 ? ungroupedNumbers : localNumbers;
    }
    @Override public Object onRetainNonConfigurationInstance() { return session; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Locale locale=getResources().getConfiguration().getLocales().get(0);
        localNumbers=new LocaleNumbers(locale); computerNumbers=new LocaleNumbers(locale,true); ungroupedNumbers=new LocaleNumbers(locale,false,false);
        session = (Session)getLastNonConfigurationInstance();
        boolean restoreSession=session==null;
        if (session == null) session = new Session(getPreferences(0).getBoolean("engineering", false)
            ? ValueFormatter.Notation.ENGINEERING : ValueFormatter.Notation.SCIENTIFIC,
            RpnCalculator.AngleUnit.valueOf(getPreferences(0).getString("angleUnit","RAD")),
            getPreferences(0).getInt("numberMode",getPreferences(0).getBoolean("computerNumbers",false) ? 1 : 0),
            getPreferences(0).getBoolean("fractions",false));
        if(restoreSession) session.store=new CalculatorStore(getNoBackupFilesDir());
        numbers=numberFormat(session.numberMode);
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
        angleButton = new Button(this); styleModeButton(angleButton);
        angleButton.setOnClickListener(v -> execute("ANGLE"));
        header.addView(angleButton,new LinearLayout.LayoutParams(dp(54),dp(48)));
        notationButton = new Button(this); styleModeButton(notationButton);
        notationButton.setOnClickListener(v -> execute("FORMAT"));
        header.addView(notationButton,new LinearLayout.LayoutParams(dp(54),dp(48)));
        numberFormatButton=new Button(this); styleModeButton(numberFormatButton);
        numberFormatButton.setOnClickListener(v -> execute("NUMBER_FORMAT"));
        header.addView(numberFormatButton,new LinearLayout.LayoutParams(dp(54),dp(48)));
        fractionButton=new Button(this); styleModeButton(fractionButton);
        fractionButton.setOnClickListener(v -> execute("FRACTION"));
        header.addView(fractionButton,new LinearLayout.LayoutParams(dp(54),dp(48)));
        status = text(12); status.setGravity(Gravity.CENTER_VERTICAL); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(status,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(header);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        stackRows = new LinearLayout(this); stackRows.setOrientation(1); stackRows.setGravity(Gravity.BOTTOM);
        scroll.addView(stackRows,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        entryView=new EntryField(this); entryView.setTextSize(32);
        entryView.setGravity(Gravity.START); digits(entryView);
        entryView.setPadding(dp(4),dp(6),dp(4),dp(6)); entryView.setBackground(null);
        entryView.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        digits(entryView);
        entryView.setSingleLine(true); entryView.setHint("▸"); entryView.setHintTextColor(Color.rgb(133,150,164));
        entryView.setShowSoftInputOnFocus(false);
        entryView.paste=() -> execute("PASTE");
        entryView.selectionChanged=(start,end) -> {
            if(!changingEntry && start>=0 && end>=0) execute("SELECT:"+start+":"+end);
        };
        entryView.addTextChangedListener(new android.text.TextWatcher() {
            private int start,count;
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { this.start=start; this.count=count; }
            @Override public void afterTextChanged(android.text.Editable text) {
                if(changingEntry) return;
                boolean explicit=count==1 && start<text.length() && text.charAt(start)==numbers.decimalSeparator();
                String value=entryShowsRaw ? text.toString() : numbers.canonicalEntry(text.toString());
                execute("EDIT:"+Math.max(0,entryView.getSelectionStart())+":"+Math.max(0,entryView.getSelectionEnd())
                    +":"+entryShowsRaw+":"+explicit+":"+value);
            }
        });
        root.addView(entryView);
        keys = new CalculatorKeypad(this, this::execute,
            getPreferences(0).getBoolean("scientificExpanded",false),
            expanded -> getPreferences(0).edit().putBoolean("scientificExpanded",expanded).apply());
        root.addView(keys); setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        refresh();
        if(restoreSession) execute("RESTORE");
    }
    private void styleModeButton(Button button) {
        button.setTextSize(12); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(4),0,dp(4),0);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void digits(TextView t) { t.setTypeface(Typeface.MONOSPACE); t.setFontFeatureSettings("tnum"); }
    private TextView text(int size) { TextView t = new TextView(this); t.setTextSize(size); t.setTextColor(Color.rgb(228,237,242)); t.setPadding(dp(4),dp(6),dp(4),dp(6)); return t; }
    private void execute(String key) {
        if (key.equals("PASTE")) { pasteClipboard(); return; }
        if (key.equals("CANCEL") || (key.equals("UNDO") && session.busy
            && SystemClock.elapsedRealtime()-session.busySince >= 250)) { cancelCalculation(); return; }
        boolean start;
        synchronized (session) {
            session.pending.addLast(key);
            start = !session.busy;
            if (start) { session.cancelRequested = false; session.busySince = SystemClock.elapsedRealtime(); session.busy = true; }
        }
        if(!key.startsWith("EDIT:") && !key.startsWith("SELECT:")) refresh();
        if (start) session.worker.submit(this::drainKeys);
    }
    private void pasteClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip==null || clip.getItemCount()==0 || clip.getItemAt(0).getText()==null) {
            Toast.makeText(this,"Clipboard does not contain a number",Toast.LENGTH_SHORT).show(); return;
        }
        try { execute("PASTE_RAW:"+clip.getItemAt(0).getText().toString()); }
        catch (IllegalArgumentException ex) { Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void copyValue(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Calculator value",numbers.copy(text)));
        if (Build.VERSION.SDK_INT<=32) Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
    }
    private void drainKeys() {
        while (true) {
            String key;
            synchronized (session) {
                if (session.cancelRequested) session.pending.clear();
                if (session.pending.isEmpty()) {
                    session.calculatingThread = null; session.busy = false;
                    Thread.interrupted();
                    key=null;
                } else {
                    key = session.pending.removeFirst();
                    session.calculatingThread = Thread.currentThread();
                }
            }
            if(key==null) { persistSession(true); return; }
            try {
                checkCancelled();
                // Private candidate includes undo; cancellation during formatting rolls back too.
                RpnCalculator candidate;
                if(key.equals("RESTORE")) {
                    try { candidate=session.store.load(); if(candidate==null) candidate=session.calculator.copy(); }
                    catch(java.io.IOException ex) { throw new IllegalStateException("Could not restore saved calculator",ex); }
                } else candidate=session.calculator.copy();
                ValueFormatter.Notation notation = session.notation;
                int numberMode=session.numberMode;
                boolean fractions=session.fractions;
                LocaleNumbers format=numberFormat(numberMode);
                candidate.setInputProfile(format.profile());
                if(key.equals("RESTORE") || key.equals("CHECKPOINT")) { /* State already prepared. */ }
                else if (key.equals("FORMAT")) notation = notation == ValueFormatter.Notation.SCIENTIFIC
                    ? ValueFormatter.Notation.ENGINEERING : ValueFormatter.Notation.SCIENTIFIC;
                else if(key.equals("NUMBER_FORMAT")) numberMode=(numberMode+1)%3;
                else if(key.equals("FRACTION")) fractions=!fractions;
                else if(key.startsWith("PASTE_RAW:")) candidate.pasteText(key.substring(10),localNumbers.profile());
                else if(key.startsWith("SELECT:")) {
                    String[] parts=key.split(":"); candidate.select(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
                } else if(key.startsWith("EDIT:")) {
                    String[] parts=key.split(":",6);
                    candidate.editText(parts[5],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),
                        Boolean.parseBoolean(parts[3]),Boolean.parseBoolean(parts[4]),format.profile());
                } else candidate.key(key);
                List<String> rows = candidate.formattedValues(notation,fractions);
                List<String> copies = new ArrayList<>();
                for (UnifiedReal value : candidate.values()) { checkCancelled(); copies.add(ValueFormatter.clipboard(value,notation)); }
                List<String> compactRows = new ArrayList<>();
                for(UnifiedReal value : candidate.values()) { checkCancelled(); compactRows.add(ValueFormatter.format(value,false,notation,fractions)); }
                synchronized (session) {
                    checkCancelled();
                    session.calculator = candidate; session.notation = notation; session.numberMode=numberMode; session.fractions=fractions;
                    session.display = new Display(rows,Collections.unmodifiableList(copies),Collections.unmodifiableList(compactRows),candidate,notation,numberMode,fractions);
                    session.error = "";
                    if(!key.equals("CHECKPOINT")) session.loaded=true;
                }
                persistSession(false);
            } catch (RuntimeException ex) {
                session.error = session.cancelRequested || ex instanceof com.hp.creals.CR.AbortedException
                    ? "Calculation cancelled" : "Error: " + (ex.getMessage()==null ? "Undefined result" : ex.getMessage());
                synchronized (session) { session.pending.clear(); }
            } finally { Thread.interrupted(); }
        }
    }
    private void persistSession(boolean idle) {
        // Called only by the serial worker, after a committed operation or when its queue drains.
        if(!session.loaded) return;
        try { session.store.save(session.calculator,idle && session.background); }
        catch(java.io.IOException | RuntimeException ex) {
            android.util.Log.e("ExactRPN","Could not save calculator",ex);
            session.error="Could not save calculator state";
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
    private void handleBack() { if (session.busy) cancelCalculation(); else if (session.display.editing) execute("CE"); else finish(); }
    private final Runnable tick = new Runnable() { public void run() { refresh(); handler.postDelayed(this,100); } };
    private void refresh() {
        Display display = session.display;
        if (rendered != display) {
            numbers=numberFormat(display.numberMode);
            keys.setNumberFormat(numbers);
            boolean stackChanged = rendered == null || rendered.numberMode!=display.numberMode || rendered.editing != display.editing || !rendered.rows.equals(display.rows) || !rendered.clipboard.equals(display.clipboard);
            if (stackChanged) {
                stackRows.removeAllViews();
                if (display.rows.isEmpty()) { TextView empty = text(16); empty.setText("Stack is empty"); empty.setTextColor(Color.rgb(133,150,164)); stackRows.addView(empty); }
                for (int i=0;i<display.rows.size();i++) {
                    int depth = display.rows.size()-i;
                    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.BOTTOM);
                    TextView label = text(12); digits(label); label.setText(depth==1 ? "x:" : depth==2 ? "y:" : depth + ":");
                    label.setGravity(Gravity.START | Gravity.BOTTOM); label.setTextColor(Color.rgb(133,150,164));
                    label.setContentDescription(depth==1 ? "Stack X" : depth==2 ? "Stack Y" : "Stack level " + depth);
                    row.addView(label,new LinearLayout.LayoutParams(dp(46),-2));
                    boolean result = !display.editing && depth == 1;
                    TextView value = text(result ? 32 : 21); digits(value); value.setGravity(Gravity.END); value.setText(numbers.stack(display.rows.get(i),Color.rgb(133,150,164)));
                    if (!result) {
                        value.setSingleLine(true); value.setHorizontallyScrolling(false);
                        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        value.setAutoSizeTextTypeUniformWithConfiguration(12,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                    }
                    if(result && !display.fractions && display.rows.get(i).contains(".") && !display.rows.get(i).startsWith("≈")) {
                        final CharSequence full=numbers.stack(display.rows.get(i),Color.rgb(133,150,164));
                        final CharSequence compact=numbers.stack(display.compactRows.get(i),Color.rgb(133,150,164));
                        value.setSingleLine(true); value.setHorizontallyScrolling(false);
                        value.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                            int available=r-l-value.getPaddingLeft()-value.getPaddingRight();
                            if(available<=0) return;
                            android.text.TextPaint measure=new android.text.TextPaint(value.getPaint());
                            float scale=getResources().getDisplayMetrics().scaledDensity;
                            int size=32;
                            for(;size>21;size--) { measure.setTextSize(size*scale); if(measure.measureText(full.toString())<=available) break; }
                            measure.setTextSize(size*scale);
                            CharSequence chosen=measure.measureText(full.toString())<=available ? full : compact;
                            if(!value.getText().toString().equals(chosen.toString())) value.setText(chosen);
                            if(value.getTextSize()!=size*scale) value.setTextSize(size);
                        });
                    }
                    value.setTextIsSelectable(false); row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                    final String copy = display.clipboard.get(i);
                    row.setOnClickListener(v -> copyValue(copy));
                    row.setContentDescription("Copy stack level " + depth + ": " + value.getText());
                    stackRows.addView(row);
                }
            }
            changingEntry=true;
            try {
                entryShowsRaw=display.raw;
                entryView.setVisibility(display.editing ? View.VISIBLE : View.GONE);
                String entry=display.raw ? display.entry : numbers.entry(display.entry);
                if(!entryView.getText().toString().equals(entry)) entryView.setText(entry);
                entryView.setTextColor(display.problem.isEmpty() ? Color.rgb(228,237,242) : Color.rgb(255,125,125));
                if(Build.VERSION.SDK_INT>=30) entryView.setStateDescription(!display.problem.isEmpty() ? display.problem+" number" : "Number entry");
                if(display.editing) {
                    if(!entryView.hasFocus()) entryView.requestFocus();
                    int start=Math.min(entry.length(),display.start),end=Math.min(entry.length(),display.end);
                    if(entryView.getSelectionStart()!=start || entryView.getSelectionEnd()!=end) entryView.setSelection(start,end);
                }
            } finally { changingEntry=false; }
            keys.setEditing(display.editing);
            angleButton.setText(display.angleUnit.name());
            angleButton.setContentDescription("Angle unit: " + display.angleUnit.name() + ". Tap to switch.");
            getPreferences(0).edit().putString("angleUnit",display.angleUnit.name()).apply();
            notationButton.setText(display.notation == ValueFormatter.Notation.ENGINEERING ? "ENG" : "SCI");
            notationButton.setContentDescription("Display notation: " + (display.notation == ValueFormatter.Notation.ENGINEERING ? "engineering" : "scientific") + ". Tap to switch.");
            getPreferences(0).edit().putBoolean("engineering",display.notation == ValueFormatter.Notation.ENGINEERING).apply();
            fractionButton.setText(display.fractions ? "FRAC" : "DEC");
            fractionButton.setContentDescription(display.fractions
                ? "Known rational results as fractions. Tap for decimals."
                : "Decimal results. Tap for fractions.");
            getPreferences(0).edit().putBoolean("fractions",display.fractions).apply();
            numberFormatButton.setText(new String[]{"LOC","DOT","NONE"}[display.numberMode]);
            numberFormatButton.setContentDescription(new String[]{
                "Phone locale number format. Tap for decimal dot with thin space grouping.",
                "Decimal dot with thin space grouping. Tap for locale decimal without grouping.",
                "Locale decimal without grouping. Tap for phone locale grouping."
            }[display.numberMode]);
            getPreferences(0).edit().putInt("numberMode",display.numberMode).apply();
            boolean follow = rendered==null || rendered.editing!=display.editing || !rendered.rows.equals(display.rows);
            rendered = display;
            // A second post runs after the updated rows have been measured. Do not repeatedly
            // scroll on polling ticks: the user must still be able to inspect older entries.
            if(follow) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        // Brief input evaluations keep Undo visible; longer work turns the same key into Cancel.
        boolean showProgress = session.busy && (session.cancelRequested
            || SystemClock.elapsedRealtime() - session.busySince >= 250);
        status.setText(showProgress ? (session.cancelRequested ? "Cancelling…" : "Working…")
            : !display.problem.isEmpty() ? display.problem.toUpperCase(Locale.ROOT) : display.editing ? "ENTRY" : "STACK");
        status.setContentDescription(status.getText());
        if (!session.error.isEmpty() && !session.error.equals(shownError)) Toast.makeText(this,session.error,Toast.LENGTH_LONG).show();
        shownError = session.error;
        keys.setCalculating(showProgress);
        notationButton.setEnabled(!session.busy);
        angleButton.setEnabled(!session.busy);
        numberFormatButton.setEnabled(!session.busy);
        fractionButton.setEnabled(!session.busy);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int unicode = event.getUnicodeChar();
        if (unicode==numbers.decimalSeparator()) unicode = '.';
        String key = KeyboardInput.command(keyCode,unicode,event.isCtrlPressed(),event.isAltPressed(),event.isMetaPressed());
        if (key == null) return super.dispatchKeyEvent(event);
        boolean navigation=key.equals("LEFT") || key.equals("RIGHT") || key.equals("HOME") || key.equals("END");
        if(navigation) {
            if(!session.display.editing) return super.dispatchKeyEvent(event);
            if(event.isShiftPressed()) key="SELECT_"+key;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (key.equals("ESCAPE")) { if (session.busy) cancelCalculation(); else execute("CE"); }
        else execute(key);
        return true;
    }
    @Override protected void onStart() { super.onStart(); session.background=false; }
    @Override protected void onStop() {
        if(!isChangingConfigurations()) { session.background=true; execute("CHECKPOINT"); }
        super.onStop();
    }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override public void onBackPressed() { handleBack(); }
    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if (isFinishing()) session.worker.shutdown();
        super.onDestroy();
    }
}




