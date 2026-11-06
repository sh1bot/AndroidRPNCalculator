// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

/** A separate process keeps cancellation and cached values isolated from the normal calculator. */
public final class LockedCalculationService extends CalculationService {
    @Override
    protected boolean temporaryService() {
        return true;
    }
}
