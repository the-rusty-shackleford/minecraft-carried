/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.gametest;

import com.chunkworks.carried.api.CarriedStore;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** A store for the tests: a list of cells, a rule for what it refuses, and a count of writes.
 * <p>AF: cell i holds cells.get(i). RI: cells never change except through write. */
final class FakeStore implements CarriedStore {
    private final String address;
    private final NonNullList<ItemStack> cells;
    private final Predicate<ItemStack> refuses;
    int writes;

    FakeStore(String address, int size, Predicate<ItemStack> refuses) {
        this.address = address;
        this.cells = NonNullList.withSize(size, ItemStack.EMPTY);
        this.refuses = refuses;
    }
    FakeStore(String address, int size) { this(address, size, s -> false); }

    /** effects: sets a cell directly, as a test's arrangement. */
    FakeStore with(int cell, ItemStack stack) { cells.set(cell, stack); return this; }
    ItemStack cell(int cell) { return cells.get(cell); }

    @Override public String address() { return address; }
    @Override public int size() { return cells.size(); }
    @Override public ItemStack peek(int cell) { return cells.get(cell); }
    @Override public boolean admits(int cell, ItemStack stack) { return stack.isEmpty() || !refuses.test(stack); }
    @Override public void write(List<ItemStack> next) {
        if (next.size() != cells.size()) throw new IllegalArgumentException("wrong size");
        for (int i = 0; i < next.size(); i++) {
            var before = cells.get(i);
            var after = next.get(i);
            boolean withdrawal = after.isEmpty() || ItemStack.isSameItemSameComponents(before, after) && after.getCount() <= before.getCount();
            if (!withdrawal && !admits(i, after)) throw new IllegalArgumentException("cell " + i + " refuses " + after);
        }
        for (int i = 0; i < next.size(); i++) cells.set(i, next.get(i).copy());
        writes++;
    }
}
