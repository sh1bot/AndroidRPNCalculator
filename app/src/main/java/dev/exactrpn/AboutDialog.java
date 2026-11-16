// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewTreeObserver;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Android streams APK-compressed documents into memory without extracting files. */
public final class AboutDialog extends DialogFragment {
    static final String REPOSITORY = "https://github.com/sh1bot/AndroidRPNCalculator";

    /** The three-dot menu's entry points into the one bundled document. */
    enum Section {
        ABOUT(R.string.about, null),
        HELP(R.string.help, "Using Exact RPN"),
        LICENSES(R.string.licenses, "Licenses and source credits"),
        PRIVACY(R.string.privacy, "Privacy Policy");

        final int title;
        final String heading;

        Section(int title, String heading) {
            this.title = title;
            this.heading = heading;
        }
    }

    static String read(Activity activity, String name) throws IOException {
        try (InputStream in = activity.getAssets().open(name);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void show(Activity activity, Section section) {
        AboutDialog dialog = new AboutDialog();
        Bundle args = new Bundle();
        args.putString("section", section.name());
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), section.name().toLowerCase(Locale.ROOT));
    }

    static void source(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY)));
        } catch (ActivityNotFoundException ex) {
            new AlertDialog.Builder(activity)
                    .setMessage(REPOSITORY)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    /**
     * Gives each {@code <tt>} run in the document a keycap look: fromHtml marks them with a
     * monospace TypefaceSpan, and a KeySpan draws a bordered, rounded background behind the
     * same range. The text itself is untouched, so selection, copying and the section
     * search still see the plain words.
     */
    private CharSequence styleKeys(CharSequence document) {
        android.text.Spannable styled = new android.text.SpannableString(document);
        boolean night =
                (getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        float density = getResources().getDisplayMetrics().density;
        for (android.text.style.TypefaceSpan span :
                styled.getSpans(0, styled.length(), android.text.style.TypefaceSpan.class)) {
            if (!"monospace".equals(span.getFamily())) continue;
            styled.setSpan(
                    new KeySpan(density, night),
                    styled.getSpanStart(span),
                    styled.getSpanEnd(span),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }

    /** Draws a key's text on a rounded, bordered chip in place of the plain run. */
    private static final class KeySpan extends android.text.style.ReplacementSpan {
        private final float density;
        private final int fill;
        private final int border;

        KeySpan(float density, boolean night) {
            this.density = density;
            fill = night ? 0x1AFFFFFF : 0x14000000;
            border = night ? 0xFF9AA0A6 : 0xFF7A7F87;
        }

        private android.graphics.Paint mono(android.graphics.Paint paint) {
            android.graphics.Paint copy = new android.graphics.Paint(paint);
            copy.setTypeface(android.graphics.Typeface.MONOSPACE);
            return copy;
        }

        @Override
        public int getSize(
                android.graphics.Paint paint,
                CharSequence text,
                int start,
                int end,
                android.graphics.Paint.FontMetricsInt fm) {
            if (fm != null) {
                int extra = Math.round(2.5f * density);
                fm.ascent -= extra;
                fm.descent += extra;
                fm.top -= extra;
                fm.bottom += extra;
            }
            return Math.round(mono(paint).measureText(text, start, end) + 11 * density);
        }

        @Override
        public void draw(
                android.graphics.Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                android.graphics.Paint paint) {
            android.graphics.Paint mono = mono(paint);
            float pad = 5.5f * density;
            float width = mono.measureText(text, start, end);
            android.graphics.Paint.FontMetrics metrics = mono.getFontMetrics();
            android.graphics.RectF chip =
                    new android.graphics.RectF(
                            x + 0.5f * density,
                            y + metrics.ascent - 2.5f * density,
                            x + width + 2 * pad - 0.5f * density,
                            y + metrics.descent + 2.5f * density);
            android.graphics.Paint fillPaint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(android.graphics.Paint.Style.FILL);
            fillPaint.setColor(fill);
            canvas.drawRoundRect(chip, 4 * density, 4 * density, fillPaint);
            android.graphics.Paint borderPaint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.6f * density);
            borderPaint.setColor(border);
            canvas.drawRoundRect(chip, 4 * density, 4 * density, borderPaint);
            canvas.drawText(text, start, end, x + pad, y, mono);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle state) {
        Section section = Section.valueOf(getArguments().getString("section"));
        TextView text = new TextView(getActivity());
        text.setTag("document-text");
        text.setTextSize(16);
        text.setTextIsSelectable(true);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        try {
            text.setText(
                    styleKeys(
                            android.text.Html.fromHtml(
                                    read(getActivity(), "docs.html"),
                                    android.text.Html.FROM_HTML_MODE_LEGACY)));
        } catch (IOException ex) {
            throw new IllegalStateException("Missing offline document", ex);
        }
        ScrollView scroll = new ScrollView(getActivity());
        scroll.addView(text);
        if (section.heading != null) {
            // Html.fromHtml has no anchor links, so scroll to the line the
            // section's heading starts on, once the text has been laid out.
            scroll.getViewTreeObserver()
                    .addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    scroll.getViewTreeObserver()
                                            .removeOnGlobalLayoutListener(this);
                                    android.text.Layout layout = text.getLayout();
                                    if (layout == null) return;
                                    int offset =
                                            text.getText().toString().indexOf(section.heading);
                                    if (offset < 0) return;
                                    int y =
                                            text.getPaddingTop()
                                                    + layout.getLineTop(
                                                            layout.getLineForOffset(offset));
                                    scroll.scrollTo(0, Math.max(0, y - pad / 2));
                                }
                            });
        }
        return new AlertDialog.Builder(getActivity())
                .setTitle(section.title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
