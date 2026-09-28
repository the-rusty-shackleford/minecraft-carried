/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Partitions for plan: want 0, less than the first cell, spanning cells, exactly the total, more
 * than the total; cells none, zeros between, a single cell; bad arguments. For upTo: most under,
 * equal to and over the total; nothing held. */
final class WithdrawalTest {
    private static final int[] HELD = {5, 0, 3, 10};

    @Test void aShortfallPlansNothing() {
        assertEquals(Optional.empty(), Withdrawal.plan(HELD, 19));
        assertEquals(Optional.empty(), Withdrawal.plan(new int[] {}, 1));
        assertEquals(Optional.empty(), Withdrawal.plan(new int[] {0, 0}, 1));
    }
    @Test void firstCellsEmptyFirst() {
        assertArrayEquals(new int[] {2, 0, 0, 0}, Withdrawal.plan(HELD, 2).orElseThrow(), "less than the first cell");
        assertArrayEquals(new int[] {5, 0, 2, 0}, Withdrawal.plan(HELD, 7).orElseThrow(), "spanning, skipping a zero");
        assertArrayEquals(new int[] {5, 0, 3, 10}, Withdrawal.plan(HELD, 18).orElseThrow(), "exactly the total");
        assertArrayEquals(new int[] {0, 0, 0, 0}, Withdrawal.plan(HELD, 0).orElseThrow(), "want nothing");
        assertArrayEquals(new int[] {}, Withdrawal.plan(new int[] {}, 0).orElseThrow(), "no cells, want nothing");
    }
    @Test void thePlanTotalsExactlyTheWant() {
        for (int want = 0; want <= 18; want++) {
            int sum = 0;
            for (int n : Withdrawal.plan(HELD, want).orElseThrow()) sum += n;
            assertEquals(want, sum);
        }
    }
    @Test void upToTakesWhatThereIs() {
        assertArrayEquals(new int[] {5, 0, 1, 0}, Withdrawal.upTo(HELD, 6));
        assertArrayEquals(new int[] {5, 0, 3, 10}, Withdrawal.upTo(HELD, 18));
        assertArrayEquals(new int[] {5, 0, 3, 10}, Withdrawal.upTo(HELD, 64));
        assertTrue(Withdrawal.upTo(new int[] {0, 0}, 5)[0] == 0);
    }
    @Test void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Withdrawal.plan(HELD, -1));
        assertThrows(IllegalArgumentException.class, () -> Withdrawal.plan(new int[] {1, -1}, 0));
        assertThrows(IllegalArgumentException.class, () -> Withdrawal.upTo(HELD, -1));
    }
}
