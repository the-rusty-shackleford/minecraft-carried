/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.domain;

import java.util.List;

/** Where a stack goes among a store's cells, the way the player's own inventory places one
 * (Backpacks+ D-0029, carried here by D-0001): one pass tops up the cells already holding that
 * kind, in order; the other fills empty cells, in order. The caller runs the passes around the
 * inventory's own: top up the inventory, top up the stores, fill the inventory's empty slots,
 * fill the stores' empty cells. */
public final class Placement {
    private Placement() {}

    /** A cell as the plan sees it.
     * <p>AF: a cell that is empty ({@code empty}), or holds {@code count} of some kind, the
     * incoming kind when {@code same}; a cell that refuses the incoming kind is {@code other(0)}.
     * RI: count ≥ 0; an empty cell has count 0 and is not same. */
    public record Held(boolean empty, boolean same, int count) {
        public Held {
            if (count < 0 || empty && (count != 0 || same)) throw new IllegalArgumentException("an empty cell holds nothing");
        }
        public static final Held EMPTY = new Held(true, false, 0);
        public static final Held REFUSED = new Held(false, false, 0);
        public static Held same(int count) { return new Held(false, true, count); }
        public static Held other(int count) { return new Held(false, false, count); }
    }

    /** Which of the two passes. */
    public enum Pass { TOP_UP, FILL_EMPTY }

    /**
     * requires: count ≥ 0, limit ≥ 1 (the most a cell may hold of the incoming kind).
     * effects: returns how many of {@code count} go into each cell under {@code pass}: TOP_UP adds
     * to cells holding the same kind, each up to {@code limit}; FILL_EMPTY puts up to
     * {@code limit} into each empty cell. Cells in order; the total is at most {@code count}.
     * throws: IllegalArgumentException on a negative count or a limit under one.
     */
    public static int[] place(List<Held> cells, int count, int limit, Pass pass) {
        if (count < 0 || limit < 1) throw new IllegalArgumentException("count ≥ 0, limit ≥ 1");
        int[] into = new int[cells.size()];
        int left = count;
        for (int i = 0; i < cells.size() && left > 0; i++) {
            Held c = cells.get(i);
            int room = pass == Pass.TOP_UP ? (c.same() ? limit - c.count() : 0) : (c.empty() ? limit : 0);
            int n = Math.max(0, Math.min(room, left));
            into[i] = n;
            left -= n;
        }
        return into;
    }
}
