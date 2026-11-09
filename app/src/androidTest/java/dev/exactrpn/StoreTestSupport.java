// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** Inspect the storage fixture without adding a pathname API to production storage. */
final class StoreTestSupport {
    static String checkpoint(CalculatorStore store) throws Exception {
        // Exercise the same access policy as a normal read before inspecting the fixture.
        if (store.load() == null) return null;
        var field = CalculatorStore.class.getDeclaredField("file");
        field.setAccessible(true);
        java.io.File file = ((android.util.AtomicFile) field.get(store)).getBaseFile();
        return file.getAbsolutePath();
    }
}
