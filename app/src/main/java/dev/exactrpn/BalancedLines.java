// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.Arrays;

/** Balance numeric groups across the minimum number of lines that can contain them. */
final class BalancedLines {
    private BalancedLines() {}

    static CharSequence apply(CharSequence source, TextPaint paint, int width) {
        ArrayList<Integer> breaks = new ArrayList<>();
        for (int i = 0; i < source.length(); i++)
            if (source.charAt(i) == '\u200b') breaks.add(i);
        if (breaks.isEmpty() || width <= 0) return source;

        int groups = breaks.size() + 1;
        float[] prefix = new float[groups + 1];
        int start = 0;
        for (int group = 0; group < groups; group++) {
            int end = group < breaks.size() ? breaks.get(group) : source.length();
            prefix[group + 1] =
                    prefix[group] + Layout.getDesiredWidth(source, start, end, paint);
            start = end + 1;
        }

        int lines = 0;
        for (int group = 0; group < groups; lines++) {
            int end = group + 1;
            while (end < groups && prefix[end + 1] - prefix[group] <= width) end++;
            group = end;
        }

        double target = prefix[groups] / lines;
        double[][] cost = new double[lines + 1][groups + 1];
        int[][] previous = new int[lines + 1][groups + 1];
        for (double[] row : cost) Arrays.fill(row, Double.POSITIVE_INFINITY);
        for (int[] row : previous) Arrays.fill(row, -1);
        cost[0][0] = 0;
        for (int line = 1; line <= lines; line++) {
            for (int end = line; end <= groups; end++) {
                for (int begin = line - 1; begin < end; begin++) {
                    float used = prefix[end] - prefix[begin];
                    if (used > width && end > begin + 1) continue;
                    double difference = used - target;
                    double candidate = cost[line - 1][begin] + difference * difference;
                    if (candidate < cost[line][end]) {
                        cost[line][end] = candidate;
                        previous[line][end] = begin;
                    }
                }
            }
        }

        boolean[] newline = new boolean[breaks.size()];
        int end = groups;
        for (int line = lines; line > 1; line--) {
            int begin = previous[line][end];
            if (begin <= 0) return source;
            newline[begin - 1] = true;
            end = begin;
        }
        SpannableStringBuilder result = new SpannableStringBuilder(source);
        for (int i = 0; i < breaks.size(); i++)
            result.replace(breaks.get(i), breaks.get(i) + 1, newline[i] ? "\n" : "\u2060");
        return result;
    }
}
