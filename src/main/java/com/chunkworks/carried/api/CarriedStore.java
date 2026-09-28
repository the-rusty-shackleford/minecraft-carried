/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.api;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * One place a player carries items besides the inventory: a worn backpack's storage cells, a bag
 * in a pocket, a quiver. Never gear a player chose for a holster (a backpack's mounts are not a
 * store).
 *
 * <p>AF: {@code size()} cells, cell {@code i} holding {@code peek(i)}.
 *
 * <p>Spec for implementors (D-0001):
 * <ul>
 * <li>{@link #peek} never returns null and never returns a stack anyone mutates: Carried treats
 * it as read-only, and a store may hand out a snapshot it keeps between changes.</li>
 * <li>A store is good for the one operation that listed it; Carried keeps none past it.</li>
 * <li>{@link #write} is called only on the logical server, with {@code size()} stacks that differ
 * from the current cells only where Carried took from a cell (a withdrawal, which a store must
 * always accept) or placed a stack {@link #admits} accepted; it replaces the contents as one
 * change.</li>
 * </ul>
 */
public interface CarriedStore {
    /** effects: a name for this store unique among the player's stores at this moment and stable
     * while it stays where it is, namespaced by its provider ({@code backpacksplus:worn}). */
    String address();

    /** effects: the number of cells, ≥ 0. */
    int size();

    /** requires: 0 ≤ cell < size(). effects: the cell's stack, EMPTY when empty; read-only. */
    ItemStack peek(int cell);

    /** requires: 0 ≤ cell < size(). effects: whether the store would hold {@code stack} in the
     * cell by its own rules (a backpack refuses another backpack). */
    boolean admits(int cell, ItemStack stack);

    /** requires: on the logical server; {@code cells} as the interface spec says.
     * effects: replaces the store's contents with {@code cells}.
     * throws: IllegalArgumentException when a cell refuses its stack; nothing changes then. */
    void write(List<ItemStack> cells);
}
