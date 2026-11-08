// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
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
    private CalculationRequest request(String key, byte[] input, int fractionMode) {
        CalculationRequest request = new CalculationRequest(key);
        request.input = input;
        request.angle = RpnCalculator.AngleUnit.RAD;
        request.notation = ValueFormatter.Notation.SCIENTIFIC;
        request.locale = "en-US";
        request.fractionMode = FractionMode.fromId(fractionMode);
        return request;
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
    public void mixedDisplayCopyAndFactorisationThroughRemoteProcess() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        File seed = File.createTempFile("functions-test-", ".state", context.getNoBackupFilesDir());
        CalculatorStore store = new CalculatorStore(context.getNoBackupFilesDir());
        try {
            RpnCalculator calculator = new RpnCalculator();
            for (String key : new String[] {"7", "ENTER", "3", "÷"}) calculator.key(key);
            Files.write(seed.toPath(), calculator.save());
            Bundle improper =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "RESTORE",
                                                            Files.readAllBytes(seed.toPath()),
                                                            1)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals("7/3", improper.getStringArrayList("rows").get(0));
            Bundle mixed =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "FRACTION",
                                                            improper.getByteArray("state"),
                                                            0)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(2, mixed.getInt("fractionMode"));
            assertEquals("2 1/3", mixed.getStringArrayList("rows").get(0));
            Bundle copied =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "COPY_ROWS:0",
                                                            mixed.getByteArray("state"),
                                                            2)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals("2 1/3", copied.getString("copyText"));
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
                    RpnCalculator.load(mixed.getByteArray("state"))
                            .values()
                            .get(0)
                            .multiply(new com.android.calculator2.UnifiedReal(3))
                            .definitelyEquals(new com.android.calculator2.UnifiedReal(7)));
            calculator = new RpnCalculator();
            CalculatorTestSupport.paste(calculator, "84");
            Files.write(seed.toPath(), calculator.save());
            Bundle factored =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "Factor",
                                                            Files.readAllBytes(seed.toPath()),
                                                            0)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(Arrays.asList("2", "2", "3", "7"), factored.getStringArrayList("rows"));
            Bundle undone =
                    worker.submit(
                                    () ->
                                            remote.evaluate(
                                                    request(
                                                            "UNDO",
                                                            factored.getByteArray("state"),
                                                            0)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals("84", undone.getString("entry"));
            assertTrue(undone.getStringArrayList("rows").isEmpty());
        } finally {
            remote.close();
            worker.shutdownNow();
            seed.delete();
        }
    }

    @Test
    public void inverseButtonsSendTheDisplayedFunction() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicReference<String> command = new AtomicReference<>();
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
                                assertEquals(example[1], command.get());
                            }
                            Button drop = ((Button) keypad.findViewWithTag("BACK"));
                            drop.performLongClick();
                            assertEquals("HOLD_DROP", command.get());
                            drop.performClick();
                            assertEquals("BACK", command.get());
                            Button swap = ((Button) keypad.findViewWithTag("SWAP"));
                            swap.performLongClick();
                            assertEquals("ROLL_UP", command.get());
                            swap.performClick();
                            assertEquals("SWAP", command.get());
                            Button decimal = (Button) keypad.findViewWithTag(".");
                            assertTrue(!decimal.isLongClickable());
                            keypad.setEditing(true);
                            assertTrue(decimal.isLongClickable());
                            decimal.performLongClick();
                            assertEquals("HOLD_DECIMAL", command.get());
                            find(keypad, "⌫").performLongClick();
                            assertEquals("HOLD_DROP", command.get());
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
                            assertEquals("sin", command.get());
                        });
    }

    @Test
    public void remoteValidationUsesTheErrorCatalogue() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteCalculator remote = new RemoteCalculator(context);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Bundle result =
                    worker.submit(() -> remote.evaluate(request("+", null, 0)))
                            .get(15, TimeUnit.SECONDS);
            assertEquals(
                    context.getString(R.string.error_need_values, 2),
                    result.getString("operationError"));
            assertTrue(result.getStringArrayList("rows").isEmpty());
            assertTrue(result.getByteArray("state").length > 0);
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
