/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chunkworks.carried.domain.Placement.Held;
import com.chunkworks.carried.domain.Placement.Pass;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Partitions: pass (top up, fill empty); count (0, less than the room, exactly the room, more);
 * cells (none, same kind partial or full, other kind, refused, empty, mixed in any order); limit 1
 * and 64; bad arguments. Moved from Backpacks+'s PickupPlanTest with the rule (D-0001). */
final class PlacementTest {
    private static final List<Held> MIXED = List.of(Held.EMPTY, Held.same(60), Held.other(3), Held.same(10), Held.EMPTY);

    @Test void topUpFillsTheSameKindInOrderAndNothingElse() {
        assertArrayEquals(new int[] {0, 4, 0, 16, 0}, Placement.place(MIXED, 20, 64, Pass.TOP_UP));
        assertArrayEquals(new int[] {0, 4, 0, 54, 0}, Placement.place(MIXED, 58, 64, Pass.TOP_UP), "exactly the room");
        assertArrayEquals(new int[] {0, 4, 0, 54, 0}, Placement.place(MIXED, 90, 64, Pass.TOP_UP), "more than the room: the rest stays");
        assertArrayEquals(new int[] {0, 3, 0, 0, 0}, Placement.place(MIXED, 3, 64, Pass.TOP_UP), "less than the first cell's room");
    }
    @Test void fillEmptyUsesOnlyEmptyCellsUpToTheLimit() {
        assertArrayEquals(new int[] {20, 0, 0, 0, 0}, Placement.place(MIXED, 20, 64, Pass.FILL_EMPTY));
        assertArrayEquals(new int[] {64, 0, 0, 0, 36}, Placement.place(MIXED, 100, 64, Pass.FILL_EMPTY));
        assertArrayEquals(new int[] {64, 0, 0, 0, 64}, Placement.place(MIXED, 200, 64, Pass.FILL_EMPTY));
        assertArrayEquals(new int[] {1, 0, 0, 0, 1}, Placement.place(MIXED, 5, 1, Pass.FILL_EMPTY), "unstackable: one a cell");
    }
    @Test void aRefusedCellTakesNothingInEitherPass() {
        var cells = List.of(Held.REFUSED, Held.EMPTY);
        assertArrayEquals(new int[] {0, 7}, Placement.place(cells, 7, 64, Pass.FILL_EMPTY));
        assertArrayEquals(new int[] {0, 0}, Placement.place(cells, 7, 64, Pass.TOP_UP));
    }
    @Test void aFullSameStackAndNoCellsTakeNothing() {
        assertArrayEquals(new int[] {0, 0}, Placement.place(List.of(Held.same(64), Held.other(64)), 10, 64, Pass.TOP_UP));
        assertArrayEquals(new int[] {0, 0}, Placement.place(List.of(Held.same(64), Held.other(64)), 10, 64, Pass.FILL_EMPTY));
        assertArrayEquals(new int[] {}, Placement.place(List.of(), 10, 64, Pass.TOP_UP));
        assertArrayEquals(new int[] {0, 0, 0, 0, 0}, Placement.place(MIXED, 0, 64, Pass.FILL_EMPTY));
    }
    @Test void aCellOverTheLimitIsNotShrunk() {
        assertArrayEquals(new int[] {0, 5}, Placement.place(List.of(Held.same(99), Held.same(11)), 5, 16, Pass.TOP_UP));
    }
    @Test void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Placement.place(MIXED, -1, 64, Pass.TOP_UP));
        assertThrows(IllegalArgumentException.class, () -> Placement.place(MIXED, 1, 0, Pass.TOP_UP));
        assertThrows(IllegalArgumentException.class, () -> new Held(true, false, 3));
        assertThrows(IllegalArgumentException.class, () -> new Held(true, true, 0));
        assertThrows(IllegalArgumentException.class, () -> Held.same(-1));
    }
}
