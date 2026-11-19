package dev.exactrpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

import dev.exactrpn.MainActivity;

/**
 * Shoots the screenshot set for screenshots/ at the set's native geometry:
 * portrait 420x840dp and landscape 840x420dp at xxhdpi (1260x2520 /
 * 2520x1260 px), under Robolectric's native graphics. Not part of the test
 * suite: it is run by tools/screenshots/capture-screenshots.sh, which also
 * optimises the raw captures. The raw output directory is the system
 * property screenshot.dir, defaulting to build/screenshots-raw.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ScreenshotCapture {
    private static final File OUT =
            new File(System.getProperty("screenshot.dir", "build/screenshots-raw"));
    private static final String PORTRAIT = "w420dp-h840dp-xxhdpi";
    private static final String PORTRAIT_DARK = "w420dp-h840dp-night-xxhdpi";
    private static final String LANDSCAPE = "w840dp-h420dp-land-xxhdpi";
    private static final String LANDSCAPE_DARK = "w840dp-h420dp-land-night-xxhdpi";

    private Activity activity;
    private Field busyField;
    private Object sessionRef;

    private void awaitIdle() {
        try {
            if (busyField == null) {
                Field field = MainActivity.class.getDeclaredField("session");
                field.setAccessible(true);
                Object session = field.get(activity);
                busyField = session.getClass().getDeclaredField("busy");
                busyField.setAccessible(true);
                sessionRef = session;
            }
            for (int i = 0; i < 100 && busyField.getBoolean(sessionRef); i++) {
                Thread.sleep(50);
                ShadowLooper.idleMainLooper();
            }
        } catch (Exception ignored) {
        }
    }

    private void settle() {
        for (int i = 0; i < 4; i++) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void launch(String qualifiers) {
        RuntimeEnvironment.setQualifiers(qualifiers);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        activity =
                InstrumentationRegistry.getInstrumentation()
                        .startActivitySync(
                                new Intent(context, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settle();
    }

    private View tagged(String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void tap(String tag) {
        View view = tagged(tag);
        if (view == null) throw new AssertionError("no view tagged " + tag);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(view::performClick);
        settle();
        awaitIdle();
        settle();
    }

    private void longTap(String tag) {
        View view = tagged(tag);
        if (view == null) throw new AssertionError("no view tagged " + tag);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(view::performLongClick);
        settle();
        awaitIdle();
        settle();
    }

    private Button findButton(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text))
            return (Button) view;
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button found = findButton(((ViewGroup) view).getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void tapText(String text) {
        Button button = findButton(activity.getWindow().getDecorView(), text);
        if (button == null) throw new AssertionError("no button with text " + text);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(button::performClick);
        settle();
        awaitIdle();
        settle();
    }

    private void longTapText(String text) {
        Button button = findButton(activity.getWindow().getDecorView(), text);
        if (button == null) throw new AssertionError("no button with text " + text);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(button::performLongClick);
        settle();
        awaitIdle();
        settle();
    }

    private void type(String... keys) {
        for (String key : keys) tap(key);
    }

    private void awaitChecked(int row) {
        for (int i = 0; i < 50; i++) {
            boolean[] done = new boolean[1];
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(
                            () -> {
                                View box = tagged("stack-checkbox:" + row);
                                done[0] =
                                        box instanceof android.widget.CheckBox
                                                && ((android.widget.CheckBox) box).isChecked();
                            });
            if (done[0]) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        throw new AssertionError("row " + row + " never became checked");
    }

    private void waitForWork() {
        for (int i = 0; i < 60; i++) {
            String[] text = new String[1];
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(
                            () -> {
                                TextView status =
                                        (TextView)
                                                activity.getWindow()
                                                        .getDecorView()
                                                        .findViewWithTag("calculation-status");
                                text[0] = status == null ? "" : status.getText().toString();
                            });
            if (!text[0].startsWith("Worki")) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void shoot(String name) throws Exception {
        waitForWork();
        View decor = activity.getWindow().getDecorView();
        boolean landscape =
                activity.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (decor.getWidth() == 0) {
            int maxW = landscape ? 2520 : 1260;
            int maxH = landscape ? 1260 : 2520;
            decor.measure(
                    View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
            decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        }
        Bitmap bitmap =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        OUT.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(OUT, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        System.out.println("SHOT " + name + " " + decor.getWidth() + "x" + decor.getHeight());
    }

    // ---------- stacks ----------

    private void heroStack() {
        type("3", "ENTER", "4", "0", "xʸ"); // 12157665459056928801, exact
        type("2", "ENTER", "1", "0", "xʸ"); // 1024
        tap("π");
        type("7", "ENTER", "3", "÷");
    }

    private void fractionStack() {
        type("7", "ENTER", "3", "÷");
        type("2", "2", "ENTER", "7", "÷");
        type("7", "ENTER", "3", "÷", "±"); // negative, on top
    }

    private void hexStack() {
        type("A", "F", "ENTER", "1", "+"); // B0
        type("F", "ENTER", "F", "×"); // E1
    }

    // ---------- scenes ----------

    @Test
    public void hero() throws Exception {
        launch(PORTRAIT_DARK);
        heroStack();
        shoot("Calculator.png");
    }

    @Test
    public void mixedFraction() throws Exception {
        launch(PORTRAIT_DARK);
        tap("FRACTION"); // mixed
        fractionStack();
        shoot("Calculator-mixed-fraction.png");
    }

    @Test
    public void improperFraction() throws Exception {
        launch(PORTRAIT_DARK);
        tap("FRACTION");
        tap("FRACTION"); // improper
        tap("FORMAT"); // SCI -> ENG
        fractionStack();
        type("1", "2", "3", "4", "5", "6", "7", "8", "9", "ENTER");
        shoot("Calculator-improper-fraction.png");
    }

    @Test
    public void functions() throws Exception {
        launch(PORTRAIT_DARK);
        type("1", "2", "ENTER", "1", "8", "ENTER");
        tap("FUNCTIONS");
        shoot("Calculator-f.png");
    }

    @Test
    public void inverseFunctions() throws Exception {
        launch(PORTRAIT_DARK);
        type("1", "2", "ENTER", "1", "8", "ENTER");
        tap("FUNCTIONS");
        longTapText("π"); // pi -> tau
        tap("COMPLEMENTS");
        shoot("Calculator-finv.png");
    }

    @Test
    public void dozenal() throws Exception {
        launch(PORTRAIT_DARK);
        tap("RADIX");
        tap("RADIX"); // dozenal
        tapText("↊");
        tap("ENTER");
        tapText("↋");
        tap("+");
        tapText("↋");
        tap("ENTER");
        type("2", "÷");
        tap("FUNCTIONS"); // shows the constant log12 label
        shoot("Calculator-dozenal.png");
    }

    @Test
    public void hexLight() throws Exception {
        launch(PORTRAIT);
        tap("RADIX"); // hex
        hexStack();
        shoot("Calculator-hex-light-mode.png");
    }

    @Test
    public void hexDark() throws Exception {
        launch(PORTRAIT_DARK);
        tap("RADIX"); // hex
        hexStack();
        shoot("Calculator-hex.png");
    }

    private int checkedCount(int rows) {
        int[] count = new int[1];
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            int c = 0;
                            for (int r = 0; r < rows; r++) {
                                View box = tagged("stack-checkbox:" + r);
                                if (box instanceof android.widget.CheckBox
                                        && ((android.widget.CheckBox) box).isChecked()) c++;
                            }
                            count[0] = c;
                        });
        return count[0];
    }

    private boolean rowChecked(int row) {
        boolean[] state = new boolean[1];
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            View box = tagged("stack-checkbox:" + row);
                            state[0] =
                                    box instanceof android.widget.CheckBox
                                            && ((android.widget.CheckBox) box).isChecked();
                        });
        return state[0];
    }

    @Test
    public void selection() throws Exception {
        launch(PORTRAIT_DARK);
        heroStack();
        tap("stack-row:0");
        tap("stack-row:1");
        tap("stack-row:2");
        if (checkedCount(4) < 3)
            throw new AssertionError("only " + checkedCount(4) + " rows selected");
        tapText("ƒ()"); // the panel opens in selection mode; binary keys show dimmed
        // Guard against silently shooting a closed panel (the first delivery did):
        // a function key must be laid out before this frame is taken.
        View sin = tagged("sin");
        if (sin == null || sin.getVisibility() != View.VISIBLE || sin.getHeight() == 0)
            throw new AssertionError("function panel is not open in the selection scene");
        RuntimeEnvironment.getMasterScheduler()
                .advanceBy(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        ShadowLooper.idleMainLooper();
        settle();
        // Robolectric's native graphics does not propagate the checked state
        // into checkbox drawables set by the in-place selection update; the
        // model state is correct, so jump the drawables to it before shooting.
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (int r = 0; r < 4; r++) {
                                View box = tagged("stack-checkbox:" + r);
                                if (box != null) {
                                    box.jumpDrawablesToCurrentState();
                                    box.invalidate();
                                }
                            }
                        });
        settle();
        shoot("Calculator-selection.png");
    }

    @Test
    public void entryMode() throws Exception {
        launch(PORTRAIT_DARK);
        tap("RADIX");
        tap("RADIX"); // dozenal
        type("1", "ENTER");
        tapText("↊");
        type("5", ".");
        tapText("↋"); // entry left uncommitted: turned digits in the entry row
        shoot("Calculator-entry-mode.png");
    }

    @Test
    public void landscape() throws Exception {
        launch(LANDSCAPE_DARK);
        tap("FRACTION"); // mixed
        type("5", "ENTER", "2", "÷");
        type("7", "ENTER", "3", "÷");
        type("2", "2", "ENTER", "7", "÷");
        shoot("Calculator-landscape.png");
    }

    @Test
    public void landscapeFunctionsDark() throws Exception {
        launch(LANDSCAPE_DARK);
        tap("FRACTION"); // mixed
        type("5", "ENTER", "2", "÷");
        type("7", "ENTER", "3", "÷");
        type("2", "2", "ENTER", "7", "÷");
        tap("FUNCTIONS");
        shoot("Calculator-landscape-f.png");
    }
}
