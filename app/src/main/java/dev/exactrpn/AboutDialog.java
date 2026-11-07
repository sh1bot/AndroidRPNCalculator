// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Offline documents are inflated directly into memory, never extracted to disk. */
public final class AboutDialog extends DialogFragment {
    static final String REPOSITORY = "https://github.com/sh1bot/AndroidRPNCalculator";

    static String read(Activity activity, String name) throws IOException {
        try (InputStream in = new GZIPInputStream(activity.getAssets().open(name));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void show(Activity activity, boolean help) {
        AboutDialog dialog = new AboutDialog();
        Bundle args = new Bundle();
        args.putBoolean("help", help);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), help ? "help" : "licenses");
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
        boolean help = getArguments().getBoolean("help");
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
                            read(getActivity(), help ? "help.html.gzip" : "licenses.html.gzip"),
                            android.text.Html.FROM_HTML_MODE_LEGACY));
        } catch (IOException ex) {
            throw new IllegalStateException("Missing offline document", ex);
        }
        ScrollView scroll = new ScrollView(getActivity());
        scroll.addView(text);
        return new AlertDialog.Builder(getActivity())
                .setTitle(help ? R.string.help : R.string.licenses)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
