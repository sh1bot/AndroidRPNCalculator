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

/** Exact values and evaluation survive rotation; views consume immutable display snapshots. */
public final class MainActivity extends Activity {
    private Session session;
    private ThemeColors colors;
    private EntryField entryView;
    private TextView status, noticeView;
    private Button notationButton, angleButton, numberFormatButton, fractionButton;
    private CalculatorKeypad keys;
    private LocaleNumbers numbers, localNumbers, computerNumbers, ungroupedNumbers;
    private boolean changingEntry, entryShowsRaw;
    private String shownError = "";
    private android.window.OnBackInvokedCallback backCallback;
    private LinearLayout stackRows;
    private ScrollView scroll;
    private Display rendered;
    private boolean selectionDirty;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideNotice=() -> { if(noticeView!=null) noticeView.setVisibility(View.GONE); };
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
        final String entry,problem,stackId;
        final ValueFormatter.Notation notation;
        final boolean editing,raw;
        final int fractionMode;
        final int numberMode;
        final int start,end;
        final RpnCalculator.AngleUnit angleUnit;
        Display(ValueFormatter.Notation notation,RpnCalculator.AngleUnit angle,int numberMode,int fractionMode) {
            rows=clipboard=compactRows=Collections.emptyList(); entry=problem=""; stackId="initial";
            this.notation=notation; angleUnit=angle; this.numberMode=numberMode; this.fractionMode=fractionMode;
            editing=raw=false; start=end=0;
        }
        Display(Bundle result) {
            rows=Collections.unmodifiableList(result.getStringArrayList("rows"));
            clipboard=Collections.unmodifiableList(result.getStringArrayList("clipboard"));
            compactRows=Collections.unmodifiableList(result.getStringArrayList("compactRows"));
            stackId=result.getString("stackId");
            entry=result.getString("entry"); problem=result.getString("problem");
            notation=ValueFormatter.Notation.valueOf(result.getString("notation"));
            angleUnit=RpnCalculator.AngleUnit.valueOf(result.getString("angle"));
            numberMode=result.getInt("numberMode"); fractionMode=result.getInt("fractionMode");
            editing=result.getBoolean("editing"); raw=result.getBoolean("raw"); start=result.getInt("start"); end=result.getInt("end");
        }
    }
    static final class Session {
        String checkpoint;
        RemoteCalculator remote;
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        volatile Display display;
        volatile String error = "";
        final Deque<String> pending = new ArrayDeque<>();
        final SortedSet<Integer> selectedRows=new TreeSet<>();
        volatile boolean busy, cancelRequested, background;
        CalculatorStore store;
        boolean loaded, scientificExpanded, inverse;
        volatile long busySince;
        volatile Thread calculatingThread;
        ValueFormatter.Notation notation;
        int numberMode;
        int fractionMode;
        Session(ValueFormatter.Notation n, RpnCalculator.AngleUnit angle,int numberMode,int fractionMode) {
            notation = n; this.numberMode=numberMode; this.fractionMode=fractionMode;
            display = new Display(n,angle,numberMode,fractionMode);
        }
    }
    private LocaleNumbers numberFormat(int mode) {
        return mode==1 ? computerNumbers : mode==2 ? ungroupedNumbers : localNumbers;
    }
    @Override public Object onRetainNonConfigurationInstance() { return session; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        colors=new ThemeColors(this);
        // Keep cursor/selection and hardware keys, but never connect this window to an IME.
        // setShowSoftInputOnFocus alone does not prevent restoration after another app closes.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        // Own the safe-area padding explicitly rather than combining decor fitting and app insets.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(Build.VERSION.SDK_INT>=28) {
            WindowManager.LayoutParams window=getWindow().getAttributes();
            window.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(window);
        }
        Locale locale=getResources().getConfiguration().getLocales().get(0);
        localNumbers=new LocaleNumbers(locale); computerNumbers=new LocaleNumbers(locale,true); ungroupedNumbers=new LocaleNumbers(locale,false,false);
        session = (Session)getLastNonConfigurationInstance();
        boolean restoreSession=session==null;
        if (session == null) session = new Session(getPreferences(0).getBoolean("engineering", false)
            ? ValueFormatter.Notation.ENGINEERING : ValueFormatter.Notation.SCIENTIFIC,
            RpnCalculator.AngleUnit.valueOf(getPreferences(0).getString("angleUnit","RAD")),
            getPreferences(0).getInt("numberMode",getPreferences(0).getBoolean("computerNumbers",false) ? 1 : 0),
            getPreferences(0).getInt("fractionMode",getPreferences(0).getBoolean("fractions",false)?1:0));
        if(restoreSession) {
            session.store=new CalculatorStore(getNoBackupFilesDir());
            session.remote=new RemoteCalculator(this);
        }
        numbers=numberFormat(session.numberMode);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(16),dp(4),dp(16),dp(12)); root.setBackgroundColor(colors.background);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            int left,top,right,bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                // getInsets unions the types, taking the maximum per edge rather than adding them.
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left=safe.left; top=safe.top; right=safe.right; bottom=safe.bottom;
            } else {
                left=insets.getSystemWindowInsetLeft(); top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null) {
                    DisplayCutout cutout=insets.getDisplayCutout();
                    left=Math.max(left,cutout.getSafeInsetLeft()); top=Math.max(top,cutout.getSafeInsetTop());
                    right=Math.max(right,cutout.getSafeInsetRight()); bottom=Math.max(bottom,cutout.getSafeInsetBottom());
                }
            }
            root.setPadding(dp(16)+left,dp(4)+top,dp(16)+right,dp(12)+bottom);
            positionNotice(root);
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
        entryView.setSingleLine(true); entryView.setHint("▸"); entryView.setHintTextColor(colors.muted);
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
                numbers.styleDecimal(text);
                boolean explicit=count==1 && start<text.length() && text.charAt(start)==numbers.decimalSeparator();
                String value=entryShowsRaw ? text.toString() : numbers.canonicalEntry(text.toString());
                execute("EDIT:"+Math.max(0,entryView.getSelectionStart())+":"+Math.max(0,entryView.getSelectionEnd())
                    +":"+entryShowsRaw+":"+explicit+":"+value);
            }
        });
        root.addView(entryView);
        keys = new CalculatorKeypad(this, this::execute,
            session.scientificExpanded,
            expanded -> session.scientificExpanded=expanded);
        keys.setInverse(session.inverse);
        root.addView(keys);
        FrameLayout screen=new FrameLayout(this);
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        noticeView=text(14); noticeView.setPadding(dp(12),dp(10),dp(12),dp(10));
        noticeView.setMaxLines(3); noticeView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        noticeView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        android.graphics.drawable.GradientDrawable noticeBackground=new android.graphics.drawable.GradientDrawable();
        noticeBackground.setColor(colors.numberKey); noticeBackground.setCornerRadius(dp(12));
        noticeView.setBackground(noticeBackground); noticeView.setElevation(dp(8)); noticeView.setVisibility(View.GONE);
        screen.addView(noticeView,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        positionNotice(root); setContentView(screen); screen.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        refresh();
        if(restoreSession) execute("RESTORE");
    }
    private void positionNotice(LinearLayout content) {
        if(noticeView==null) return;
        FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)noticeView.getLayoutParams();
        layout.leftMargin=content.getPaddingLeft(); layout.rightMargin=content.getPaddingRight();
        layout.topMargin=content.getPaddingTop()+dp(52); noticeView.setLayoutParams(layout);
    }
    private void showNotice(String message,boolean error) {
        noticeView.setText(message); noticeView.setTextColor(error ? colors.error : colors.text);
        noticeView.setVisibility(View.VISIBLE); noticeView.bringToFront();
        handler.removeCallbacks(hideNotice); handler.postDelayed(hideNotice,error ? 5000 : 2000);
    }
    private void styleModeButton(Button button) {
        button.setTextColor(colors.text); button.setTextSize(12); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(4),0,dp(4),0);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void digits(TextView t) { t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); t.setFontFeatureSettings("tnum"); }
    private TextView text(int size) { TextView t = new TextView(this); t.setTextSize(size); t.setTextColor(colors.text); t.setPadding(dp(4),dp(6),dp(4),dp(6)); return t; }
    private void execute(String key) {
        if(key.equals("INVERSE")) { session.inverse=!session.inverse; keys.setInverse(session.inverse); return; }
        if (key.equals("PASTE")) { pasteClipboard(); return; }
        if (key.equals("CANCEL") || (key.equals("UNDO") && session.busy
            && SystemClock.elapsedRealtime()-session.busySince >= 250)) { cancelCalculation(); return; }
        if(!key.equals("FORMAT") && !key.equals("NUMBER_FORMAT") && !key.equals("FRACTION")
            && !key.equals("ANGLE") && !key.equals("CHECKPOINT") && !key.startsWith("SELECT:") && !key.startsWith("DROP_ROWS:")) clearStackSelection(false);
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
            showNotice("Clipboard does not contain a number",true); return;
        }
        CharSequence text=clip.getItemAt(0).getText();
        if(text.length()>RpnCalculator.MAX_ENTRY_LENGTH) { showNotice("Pasted number is too long (maximum 4096 characters)",true); return; }
        execute("PASTE_RAW:"+text.toString());
    }
    private void copyValue(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Calculator value",numberFormat(session.display.numberMode).copy(text)));
        if (Build.VERSION.SDK_INT<=32) showNotice("Copied",false);
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
                if(key.equals("RESTORE")) {
                    try { session.checkpoint=session.store.checkpoint(); session.store.cleanWorkFiles(session.checkpoint); }
                    catch(java.io.IOException ex) { throw new IllegalStateException("Could not restore saved calculator",ex); }
                }
                Bundle request=new Bundle();
                request.putString("key",key); request.putString("input",session.checkpoint);
                request.putString("locale",getResources().getConfiguration().getLocales().get(0).toLanguageTag());
                request.putString("angle",session.display.angleUnit.name());
                request.putString("notation",session.notation.name()); request.putInt("numberMode",session.numberMode);
                request.putInt("fractionMode",session.fractionMode);
                Bundle result=session.remote.evaluate(request);
                Display display=new Display(result);
                publishResult(result,display,key);
                persistSession(false);
            } catch (RuntimeException ex) {
                if(key.equals("RESTORE")) {
                    session.checkpoint=null; session.remote.cancel();
                    if(ex instanceof RemoteCalculator.InvalidCheckpoint) {
                        try { session.store.quarantine(); }
                        catch(java.io.IOException backupError) { android.util.Log.e("ExactRPN","Could not quarantine checkpoint",backupError); }
                    }
                }
                session.error = session.cancelRequested || ex instanceof com.hp.creals.CR.AbortedException
                    ? "Calculation cancelled" : key.equals("RESTORE") ? "Could not restore saved calculation. Started empty." : "Error: " + (ex.getMessage()==null ? "Undefined result" : ex.getMessage());
                synchronized (session) { session.pending.clear(); }
            } finally { Thread.interrupted(); }
        }
    }
    private void publishResult(Bundle result,Display display,String key) {
        Runnable accept=() -> {
            synchronized(session) {
                checkCancelled();
                if(key.startsWith("DROP_ROWS:")) {
                    SortedSet<Integer> removed=new TreeSet<>();
                    for(String index:key.substring(10).split(",")) removed.add(Integer.parseInt(index));
                    SortedSet<Integer> remaining=new TreeSet<>();
                    for(int index:session.selectedRows) if(!removed.contains(index)) remaining.add(index-removed.headSet(index).size());
                    session.selectedRows.clear(); session.selectedRows.addAll(remaining);
                }
                session.checkpoint=result.getString("checkpoint");
                session.notation=display.notation; session.numberMode=display.numberMode; session.fractionMode=display.fractionMode;
                session.display=display; session.error="";
                if(!key.equals("CHECKPOINT")) session.loaded=true;
            }
        };
        if(!key.startsWith("DROP_ROWS:")) { accept.run(); return; }
        // Publish selection remapping and the new rows together on the UI thread, before
        // another queued result can overtake this one. Failed/cancelled drops leave selection intact.
        FutureTask<Void> commit=new FutureTask<>(accept,null); handler.post(commit);
        try { commit.get(); }
        catch(InterruptedException ex) { commit.cancel(false); Thread.currentThread().interrupt(); throw new com.hp.creals.CR.AbortedException(); }
        catch(ExecutionException ex) {
            if(ex.getCause() instanceof RuntimeException) throw (RuntimeException)ex.getCause();
            throw new IllegalStateException("Could not update stack selection",ex.getCause());
        }
    }
    private void persistSession(boolean idle) {
        // Called only by the serial worker, after a committed operation or when its queue drains.
        if(!session.loaded) { session.store.cleanWorkFiles(session.checkpoint); return; }
        try {
            if(idle && session.background && session.display.rows.isEmpty() && session.display.entry.isEmpty()) {
                session.checkpoint=null; session.store.clear();
            } else if(session.checkpoint!=null) session.store.save(session.checkpoint);
            session.store.cleanWorkFiles(session.checkpoint);
        }
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
            session.pending.clear();
            session.remote.cancel();
            if (session.calculatingThread != null) session.calculatingThread.interrupt();
        }
        refresh();
    }
    private void handleBack() { if (session.busy) cancelCalculation(); else if(!session.selectedRows.isEmpty()) clearStackSelection(true); else if (session.display.editing) execute("CANCEL_ENTRY"); else finish(); }
    private final Runnable tick = new Runnable() { public void run() { refresh(); handler.postDelayed(this,100); } };
    private void clearStackSelection(boolean redraw) {
        if(session.selectedRows.isEmpty()) return;
        session.selectedRows.clear(); selectionDirty=true;
        if(redraw) refresh();
    }
    private boolean stackActionAllowed(Display snapshot) {
        if(session.busy) { showNotice("Wait for the calculation to finish",true); return false; }
        if(!Objects.equals(session.display.stackId,snapshot.stackId)) { showNotice("Stack changed; try again",true); refresh(); return false; }
        return true;
    }
    private boolean toggleStackRow(int index,Display snapshot) {
        if(!stackActionAllowed(snapshot)) return false;
        int scrollY=scroll.getScrollY();
        View oldRow=stackRows.findViewWithTag("stack-row:"+index);
        int anchorCenter=oldRow==null ? 0 : oldRow.getTop()+oldRow.getHeight()/2-scrollY;
        if(!session.selectedRows.remove(index)) session.selectedRows.add(index);
        selectionDirty=true; refresh();
        scroll.post(() -> {
            View newRow=stackRows.findViewWithTag("stack-row:"+index);
            scroll.scrollTo(0,oldRow==null || newRow==null ? scrollY : newRow.getTop()+newRow.getHeight()/2-anchorCenter);
        });
        return true;
    }
    private void stackRowTapped(int index,Display snapshot) {
        if(!session.selectedRows.isEmpty()) toggleStackRow(index,snapshot);
        else copyValue(snapshot.clipboard.get(index));
    }
    private void showStackMenu(View anchor,int index,Display snapshot) {
        if(!stackActionAllowed(snapshot)) return;
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"Drop"); menu.getMenu().add(0,2,1,"Copy");
        menu.setOnMenuItemClickListener(item -> {
            if(!stackActionAllowed(snapshot)) return true;
            SortedSet<Integer> rows=new TreeSet<>();
            if(session.selectedRows.contains(index)) rows.addAll(session.selectedRows);
            else rows.add(index);
            if(item.getItemId()==1) {
                List<String> indices=new ArrayList<>(); for(int row:rows) indices.add(Integer.toString(row));
                execute("DROP_ROWS:"+String.join(",",indices));
            } else {
                List<String> copies=new ArrayList<>(); for(int row:rows) copies.add(session.display.clipboard.get(row));
                copyValue(String.join("\n",copies));
            }
            return true;
        });
        menu.show();
    }
    private static String withoutApproximation(String number) {
        return number.startsWith("≈ ") ? number.substring(2) : number;
    }
    private void updateStackLabel(TextView label,int depth,String number) {
        boolean approximate=number.startsWith("≈ ");
        String name=depth==1 ? "x" : depth==2 ? "y" : Integer.toString(depth);
        label.setText(name+(approximate ? "≈" : ":"));
        label.setContentDescription("Stack " + name + (approximate ? ", approximate value" : "") + "; long press to select");
    }
    private void saveDisplayPreferences(Display display) {
        android.content.SharedPreferences preferences=getPreferences(0);
        android.content.SharedPreferences.Editor editor=preferences.edit(); boolean changed=false;
        if(!display.angleUnit.name().equals(preferences.getString("angleUnit",""))) { editor.putString("angleUnit",display.angleUnit.name()); changed=true; }
        boolean engineering=display.notation==ValueFormatter.Notation.ENGINEERING;
        if(preferences.getBoolean("engineering",false)!=engineering) { editor.putBoolean("engineering",engineering); changed=true; }
        if(preferences.getInt("fractionMode",-1)!=display.fractionMode) { editor.putInt("fractionMode",display.fractionMode); changed=true; }
        if(preferences.getInt("numberMode",-1)!=display.numberMode) { editor.putInt("numberMode",display.numberMode); changed=true; }
        if(changed) editor.apply();
    }
    private void refresh() {
        Display display = session.display;
        if (rendered != display || selectionDirty) {
            numbers=numberFormat(display.numberMode);
            keys.setNumberFormat(numbers);
            boolean stackChanged = selectionDirty || rendered == null || !Objects.equals(rendered.stackId,display.stackId) || rendered.numberMode!=display.numberMode || rendered.editing != display.editing || !rendered.rows.equals(display.rows) || !rendered.clipboard.equals(display.clipboard);
            selectionDirty=false;
            if (stackChanged) {
                session.selectedRows.removeIf(index -> index>=display.rows.size());
                stackRows.removeAllViews();
                if (display.rows.isEmpty()) { TextView empty = text(16); empty.setText("Stack is empty"); empty.setTextColor(colors.muted); stackRows.addView(empty); }
                for (int i=0;i<display.rows.size();i++) {
                    final int index=i;
                    int depth = display.rows.size()-i;
                    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setTag("stack-row:"+i);
                    if(!session.selectedRows.isEmpty()) {
                        CheckBox selected=new CheckBox(this); selected.setTag("stack-checkbox:"+i);
                        selected.setMinWidth(0); selected.setMinimumWidth(0); selected.setPadding(0,0,0,0);
                        selected.setButtonTintList(new android.content.res.ColorStateList(
                            new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{colors.accent,colors.muted}));
                        selected.setChecked(session.selectedRows.contains(i)); selected.setContentDescription("Select stack level "+depth);
                        selected.setOnClickListener(v -> { if(!toggleStackRow(index,display)) selected.setChecked(session.selectedRows.contains(index)); });
                        row.addView(selected,new LinearLayout.LayoutParams(dp(40),dp(40)));
                        if(session.selectedRows.contains(i)) {
                            android.graphics.drawable.GradientDrawable highlight=new android.graphics.drawable.GradientDrawable();
                            highlight.setColor(colors.numberKey); highlight.setCornerRadius(dp(8)); row.setBackground(highlight);
                        }
                    }
                    TextView label = text(12); digits(label); label.setSingleLine(true);
                    updateStackLabel(label,depth,display.rows.get(i));
                    label.setTag("stack-label:"+i);
                    label.setOnLongClickListener(v -> { toggleStackRow(index,display); return true; });
                    label.setOnClickListener(v -> stackRowTapped(index,display));
                    label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); label.setTextColor(colors.muted);
                    row.addView(label,new LinearLayout.LayoutParams(dp(46),-2));
                    boolean result = !display.editing && depth == 1;
                    TextView value = text(result ? 32 : 21); digits(value); value.setGravity(Gravity.END); value.setText(numbers.stack(withoutApproximation(display.rows.get(i)),colors.muted));
                    if (!result) {
                        value.setSingleLine(true); value.setHorizontallyScrolling(false);
                        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        value.setAutoSizeTextTypeUniformWithConfiguration(12,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                    }
                    if(result && display.fractionMode==0 && display.rows.get(i).contains(".") && !display.rows.get(i).startsWith("≈")) {
                        final String fullNumber=display.rows.get(i), compactNumber=display.compactRows.get(i);
                        final CharSequence full=numbers.stack(withoutApproximation(fullNumber),colors.muted);
                        final CharSequence compact=numbers.stack(withoutApproximation(compactNumber),colors.muted);
                        value.setSingleLine(true); value.setHorizontallyScrolling(false);
                        value.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                            int available=r-l-value.getPaddingLeft()-value.getPaddingRight();
                            if(available<=0) return;
                            android.text.TextPaint measure=new android.text.TextPaint(value.getPaint());
                            float scale=getResources().getDisplayMetrics().scaledDensity;
                            int size=32;
                            for(;size>21;size--) { measure.setTextSize(size*scale); if(android.text.Layout.getDesiredWidth(full,measure)<=available) break; }
                            measure.setTextSize(size*scale);
                            boolean fits=android.text.Layout.getDesiredWidth(full,measure)<=available;
                            CharSequence chosen=fits ? full : compact;
                            updateStackLabel(label,depth,fits ? fullNumber : compactNumber);
                            row.setContentDescription((session.selectedRows.isEmpty() ? "Copy" : "Select") + " stack level " + depth + (fits || !compactNumber.startsWith("≈ ") ? ": " : ", approximately: ") + chosen);
                            if(!value.getText().toString().equals(chosen.toString())) value.setText(chosen);
                            if(value.getTextSize()!=size*scale) value.setTextSize(size);
                        });
                    }
                    value.setTextIsSelectable(false); row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                    value.setTag("stack-value:"+i);
                    value.setOnLongClickListener(v -> { showStackMenu(value,index,display); return true; });
                    value.setOnClickListener(v -> stackRowTapped(index,display));
                    row.setOnLongClickListener(v -> { showStackMenu(value,index,display); return true; });
                    row.setOnClickListener(v -> stackRowTapped(index,display));
                    row.setContentDescription((session.selectedRows.isEmpty() ? "Copy" : "Select") + " stack level " + depth + (display.rows.get(i).startsWith("≈ ") ? ", approximately: " : ": ") + value.getText());
                    stackRows.addView(row);
                }
            }
            changingEntry=true;
            try {
                entryShowsRaw=display.raw;
                entryView.setVisibility(display.editing ? View.VISIBLE : View.GONE);
                String entry=display.raw ? display.entry : numbers.entry(display.entry);
                if(!entryView.getText().toString().equals(entry)) entryView.setText(entry);
                numbers.styleDecimal(entryView.getText());
                entryView.setTextColor(display.problem.isEmpty() ? colors.text : colors.error);
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
            notationButton.setText(display.notation == ValueFormatter.Notation.ENGINEERING ? "ENG" : "SCI");
            notationButton.setContentDescription("Display notation: " + (display.notation == ValueFormatter.Notation.ENGINEERING ? "engineering" : "scientific") + ". Tap to switch.");
            fractionButton.setText(new String[]{"DEC","IMPR","FRAC"}[display.fractionMode]);
            fractionButton.setContentDescription(new String[]{
                "Decimal display. Tap for improper fractions.",
                "Improper fraction display. Tap for mixed fractions.",
                "Mixed fraction display. Tap for decimals."
            }[display.fractionMode]);
            numberFormatButton.setText(new String[]{"LOC","DOT","NONE"}[display.numberMode]);
            numberFormatButton.setContentDescription(new String[]{
                "Phone locale number format. Tap for decimal dot with thin space grouping.",
                "Decimal dot with thin space grouping. Tap for locale decimal without grouping.",
                "Locale decimal without grouping. Tap for phone locale grouping."
            }[display.numberMode]);
            saveDisplayPreferences(display);
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
        if (!session.error.isEmpty() && !session.error.equals(shownError)) showNotice(session.error,true);
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
        if (key.equals("ESCAPE")) { if (session.busy) cancelCalculation(); else if(!session.selectedRows.isEmpty()) clearStackSelection(true); else execute("CANCEL_ENTRY"); }
        else execute(key);
        return true;
    }
    @Override protected void onStart() { super.onStart(); session.background=false; }
    @Override protected void onStop() {
        if(!isChangingConfigurations()) { session.background=true; execute("CHECKPOINT"); }
        super.onStop();
    }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); handler.removeCallbacks(hideNotice); hideNotice.run(); super.onPause(); }
    @Override public void onBackPressed() { handleBack(); }
    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if (isFinishing()) { session.remote.close(); session.worker.shutdown(); }
        super.onDestroy();
    }
}




