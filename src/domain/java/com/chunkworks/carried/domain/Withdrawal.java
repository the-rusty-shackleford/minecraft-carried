/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.domain;

import java.util.Optional;

/** How a withdrawal is spread over the cells in reach (D-0001): first cells first, which the
 * caller orders inventory before stores, so a bag is the reserve. */
public final class Withdrawal {
    private Withdrawal() {}

    /**
     * requires: every held[i] ≥ 0 (how many of the wanted kind cell i holds; 0 when it holds
     * another kind or nothing), want ≥ 0.
     * effects: when the cells hold at least {@code want} in all, how many to take from each, first
     * cells emptied first, totalling exactly {@code want}; otherwise empty, so a caller that
     * applies only a present plan changes nothing on a shortfall (all or nothing).
     * throws: IllegalArgumentException on a negative count.
     */
    public static Optional<int[]> plan(int[] held, int want) {
        if (want < 0) throw new IllegalArgumentException("want ≥ 0");
        long total = 0;
        for (int h : held) {
            if (h < 0) throw new IllegalArgumentException("held ≥ 0");
            total += h;
        }
        if (total < want) return Optional.empty();
        int[] take = new int[held.length];
        int left = want;
        for (int i = 0; i < held.length && left > 0; i++) {
            take[i] = Math.min(held[i], left);
            left -= take[i];
        }
        return Optional.of(take);
    }

    /**
     * requires: every held[i] ≥ 0, most ≥ 0.
     * effects: as {@link #plan} for as many as the cells hold, up to {@code most}: never empty,
     * possibly all zeros.
     * throws: IllegalArgumentException on a negative count.
     */
    public static int[] upTo(int[] held, int most) {
        if (most < 0) throw new IllegalArgumentException("most ≥ 0");
        long total = 0;
        for (int h : held) {
            if (h < 0) throw new IllegalArgumentException("held ≥ 0");
            total += h;
        }
        return plan(held, (int) Math.min(total, most)).orElseThrow();
    }
}
