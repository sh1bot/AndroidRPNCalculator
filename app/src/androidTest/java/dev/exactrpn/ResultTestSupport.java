// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import java.util.ArrayList;
import java.util.List;

final class ResultTestSupport {
    static List<String> rows(DisplayResult result) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < result.values.size(); i++)
            rows.add(
                    ValueFormatter.format(
                            result.values.get(i),
                            i == result.values.size() - 1,
                            ValueFormatter.Notation.SCIENTIFIC));
        return rows;
    }

    static String copy(DisplayResult result) {
        java.util.StringJoiner text = new java.util.StringJoiner("\n");
        for (NumericValue value : result.values)
            text.add(ValueFormatter.clipboard(value, ValueFormatter.Notation.SCIENTIFIC));
        return text.toString();
    }
}
