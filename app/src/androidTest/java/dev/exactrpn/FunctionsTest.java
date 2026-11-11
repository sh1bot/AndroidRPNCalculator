// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class FunctionsTest {
    private CalculationRequest request(String key, byte[] input) throws java.io.IOException {
        return request(Command.key(key), input);
    }

    private CalculationRequest request(Command key, byte[] input) throws java.io.IOException {
        CalculationRequest request = new CalculationRequest(key);
        request.input = input;
        if (key.kind == Command.Kind.READ_VALUES) request.numbers = NumericRequest.PRECISE;
        if (input != null
                && !key.is("RESTORE")
                && key.kind != Command.Kind.READ_VALUES
                && !key.isDisplayOnly())
            request.entryDraft =
                    RpnCalculator.load(input).detachEntryEditor().entryForCommit().save();
        request.angle = RpnCalculator.AngleUnit.RAD;

        return request;
    }

    @Test
    public void keyDescriptionsCoverTheCatalog() {
        android.content.res.Resources resources =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
        String[] descriptions = resources.getStringArray(R.array.key_descriptions);
        assertEquals(CalculatorKey.values().length, descriptions.length);
        assertEquals(
                resources.getString(R.string.reciprocal_description),
                descriptions[CalculatorKey.RECIPROCAL.ordinal()]);
        assertEquals(
                resources.getString(R.string.pi_description),
                descriptions[CalculatorKey.PI.ordinal()]);
        assertEquals("", descriptions[CalculatorKey.SIN.ordinal()]);
    }

    @Test
    public void scientificDisplayStylesPowersAndKeepsEntryAndCopyLiteral() {
        for (Locale locale :
                new Locale[] {Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-EG")}) {
            LocaleNumbers numbers = new LocaleNumbers(locale, NumberMode.LOCALE);
            for (String input : new String[] {"1.23e+15", "123.4e-12", "1.2E+30"}) {
                android.text.Spanned styled = (android.text.Spanned) numbers.stack(input, 0);
                int marker = Math.max(input.indexOf('e'), input.indexOf('E'));
                assertEquals(
                        numbers.entry(input.substring(0, marker))
                                + "\u2009×"
                                + numbers.entry("10")
                                + numbers.entry(input.substring(marker + 1).replace('-', '−')),
                        styled.toString());
                android.text.style.SuperscriptSpan[] powers =
                        styled.getSpans(
                                0, styled.length(), android.text.style.SuperscriptSpan.class);
                assertEquals(1, powers.length);
                android.text.style.TypefaceSpan[] markerFaces =
                        styled.getSpans(
                                0, styled.length(), android.text.style.TypefaceSpan.class);
                assertEquals(1, markerFaces.length);
                assertEquals("sans-serif", markerFaces[0].getFamily());
                assertEquals(
                        "\u2009×",
                        styled.subSequence(
                                        styled.getSpanStart(markerFaces[0]),
                                        styled.getSpanEnd(markerFaces[0]))
                                .toString());
                assertEquals(
                        numbers.entry(input.substring(marker + 1).replace('-', '−')),
                        styled.subSequence(
                                        styled.getSpanStart(powers[0]),
                                        styled.getSpanEnd(powers[0]))
                                .toString());
                android.text.SpannableStringBuilder entry =
                        new android.text.SpannableStringBuilder(numbers.entry(input));
                numbers.styleEntry(entry);
                numbers.styleEntry(
                        entry); // Re-rendering replaces our spans rather than accumulating them.
                assertEquals(numbers.entry(input), entry.toString());
                ExponentMarkerSpan[] markers =
                        entry.getSpans(0, entry.length(), ExponentMarkerSpan.class);
                assertEquals(1, markers.length);
                assertEquals(marker, entry.getSpanStart(markers[0]));
                assertEquals(marker + 1, entry.getSpanEnd(markers[0]));
                android.text.style.SuperscriptSpan[] entryPowers =
                        entry.getSpans(0, entry.length(), android.text.style.SuperscriptSpan.class);
                assertEquals(1, entryPowers.length);
                assertEquals(marker + 1, entry.getSpanStart(entryPowers[0]));
                assertEquals(numbers.entry(input), numbers.copy(input));
                assertTrue(numbers.entry(input).contains(input.substring(marker, marker + 1)));
            }
            android.text.Spanned bound =
                    (android.text.Spanned) numbers.stack("≈ 0 (|x| < 1e−1000)", 0);
            assertEquals(
                    1,
                    bound.getSpans(0, bound.length(), android.text.style.SuperscriptSpan.class)
                            .length);
            assertTrue(bound.toString().endsWith(")"));
        }
    }

    @Test
    public void dozenalDigitsKeepTheirCharactersAndInstallFontFallbacks() {
        LocaleNumbers numbers = new LocaleNumbers(Locale.US, NumberMode.DOT, RadixMode.DOZENAL);
        android.text.Spanned stack = (android.text.Spanned) numbers.stack("↊↋", 0);
        assertEquals("↊↋", stack.toString());
        assertEquals(2, stack.getSpans(0, stack.length(), TurnedDigitSpan.class).length);

        android.text.SpannableString entry = new android.text.SpannableString(numbers.entry("AB"));
        numbers.styleEntry(entry);
        numbers.styleEntry(entry);
        assertEquals("↊↋", entry.toString());
        assertEquals(2, entry.getSpans(0, entry.length(), TurnedDigitSpan.class).length);
        assertEquals("AB", numbers.copy("AB"));

        LocaleNumbers ungrouped =
                new LocaleNumbers(Locale.US, NumberMode.UNGROUPED, RadixMode.DECIMAL);
        assertEquals(
                "1\u20602\u20603\u200b4\u20605\u20606\u2060.\u20601\u20602\u20603\u200b4\u20605\u20606",
                ungrouped.breakable(ungrouped.stack("123456.123456", 0)).toString());
        LocaleNumbers grouped = new LocaleNumbers(Locale.US, NumberMode.DOT, RadixMode.DECIMAL);
        assertEquals(
                "1\u20602\u20603\u2060\u2009\u200b4\u20605\u20606\u2060.\u20601\u20602\u20603\u200b4\u20605\u20606",
                grouped.breakable(grouped.stack("123456.123456", 0)).toString());
        android.text.Spanned groupedDisplay = (android.text.Spanned) grouped.stack("123456", 0);
        GroupingSeparatorSpan[] groupingSpans =
                groupedDisplay.getSpans(0, groupedDisplay.length(), GroupingSeparatorSpan.class);
        assertEquals(1, groupingSpans.length);
        android.text.TextPaint monospace = new android.text.TextPaint();
        monospace.setTypeface(android.graphics.Typeface.MONOSPACE);
        monospace.setTextSize(40);
        assertTrue(
                groupingSpans[0].getSize(monospace, groupedDisplay, 3, 4, null)
                        < monospace.measureText("0"));
        LocaleNumbers hexadecimal =
                new LocaleNumbers(Locale.US, NumberMode.UNGROUPED, RadixMode.HEX);
        assertEquals(
                "A\u2060B\u2060C\u2060D\u200bE\u2060F\u20600\u20601\u2060.\u20602\u20603\u20604\u20605\u200b6\u20607\u20608\u20609",
                hexadecimal.breakable(hexadecimal.stack("ABCDEF01.23456789", 0)).toString());
    }

    @Test
    public void stackedFractionUsesTheResolvedTextColour() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocaleNumbers numbers =
                new LocaleNumbers(Locale.US, NumberMode.UNGROUPED, RadixMode.DECIMAL);
        FractionTextView view = new FractionTextView(context);
        int textColor = android.graphics.Color.MAGENTA;
        view.setTextColor(textColor);
        view.setTextSize(24);
        view.stackedFraction(numbers.fractionParts("123456/789", textColor), "123456/789");
        view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                        240, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        0, android.view.View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                        view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        boolean found = false;
        for (int y = 0; !found && y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++)
                if ((bitmap.getPixel(x, y) & 0x00ffffff) == (textColor & 0x00ffffff)) {
                    found = true;
                    break;
                }
        assertTrue(found);
    }

    @Test
    public void balancingUsesTheMinimumLineCountAndWholeDigitGroups() {
        LocaleNumbers numbers =
                new LocaleNumbers(Locale.US, NumberMode.UNGROUPED, RadixMode.DECIMAL);
        CharSequence grouped =
                numbers.breakable(numbers.stack("111111111111111111", android.graphics.Color.GRAY));
        android.text.TextPaint paint = new android.text.TextPaint();
        paint.setTypeface(android.graphics.Typeface.MONOSPACE);
        paint.setTextSize(32);
        int width = (int) Math.ceil(android.text.Layout.getDesiredWidth("111111111", paint));
        String balanced = BalancedLines.apply(grouped, paint, width).toString();
        String[] lines = balanced.split("\n", -1);
        assertEquals(2, lines.length);
        for (String line : lines)
            assertEquals(9, line.replace("\u2060", "").length());
    }

    @Test
    public void mixedDisplayCopyAndFactorisationThroughRemoteProcess() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        File seed = File.createTempFile("functions-test-", ".state", context.getNoBackupFilesDir());
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        try {
            TestCalculator calculator = new TestCalculator();
            for (String key : new String[] {"7", "ENTER", "3", "÷"}) calculator.key(key);
            Files.write(seed.toPath(), calculator.save());
            DisplayResult improper =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "RESTORE",
                                                            Files.readAllBytes(seed.toPath()))))
                            .get(15, TimeUnit.SECONDS);
            NumericValue number = improper.values.get(0);
            assertEquals(
                    "7/3",
                    ValueFormatter.format(
                            number,
                            true,
                            ValueFormatter.Notation.SCIENTIFIC,
                            FractionMode.IMPROPER));
            assertEquals(
                    "2 1/3",
                    ValueFormatter.format(
                            number, true, ValueFormatter.Notation.SCIENTIFIC, FractionMode.MIXED));
            DisplayResult mixed = improper;
            DisplayResult copied =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            Command.rows(
                                                                    Command.Kind.READ_VALUES, 0),
                                                            mixed.state)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(
                    "2 1/3",
                    ValueFormatter.clipboard(
                            copied.values.get(0),
                            ValueFormatter.Notation.SCIENTIFIC,
                            FractionMode.MIXED));
            android.text.Spanned styled =
                    (android.text.Spanned)
                            new LocaleNumbers(Locale.US, NumberMode.DOT).stack("2 1/3", 0);
            assertEquals("2\u00a01/3", styled.toString());
            android.text.style.SuperscriptSpan[] raised =
                    styled.getSpans(0, styled.length(), android.text.style.SuperscriptSpan.class);
            android.text.style.SubscriptSpan[] lowered =
                    styled.getSpans(0, styled.length(), android.text.style.SubscriptSpan.class);
            assertEquals(1, raised.length);
            assertEquals(1, lowered.length);
            assertEquals(
                    "1",
                    styled.subSequence(styled.getSpanStart(raised[0]), styled.getSpanEnd(raised[0]))
                            .toString());
            assertEquals(
                    "3",
                    styled.subSequence(
                                    styled.getSpanStart(lowered[0]), styled.getSpanEnd(lowered[0]))
                            .toString());
            android.text.Spanned longFraction =
                    (android.text.Spanned)
                            new LocaleNumbers(Locale.US, NumberMode.UNGROUPED)
                                    .stack("123456789/12345", 0);
            android.text.style.SuperscriptSpan[] fullNumerator =
                    longFraction.getSpans(
                            0, longFraction.length(), android.text.style.SuperscriptSpan.class);
            android.text.style.SubscriptSpan[] fullDenominator =
                    longFraction.getSpans(
                            0, longFraction.length(), android.text.style.SubscriptSpan.class);
            assertEquals(
                    "123456789",
                    longFraction
                            .subSequence(
                                    longFraction.getSpanStart(fullNumerator[0]),
                                    longFraction.getSpanEnd(fullNumerator[0]))
                            .toString());
            assertEquals(
                    "12345",
                    longFraction
                            .subSequence(
                                    longFraction.getSpanStart(fullDenominator[0]),
                                    longFraction.getSpanEnd(fullDenominator[0]))
                            .toString());
            assertTrue(
                    TestCalculator.load(mixed.state)
                            .values()
                            .get(0)
                            .multiply(new com.android.calculator2.UnifiedReal(3))
                            .definitelyEquals(new com.android.calculator2.UnifiedReal(7)));
            calculator = new TestCalculator();
            CalculatorTestSupport.paste(calculator, "84");
            Files.write(seed.toPath(), calculator.save());
            DisplayResult factored =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "Factor",
                                                            Files.readAllBytes(seed.toPath()))))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(Arrays.asList("2", "2", "3", "7"), ResultTestSupport.rows(factored));
            DisplayResult undone =
                    worker.submit(() -> remote.evaluate(request("UNDO", factored.state)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals("84", EntryEditor.load(undone.entryState).entry());
            assertTrue(undone.values.isEmpty());
        } finally {
            remote.close();
            worker.shutdownNow();
            seed.delete();
        }
    }

    @Test
    public void inverseButtonsSendTheDisplayedFunction() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicReference<Command> command = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context, command::set, true, (expanded, inverse) -> {});
                            keypad.setInverse(true);
                            for (String[] example :
                                    new String[][] {
                                        {"asin", "asin"},
                                        {"acos", "acos"},
                                        {"atan", "atan"},
                                        {"eˣ", "eˣ"},
                                        {"x²", "x²"},
                                        {"ˣ√y", "root"},
                                        {"mod", "mod"},
                                        {"10ˣ", "10ˣ"},
                                        {"⌈x⌉", "ceil"}
                                    }) {
                                Button button = find(keypad, example[0]);
                                assertNotNull(example[0], button);
                                button.performClick();
                                assertEquals(example[1], command.get().wire);
                            }
                            Button drop = ((Button) keypad.findViewWithTag("BACK"));
                            drop.performLongClick();
                            assertEquals("ROLL_DOWN", command.get().wire);
                            drop.performClick();
                            assertEquals("DROP", command.get().wire);
                            Button swap = ((Button) keypad.findViewWithTag("SWAP"));
                            swap.performLongClick();
                            assertEquals("ROLL_UP", command.get().wire);
                            swap.performClick();
                            assertEquals("SWAP", command.get().wire);
                            Button decimal = (Button) keypad.findViewWithTag(".");
                            assertTrue(!decimal.isLongClickable());
                            keypad.setMode(InteractionMode.ENTRY);
                            assertTrue(decimal.isLongClickable());
                            decimal.performLongClick();
                            assertEquals("HOLD_DECIMAL", command.get().wire);
                            find(keypad, "⌫").performLongClick();
                            assertEquals("BACK", command.get().wire);
                            keypad.setInverse(false);
                            Button log = find(keypad, "log10");
                            assertNotNull(log);
                            android.text.Spanned label = (android.text.Spanned) log.getText();
                            android.text.style.SubscriptSpan[] bases =
                                    label.getSpans(
                                            0,
                                            label.length(),
                                            android.text.style.SubscriptSpan.class);
                            assertEquals(1, bases.length);
                            assertEquals(
                                    "10",
                                    label.subSequence(
                                                    label.getSpanStart(bases[0]),
                                                    label.getSpanEnd(bases[0]))
                                            .toString());
                            Button sine = find(keypad, "sin");
                            assertNotNull(sine);
                            sine.performClick();
                            assertEquals("sin", command.get().wire);
                        });
    }

    @Test
    public void gesturesUseTheFreshRoleWhenArithmeticFinishesBetweenFrames() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            AtomicReference<Command> command = new AtomicReference<>();
                            CalculatorKeypad keypad =
                                    new CalculatorKeypad(
                                            context,
                                            command::set,
                                            false,
                                            (expanded, inverse) -> {});
                            keypad.setMode(InteractionMode.ENTRY);
                            Button clear = (Button) keypad.findViewWithTag("CLEAR");
                            assertEquals("CE", clear.getText().toString());
                            keypad.setBeforeInput(() -> keypad.setMode(InteractionMode.STACK));
                            clear.performClick();
                            assertEquals("CLEAR", command.get().wire);
                            assertEquals("AC", clear.getText().toString());
                            keypad.setMode(InteractionMode.SELECT);
                            keypad.setCalculating(true);
                            keypad.setBeforeInput(() -> keypad.setCalculating(false));
                            Button undo = (Button) keypad.findViewWithTag("UNDO");
                            assertEquals("Cancel", undo.getText().toString());
                            undo.performClick();
                            assertEquals("UNDO", command.get().wire);
                            assertFalse(undo.isLongClickable());
                            keypad.setMode(InteractionMode.STACK);
                            keypad.setCalculating(true);
                            undo.performLongClick();
                            assertEquals("REDO", command.get().wire);
                        });
    }

    @Test
    public void remoteValidationUsesTheErrorCatalogue() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            DisplayResult result =
                    worker.submit(() -> remote.evaluate(request("+", null)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(CalculationError.Code.NEED_VALUES, result.operationError);
            assertEquals(2, result.errorArgument);
            assertTrue(result.values.isEmpty());
            assertTrue(result.state.length > 0);
        } finally {
            remote.close();
            worker.shutdownNow();
        }
    }

    private Button find(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button result = find(((ViewGroup) view).getChildAt(i), text);
                if (result != null) return result;
            }
        return null;
    }
}
