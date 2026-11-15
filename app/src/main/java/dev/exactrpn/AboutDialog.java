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
                    android.text.Html.fromHtml(
                            read(getActivity(), "docs.html"),
                            android.text.Html.FROM_HTML_MODE_LEGACY));
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
