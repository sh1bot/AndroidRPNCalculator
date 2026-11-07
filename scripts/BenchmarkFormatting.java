// SPDX-License-Identifier: Apache-2.0
// After scripts/test-engine.sh: java -cp engine/build/manual scripts/BenchmarkFormatting.java
// Warm desktop JVM comparison of the former three passes and repeated cached presentation.
// This excludes IPC, state serialization, disk, UI layout and first-time formatting costs.
import com.android.calculator2.UnifiedReal;

import dev.exactrpn.*;

import java.util.*;

public class BenchmarkFormatting {
    static volatile int sink;

    static void old(List<UnifiedReal> values) {
        int length = 0;
        for (int i = 0; i < values.size(); i++) {
            UnifiedReal v = values.get(i);
            length +=
                    ValueFormatter.format(
                                    v,
                                    i == values.size() - 1,
                                    ValueFormatter.Notation.SCIENTIFIC,
                                    FractionMode.DECIMAL)
                            .length();
            length +=
                    ValueFormatter.clipboard(
                                    v, ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL)
                            .length();
            length +=
                    ValueFormatter.format(
                                    v,
                                    false,
                                    ValueFormatter.Notation.SCIENTIFIC,
                                    FractionMode.DECIMAL)
                            .length();
        }
        sink = length;
    }

    static void cached(List<UnifiedReal> values, ValuePresentationCache cache) {
        cache.retain(values);
        int length = 0;
        for (int i = 0; i < values.size(); i++) {
            UnifiedReal v = values.get(i);
            length +=
                    cache.row(
                                    v,
                                    i == values.size() - 1,
                                    ValueFormatter.Notation.SCIENTIFIC,
                                    FractionMode.DECIMAL)
                            .length();
            length +=
                    cache.row(v, false, ValueFormatter.Notation.SCIENTIFIC, FractionMode.DECIMAL)
                            .length();
        }
        sink = length;
    }

    public static void main(String[] args) {
        for (int size : new int[] {50, 200}) {
            List<UnifiedReal> values = new ArrayList<>();
            for (int i = 2; i < size + 2; i++) values.add(new UnifiedReal(i).sqrt());
            ValuePresentationCache cache = new ValuePresentationCache();
            for (int i = 0; i < 20; i++) {
                old(values);
                cached(values, cache);
            }
            int repeats = 100;
            long start = System.nanoTime();
            for (int i = 0; i < repeats; i++) old(values);
            double before = (System.nanoTime() - start) / 1e6 / repeats;
            start = System.nanoTime();
            for (int i = 0; i < repeats; i++) cached(values, cache);
            double after = (System.nanoTime() - start) / 1e6 / repeats;
            System.out.printf(
                    Locale.ROOT,
                    "%d rows: old %.3f ms, cached %.3f ms per presentation%n",
                    size,
                    before,
                    after);
        }
    }
}
