// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.Objects;

/** Exact values and evaluation survive rotation; views consume immutable display snapshots. */
public class MainActivity extends Activity {
    private CalculatorSession session;
    private ThemeColors colors;
    private EntryField entryView;
    private TextView status, noticeView;
    private Button notationButton, angleButton, numberFormatButton, fractionButton;
    private CalculatorKeypad keys;
    private LocaleNumbers numbers, localNumbers, computerNumbers, ungroupedNumbers;
    private boolean changingEntry, entryShowsRaw;
    private long shownErrorRevision = -1;
    private android.window.OnBackInvokedCallback backCallback;
    private LinearLayout stackRows;
    private ScrollView scroll;
    private Display rendered;
    private StackRowRenderer rowRenderer;
    private boolean selectionDirty;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideNotice =
            () -> {
                if (noticeView != null) noticeView.setVisibility(View.GONE);
            };

    static final class EntryField extends EditText {
        java.util.function.BiConsumer<Integer, Integer> selectionChanged;
        Runnable paste;

        EntryField(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onSelectionChanged(int start, int end) {
            super.onSelectionChanged(start, end);
            if (selectionChanged != null) selectionChanged.accept(start, end);
        }

        @Override
        public boolean onTextContextMenuItem(int id) {
            if ((id == android.R.id.paste || id == android.R.id.pasteAsPlainText)
                    && paste != null) {
                paste.run();
                return true;
            }
            return super.onTextContextMenuItem(id);
        }
    }

    private LocaleNumbers numberFormat(NumberMode mode) {
        return mode == NumberMode.DOT
                ? computerNumbers
                : mode == NumberMode.UNGROUPED ? ungroupedNumbers : localNumbers;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return session;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        colors = new ThemeColors(this);
        rowRenderer =
                new StackRowRenderer(
                        this,
                        colors,
                        new StackRowRenderer.Actions() {
                            public boolean toggle(int index, Display display) {
                                return toggleStackRow(index, display);
                            }

                            public void copy(int index, Display display) {
                                stackRowTapped(index, display);
                            }
                        });
        configureWindow();
        boolean restoreSession = restoreSession();
        LinearLayout root = createRoot();
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        stackRows = new LinearLayout(this);
        stackRows.setOrientation(1);
        stackRows.setGravity(Gravity.BOTTOM);
        scroll.addView(stackRows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout stackPanel = new LinearLayout(this);
        stackPanel.setOrientation(LinearLayout.VERTICAL);
        stackPanel.setTag("stack-panel");
        addModeButtons(stackPanel);
        stackPanel.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> positionNotice(root));
        stackPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addEntry(stackPanel);
        keys =
                new CalculatorKeypad(
                        this,
                        this::execute,
                        session.scientificExpanded,
                        (expanded, inverse) -> {
                            dismissErrorNotice();
                            session.scientificExpanded = expanded;
                            session.inverse = inverse;
                        });
        keys.setBeforeInput(this::refresh);
        keys.setInverse(session.inverse);
        keys.setTauPreference(
                session.tauConstant,
                enabled -> {
                    dismissErrorNotice();
                    session.tauConstant = enabled;
                    if (session.canUseSavedState())
                        getPreferences(0).edit().putBoolean("tauConstant", enabled).apply();
                });
        root.addView(
                new CalculatorWorkspace(this, stackPanel, keys),
                new LinearLayout.LayoutParams(-1, 0, 1));
        installContent(root);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        refresh();
        if (restoreSession) execute(Command.RESTORE);
    }

    private void positionNotice(LinearLayout content) {
        if (noticeView == null) return;
        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) noticeView.getLayoutParams();
        View stack = content.findViewWithTag("stack-panel");
        int left = content.getPaddingLeft();
        int right = content.getPaddingRight();
        if (stack != null && stack.getWidth() > 0)
            right = Math.max(right, content.getWidth() - left - stack.getWidth());
        int top = content.getPaddingTop() + dp(52);
        if (layout.leftMargin != left || layout.rightMargin != right || layout.topMargin != top) {
            layout.leftMargin = left;
            layout.rightMargin = right;
            layout.topMargin = top;
            noticeView.setLayoutParams(layout);
        }
    }

    private void showNotice(String message, boolean error) {
        noticeView.setText(message);
        noticeView.setTextColor(error ? colors.error : colors.text);
        noticeView.setVisibility(View.VISIBLE);
        noticeView.bringToFront();
        handler.removeCallbacks(hideNotice);
        handler.postDelayed(hideNotice, error ? 5000 : 2000);
    }

    private void styleModeButton(Button button) {
        button.setTextColor(colors.text);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void digits(TextView t) {
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setFontFeatureSettings("tnum");
    }

    private TextView text(int size) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(colors.text);
        t.setPadding(dp(4), dp(6), dp(4), dp(6));
        return t;
    }

    private void dismissErrorNotice() {
        session.clearError();
        handler.removeCallbacks(hideNotice);
        if (noticeView != null) noticeView.setVisibility(View.GONE);
    }

    private void execute(String key) {
        execute(Command.key(key));
    }

    private void execute(Command key) {
        if (!key.is(Command.RESTORE)) dismissErrorNotice();
        if (key.is(Command.PASTE)) {
            pasteClipboard();
            return;
        }
        if (key.is(Command.CANCEL)
                || (key.is(Command.UNDO)
                        && session.busy
                        && (session.operationPending
                                || SystemClock.elapsedRealtime() - session.busySince >= 250))) {
            cancelCalculation();
            return;
        }
        if (key.kind == Command.Kind.KEY && executeSelectionAction(key.wire)) return;
        if (!key.preservesSelection()) clearStackSelection(false);
        session.enqueue(key);
        if (!key.isEntryUpdate()) refresh();
    }

    private void pasteClipboard() {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) {
            showNotice(getString(R.string.clipboard_no_number), true);
            return;
        }
        CharSequence text = clip.getItemAt(0).getText();
        int limit =
                text.length() > RpnCalculator.MAX_PASTE_LENGTH
                        ? RpnCalculator.MAX_PASTE_LENGTH
                        : text.toString().split("\\R", -1).length > 1
                                ? RpnCalculator.MAX_PASTE_LENGTH
                                : EntryEditor.MAX_ENTRY_LENGTH;
        if (text.length() > limit) {
            showNotice(getString(R.string.clipboard_too_long, limit), true);
            return;
        }
        execute(Command.paste(text.toString()));
    }

    private void copyValue(String text, NumberMode mode) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                        getString(R.string.clipboard_label), numberFormat(mode).copy(text)));
        if (Build.VERSION.SDK_INT <= 32) showNotice(getString(R.string.copied), false);
    }

    private void cancelCalculation() {
        session.cancel();
        refresh();
    }

    private void handleBack() {
        dismissErrorNotice();
        if (session.busy) cancelCalculation();
        else if (!session.selectedRows.isEmpty()) clearStackSelection(true);
        else if (session.display.editing) execute("CANCEL_ENTRY");
        else finish();
    }

    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (!session.background) session.onForeground();
                    refresh();
                    handler.postDelayed(this, 100);
                }
            };

    private void clearStackSelection(boolean redraw) {
        if (session.selectedRows.isEmpty()) return;
        session.selectedRows.clear();
        selectionDirty = true;
        if (redraw) refresh();
    }

    private boolean stackActionAllowed(Display snapshot) {
        if (session.busy) {
            showNotice(getString(R.string.wait_calculation), true);
            return false;
        }
        if (!Objects.equals(session.display.stackId, snapshot.stackId)) {
            showNotice(getString(R.string.stack_changed), true);
            refresh();
            return false;
        }
        return true;
    }

    private boolean toggleStackRow(int index, Display snapshot) {
        if (session.display.editing || snapshot.editing || !stackActionAllowed(snapshot))
            return false;
        int scrollY = scroll.getScrollY();
        View oldRow = stackRows.findViewWithTag("stack-row:" + index);
        int anchorCenter = oldRow == null ? 0 : oldRow.getTop() + oldRow.getHeight() / 2 - scrollY;
        if (!session.selectedRows.remove(index)) session.selectedRows.add(index);
        selectionDirty = true;
        refresh();
        scroll.post(
                () -> {
                    View newRow = stackRows.findViewWithTag("stack-row:" + index);
                    scroll.scrollTo(
                            0,
                            oldRow == null || newRow == null
                                    ? scrollY
                                    : newRow.getTop() + newRow.getHeight() / 2 - anchorCenter);
                });
        return true;
    }

    private void stackRowTapped(int index, Display snapshot) {
        if (!stackActionAllowed(snapshot)) return;
        session.copyRows(new int[] {index}, text -> copyValue(text, snapshot.numberMode));
    }

    private boolean executeSelectionAction(String key) {
        if (session.selectedRows.isEmpty()) return false;
        Command.Kind action;
        Operation operation = Operation.find(key);
        switch (key) {
            case Command.UNDO:
                if (stackActionAllowed(session.display)) clearStackSelection(true);
                return true;
            case "SMART_CLEAR":
            case "CLEAR":
                if (!stackActionAllowed(session.display)) return true;
                for (int i = 0; i < session.display.rows.size(); i++) session.selectedRows.add(i);
                selectionDirty = true;
                refresh();
                return true;
            case Command.ENTER:
                action = Command.Kind.PICK_ROWS;
                break;
            case "BACK":
            case "DROP":
            case "DELETE":
                action = Command.Kind.DROP_ROWS;
                break;
            case "+":
                action = Command.Kind.SUM_ROWS;
                break;
            case "×":
                action = Command.Kind.PRODUCT_ROWS;
                break;
            case "HOLD_DROP":
            case "ROLL_UP":
                return true;
            default:
                if (operation != null && operation.arity == 1) {
                    action = Command.Kind.UNARY_ROWS;
                    break;
                }
                if (key.equals("Factor")
                        || key.equals("SWAP")
                        || operation != null && operation.arity == 2) {
                    showNotice(getString(R.string.selection_operation_unavailable), true);
                    return true;
                }
                return false;
        }
        if (!stackActionAllowed(session.display)) return true;
        int[] rows = session.selectedRows.stream().mapToInt(Integer::intValue).toArray();
        session.enqueue(
                action == Command.Kind.UNARY_ROWS
                        ? Command.unaryRows(operation, rows)
                        : Command.rows(action, rows));
        refresh();
        return true;
    }

    private void saveDisplayPreferences(Display display) {
        if (!session.canUseSavedState()) return;
        android.content.SharedPreferences preferences = getPreferences(0);
        android.content.SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        if (!display.angleUnit.name().equals(preferences.getString("angleUnit", ""))) {
            editor.putString("angleUnit", display.angleUnit.name());
            changed = true;
        }
        boolean engineering = display.notation == ValueFormatter.Notation.ENGINEERING;
        if (preferences.getBoolean("engineering", false) != engineering) {
            editor.putBoolean("engineering", engineering);
            changed = true;
        }
        if (preferences.getInt("fractionMode", -1) != display.fractionMode.ordinal()) {
            editor.putInt("fractionMode", display.fractionMode.ordinal());
            changed = true;
        }
        if (preferences.getInt("numberMode", -1) != display.numberMode.ordinal()) {
            editor.putInt("numberMode", display.numberMode.ordinal());
            changed = true;
        }
        if (changed) editor.apply();
    }

    private void refresh() {
        Display display = session.display;
        if (display.editing) clearStackSelection(false);
        if (rendered != display || selectionDirty) {
            numbers = numberFormat(display.numberMode);
            keys.setNumberFormat(numbers);
            boolean stackChanged =
                    selectionDirty
                            || rendered == null
                            || !Objects.equals(rendered.stackId, display.stackId)
                            || rendered.numberMode != display.numberMode
                            || rendered.editing != display.editing
                            || !rendered.rows.equals(display.rows);
            selectionDirty = false;
            if (stackChanged) {
                rowRenderer.render(stackRows, display, numbers, session.selectedRows);
            }

            refreshEntry(display);
            refreshModes(display);
            saveDisplayPreferences(display);
            boolean follow =
                    rendered == null
                            || rendered.editing != display.editing
                            || !rendered.rows.equals(display.rows);
            rendered = display;
            // A second post runs after the updated rows have been measured. Do not repeatedly
            // scroll on polling ticks: the user must still be able to inspect older entries.
            if (follow) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        refreshProgress(display);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int unicode = event.getUnicodeChar();
        if (unicode == numbers.decimalSeparator()) unicode = '.';
        String key =
                KeyboardInput.command(
                        keyCode,
                        unicode,
                        event.isCtrlPressed(),
                        event.isAltPressed(),
                        event.isMetaPressed());
        if (key == null) return super.dispatchKeyEvent(event);
        boolean navigation =
                key.equals("LEFT")
                        || key.equals("RIGHT")
                        || key.equals("HOME")
                        || key.equals("END");
        if (navigation) {
            if (!session.display.editing) return super.dispatchKeyEvent(event);
            if (event.isShiftPressed()) key = "SELECT_" + key;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (key.equals("ESCAPE")) {
            dismissErrorNotice();
            if (session.busy) cancelCalculation();
            else if (!session.selectedRows.isEmpty()) clearStackSelection(true);
            else execute("CANCEL_ENTRY");
        } else execute(key);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        session.background = false;
        session.onForeground();
    }

    @Override
    protected void onStop() {
        if (!isChangingConfigurations()) {
            session.background = true;
            if (!session.temporary && DeviceAccess.locked(this)) session.pauseForLock();
            else session.saveForLifecycle();
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(hideNotice);
        hideNotice.run();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if (isFinishing()) {
            session.close();
        }
        super.onDestroy();
    }

    private void configureWindow() {
        // Keep cursor/selection and hardware keys, but never connect this window to an IME.
        // setShowSoftInputOnFocus alone does not prevent restoration after another app closes.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        // Own the safe-area padding explicitly rather than combining decor fitting and app insets.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            getWindow().getDecorView().getSystemUiVisibility()
                                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams window = getWindow().getAttributes();
            window.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(window);
        }
    }

    protected boolean temporaryLaunch() {
        return false;
    }

    private boolean restoreSession() {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        localNumbers = new LocaleNumbers(locale);
        computerNumbers = new LocaleNumbers(locale, NumberMode.DOT);
        ungroupedNumbers = new LocaleNumbers(locale, NumberMode.UNGROUPED);
        session = (CalculatorSession) getLastNonConfigurationInstance();
        boolean restoreSession = session == null;
        if (session == null) {
            if (temporaryLaunch() || DeviceAccess.locked(this))
                session =
                        new CalculatorSession(
                                ValueFormatter.Notation.SCIENTIFIC,
                                RpnCalculator.AngleUnit.RAD,
                                NumberMode.LOCALE,
                                FractionMode.DECIMAL,
                                true);
            else {
                session =
                        new CalculatorSession(
                                getPreferences(0).getBoolean("engineering", false)
                                        ? ValueFormatter.Notation.ENGINEERING
                                        : ValueFormatter.Notation.SCIENTIFIC,
                                RpnCalculator.AngleUnit.valueOf(
                                        getPreferences(0).getString("angleUnit", "RAD")),
                                NumberMode.values()[getPreferences(0).getInt("numberMode", 0)],
                                FractionMode.values()[getPreferences(0).getInt("fractionMode", 0)]);
                session.tauConstant = getPreferences(0).getBoolean("tauConstant", false);
            }
        }
        session.attach(this);
        numbers = numberFormat(session.display.numberMode);
        return restoreSession;
    }

    private LinearLayout createRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(dp(16), dp(4), dp(16), dp(12));
        root.setBackgroundColor(colors.background);
        root.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    int left, top, right, bottom;
                    if (Build.VERSION.SDK_INT >= 30) {
                        // getInsets unions the types, taking the maximum per edge rather than
                        // adding them.
                        android.graphics.Insets safe =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        left = safe.left;
                        top = safe.top;
                        right = safe.right;
                        bottom = safe.bottom;
                    } else {
                        left = insets.getSystemWindowInsetLeft();
                        top = insets.getSystemWindowInsetTop();
                        right = insets.getSystemWindowInsetRight();
                        bottom = insets.getSystemWindowInsetBottom();
                        if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                            DisplayCutout cutout = insets.getDisplayCutout();
                            left = Math.max(left, cutout.getSafeInsetLeft());
                            top = Math.max(top, cutout.getSafeInsetTop());
                            right = Math.max(right, cutout.getSafeInsetRight());
                            bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                        }
                    }
                    root.setPadding(dp(16) + left, dp(4) + top, dp(16) + right, dp(12) + bottom);
                    positionNotice(root);
                    return insets;
                });
        return root;
    }

    private LinearLayout modeHeader() {
        LinearLayout header =
                new LinearLayout(this) {
                    @Override
                    protected void onMeasure(int widthSpec, int heightSpec) {
                        int width = View.MeasureSpec.getSize(widthSpec);
                        int menuWidth = Math.min(dp(48), width / 6);
                        int buttonWidth = Math.min(dp(54), (width - menuWidth) / 5);
                        for (int i = 0; i < getChildCount(); i++)
                            if (getChildAt(i) instanceof Button)
                                getChildAt(i).getLayoutParams().width =
                                        "overflow-menu".equals(getChildAt(i).getTag())
                                                ? menuWidth
                                                : buttonWidth;
                        super.onMeasure(widthSpec, heightSpec);
                    }
                };
        return header;
    }

    private Button modeButton(LinearLayout header, String command) {
        Button button = new Button(this);
        styleModeButton(button);
        button.setOnClickListener(v -> execute(command));
        header.addView(button, new LinearLayout.LayoutParams(dp(54), dp(48)));
        return button;
    }

    private void addModeButtons(LinearLayout root) {
        LinearLayout header = modeHeader();
        header.setTag("mode-row");
        header.setGravity(Gravity.CENTER_VERTICAL);
        angleButton = modeButton(header, "ANGLE");
        notationButton = modeButton(header, "FORMAT");
        numberFormatButton = modeButton(header, "NUMBER_FORMAT");
        fractionButton = modeButton(header, "FRACTION");
        status = text(12);
        status.setTag("calculation-status");
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(overflowMenu(), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);
    }

    private Button overflowMenu() {
        Button menu = new Button(this);
        menu.setTag("overflow-menu");
        styleModeButton(menu);
        menu.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable mask =
                new android.graphics.drawable.GradientDrawable();
        mask.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable active =
                new android.graphics.drawable.GradientDrawable();
        active.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        active.setColor(colors.ripple);
        android.graphics.drawable.StateListDrawable surface =
                new android.graphics.drawable.StateListDrawable();
        surface.addState(new int[] {android.R.attr.state_activated}, active);
        surface.addState(
                new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        menu.setBackground(
                new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(colors.ripple), surface, mask));
        menu.setStateListAnimator(null);
        menu.setElevation(0);
        menu.setText("");
        menu.setContentDescription(getString(R.string.more_options));
        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.ic_more);
        icon.setTint(colors.text);
        menu.setForegroundGravity(Gravity.CENTER);
        menu.setForeground(icon);
        menu.setOnClickListener(v -> showMenu(menu));
        return menu;
    }

    private void menuItem(android.widget.PopupMenu menu, int label, Runnable action) {
        menu.getMenu()
                .add(label)
                .setOnMenuItemClickListener(
                        item -> {
                            action.run();
                            return true;
                        });
    }

    private void showMenu(Button button) {
        dismissErrorNotice();
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, button);
        menuItem(menu, R.string.help, () -> AboutDialog.show(this, true));
        menuItem(menu, R.string.licenses, () -> AboutDialog.show(this, false));
        menuItem(menu, R.string.source_code, () -> AboutDialog.source(this));
        menu.setOnDismissListener(dismissed -> button.setActivated(false));
        menu.show();
        button.setActivated(true);
    }

    private void addEntry(LinearLayout root) {
        entryView = new EntryField(this);
        entryView.setTag("number-entry");
        entryView.setTextSize(32);
        entryView.setGravity(Gravity.START);
        digits(entryView);
        entryView.setPadding(dp(4), dp(6), dp(4), dp(6));
        entryView.setBackground(null);
        entryView.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        digits(entryView);
        entryView.setSingleLine(true);
        entryView.setHint("▸");
        entryView.setHintTextColor(colors.muted);
        entryView.setShowSoftInputOnFocus(false);
        entryView.paste = () -> execute(Command.PASTE);
        entryView.selectionChanged =
                (start, end) -> {
                    if (!changingEntry && start >= 0 && end >= 0)
                        execute(Command.select(start, end));
                };
        entryView.addTextChangedListener(
                new android.text.TextWatcher() {
                    private int start, count;

                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        this.start = start;
                        this.count = count;
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable text) {
                        if (changingEntry) return;
                        numbers.styleEntry(text);
                        boolean explicit =
                                count == 1
                                        && start < text.length()
                                        && text.charAt(start) == numbers.decimalSeparator();
                        String value =
                                entryShowsRaw
                                        ? text.toString()
                                        : numbers.canonicalEntry(text.toString());
                        execute(
                                Command.edit(
                                        value,
                                        Math.max(0, entryView.getSelectionStart()),
                                        Math.max(0, entryView.getSelectionEnd()),
                                        entryShowsRaw,
                                        explicit));
                    }
                });
        root.addView(entryView);
    }

    private void installContent(LinearLayout root) {
        FrameLayout screen = new FrameLayout(this);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        noticeView = text(14);
        noticeView.setTag("calculation-notice");
        noticeView.setPadding(dp(12), dp(10), dp(12), dp(10));
        noticeView.setMaxLines(3);
        noticeView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        noticeView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        android.graphics.drawable.GradientDrawable noticeBackground =
                new android.graphics.drawable.GradientDrawable();
        noticeBackground.setColor(colors.numberKey);
        noticeBackground.setCornerRadius(dp(12));
        noticeView.setBackground(noticeBackground);
        noticeView.setElevation(dp(8));
        noticeView.setVisibility(View.GONE);
        screen.addView(noticeView, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        positionNotice(root);
        setContentView(screen);
        screen.requestApplyInsets();
    }

    private void refreshEntry(Display display) {
        changingEntry = true;
        try {
            entryShowsRaw = display.raw;
            entryView.setVisibility(display.editing ? View.VISIBLE : View.GONE);
            String entry = display.raw ? display.entry : numbers.entry(display.entry);
            if (!entryView.getText().toString().equals(entry)) entryView.setText(entry);
            numbers.styleEntry(entryView.getText());
            entryView.setTextColor(display.problem.isEmpty() ? colors.text : colors.error);
            if (Build.VERSION.SDK_INT >= 30)
                entryView.setStateDescription(
                        !display.problem.isEmpty()
                                ? getString(
                                        display.problem.equals("Ambiguous")
                                                ? R.string.ambiguous_entry
                                                : R.string.invalid_entry)
                                : getString(R.string.number_entry));
            if (display.editing) {
                if (!entryView.hasFocus()) entryView.requestFocus();
                int start = Math.min(entry.length(), display.start),
                        end = Math.min(entry.length(), display.end);
                if (entryView.getSelectionStart() != start || entryView.getSelectionEnd() != end)
                    entryView.setSelection(start, end);
            }
        } finally {
            changingEntry = false;
        }
    }

    private void refreshModes(Display display) {
        keys.setMode(session.mode(display));
        angleButton.setText(display.angleUnit.name());
        angleButton.setContentDescription(
                getString(R.string.angle_description, display.angleUnit.name()));
        notationButton.setText(
                display.notation == ValueFormatter.Notation.ENGINEERING ? "ENG" : "SCI");
        notationButton.setContentDescription(
                getString(
                        R.string.notation_description,
                        getString(
                                display.notation == ValueFormatter.Notation.ENGINEERING
                                        ? R.string.engineering
                                        : R.string.scientific)));
        fractionButton.setText(
                new String[] {"DEC", "FRAC", "IMPR"}[display.fractionMode.ordinal()]);
        fractionButton.setContentDescription(
                new String[] {
                            getString(R.string.decimal_description),
                            getString(R.string.mixed_description),
                            getString(R.string.improper_description)
                        }
                        [display.fractionMode.ordinal()]);
        numberFormatButton.setText(
                new String[] {"LOC", "DOT", "NONE"}[display.numberMode.ordinal()]);
        numberFormatButton.setContentDescription(
                new String[] {
                            getString(R.string.locale_description),
                            getString(R.string.dot_description),
                            getString(R.string.ungrouped_description)
                        }
                        [display.numberMode.ordinal()]);
    }

    private void refreshProgress(Display display) {
        // Brief input evaluations keep Undo visible; longer work turns the same key into Cancel.
        boolean showProgress =
                session.busy
                        && (session.cancelRequested
                                || session.operationPending
                                || SystemClock.elapsedRealtime() - session.busySince >= 250);
        status.setText(
                showProgress
                        ? (session.cancelRequested
                                ? getString(R.string.cancelling)
                                : getString(R.string.working))
                        : !display.problem.isEmpty()
                                ? getString(
                                        display.problem.equals("Ambiguous")
                                                ? R.string.ambiguous_status
                                                : R.string.invalid_status)
                                : getString(
                                        switch (session.mode(display)) {
                                            case ENTRY -> R.string.entry_status;
                                            case STACK -> R.string.stack_status;
                                            case SELECT -> R.string.select_status;
                                        }));
        CharSequence calculationStatus = status.getText();
        boolean temporarySelection =
                session.temporary && session.mode(display) == InteractionMode.SELECT;
        if (status.getMaxLines() != (temporarySelection ? 2 : 1)) {
            status.setSingleLine(!temporarySelection);
            if (temporarySelection) status.setMaxLines(2);
        }
        if (session.temporary)
            status.setText(
                    getString(R.string.temporary_status)
                            + (temporarySelection ? "\n" + getString(R.string.select_status) : ""));
        status.setTextColor(session.temporary ? colors.accent : colors.text);
        status.setContentDescription(
                session.temporary
                        ? getString(R.string.temporary_description, calculationStatus)
                        : status.getText());
        long errorRevision = session.errorRevision;
        if (!session.error.isEmpty() && errorRevision != shownErrorRevision)
            showNotice(session.error, true);
        shownErrorRevision = errorRevision;
        keys.setCalculating(showProgress);
    }
}
