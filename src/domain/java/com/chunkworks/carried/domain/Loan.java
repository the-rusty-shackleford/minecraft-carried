/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.domain;

/** What a store owes for a projectile lent to a weapon (D-0003): the store pays for exactly what
 * the lent copy lost, however the weapon took it (vanilla's {@code useAmmo} splits it; a modded
 * weapon may shrink it itself), and never more than the cell still holds. */
public final class Loan {
    private Loan() {}

    /**
     * requires: settled ≥ 0 (the copy's count when the loan was last settled), now ≥ 0 (its count
     * now), held ≥ 0 (what the lending cell still holds of the lent kind).
     * effects: how many to take from the cell: what the copy lost since the last settlement,
     * capped by what the cell holds; zero when the copy lost nothing or grew.
     * throws: IllegalArgumentException on a negative count.
     */
    public static int debit(int settled, int now, int held) {
        if (settled < 0 || now < 0 || held < 0) throw new IllegalArgumentException("counts ≥ 0");
        return Math.max(0, Math.min(settled - now, held));
    }
}
