// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Offline source credits and verbatim licence texts, bundled from the repository at build time. */
public final class AboutDialog extends android.app.DialogFragment {
    static final String REPOSITORY = "https://github.com/sh1bot/AndroidRPNCalculator";
    static final String[] FILES = {"NOTICE.txt", "APACHE-2.0.txt", "CR-COPYRIGHT.txt"};

    static String read(Activity activity, String name) throws IOException {
        try (InputStream input = activity.getAssets().open("licenses/" + name);
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void show(Activity activity) {
        new AboutDialog().show(activity.getFragmentManager(), "about");
    }

    @Override
    public android.app.Dialog onCreateDialog(android.os.Bundle state) {
        if (getArguments() != null)
            return licenseDialog(
                    getActivity(),
                    getArguments().getInt("title"),
                    getArguments().getString("file"));
        return aboutDialog(getActivity());
    }

    private static android.app.Dialog aboutDialog(Activity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView description = new TextView(activity);
        description.setText(R.string.about_description);
        description.setTextSize(16);
        content.addView(description);
        Button repository = new Button(activity);
        repository.setText(R.string.github_repository);
        repository.setAllCaps(false);
        repository.setTag("about-repository");
        repository.setContentDescription(REPOSITORY);
        repository.setOnClickListener(
                v -> {
                    try {
                        activity.startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY)));
                    } catch (ActivityNotFoundException ex) {
                        new AlertDialog.Builder(activity)
                                .setMessage(REPOSITORY)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                });
        content.addView(repository);
        int[] labels = {
            R.string.source_credits, R.string.apache_license, R.string.constructive_real_notices
        };
        for (int i = 0; i < FILES.length; i++) {
            String file = FILES[i];
            int title = labels[i];
            Button button = new Button(activity);
            button.setAllCaps(false);
            button.setText(title);
            button.setTag("license:" + file);
            button.setOnClickListener(v -> showLicense(activity, title, file));
            content.addView(button);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        return new AlertDialog.Builder(activity)
                .setTitle(R.string.about)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    private static void showLicense(Activity activity, int title, String file) {
        AboutDialog dialog = new AboutDialog();
        android.os.Bundle args = new android.os.Bundle();
        args.putInt("title", title);
        args.putString("file", file);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), "license:" + file);
    }

    private static android.app.Dialog licenseDialog(Activity activity, int title, String file) {
        TextView text = new TextView(activity);
        text.setTag("license-text");
        text.setTextSize(16);
        text.setTextIsSelectable(true);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        try {
            text.setText(read(activity, file));
        } catch (IOException ex) {
            throw new IllegalStateException("Missing bundled licence: " + file, ex);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        return new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
