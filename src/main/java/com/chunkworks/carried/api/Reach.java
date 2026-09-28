/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a withdrawal can reach, fixed at one moment on the server: the places in reading
 * order, each with its stack as it was then.
 * <p>AF: place {@code i} is inventory slot {@code cell[i]} when {@code store[i] < 0}, else cell
 * {@code cell[i]} of {@code stores.get(store[i])}; {@code stack[i]} is its stack.
 * <p>RI: the arrays have one entry per place; places of one store are contiguous and in cell
 * order; inventory places come first. Good only until something else changes the player's items,
 * which is why a plan is applied to the reach it was made from, in the same call.
 */
final class Reach {
    private final Player player;
    private final List<CarriedStore> stores;
    private final int[] store;
    private final int[] cell;
    private final ItemStack[] stack;

    private Reach(Player player, List<CarriedStore> stores, int[] store, int[] cell, ItemStack[] stack) {
        this.player = player;
        this.stores = stores;
        this.store = store;
        this.cell = cell;
        this.stack = stack;
    }

    /** effects: the inventory's main slots and offhand (unless {@code storesOnly}), then every
     * store's cells. */
    static Reach of(Player player, boolean storesOnly) {
        var stores = new ArrayList<CarriedStore>(4);
        Carried.listStores(player, stores);
        int places = storesOnly ? 0 : Inventory.INVENTORY_SIZE + 1;
        for (var s : stores) places += s.size();
        int[] store = new int[places], cell = new int[places];
        var stack = new ItemStack[places];
        int i = 0;
        if (!storesOnly) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++, i++) {
                store[i] = -1; cell[i] = slot; stack[i] = inventory.items.get(slot);
            }
            store[i] = -1; cell[i] = Carried.OFFHAND; stack[i] = inventory.offhand.get(0); i++;
        }
        for (int k = 0; k < stores.size(); k++) {
            var s = stores.get(k);
            for (int c = 0, n = s.size(); c < n; c++, i++) {
                store[i] = k; cell[i] = c; stack[i] = s.peek(c);
            }
        }
        return new Reach(player, stores, store, cell, stack);
    }

    /** effects: how many each place holds of what {@code kind} accepts (0 when empty or not). */
    int[] held(Predicate<ItemStack> kind) {
        int[] held = new int[stack.length];
        for (int i = 0; i < stack.length; i++) held[i] = !stack[i].isEmpty() && kind.test(stack[i]) ? stack[i].getCount() : 0;
        return held;
    }

    /**
     * requires: take[i] ≤ what place i holds, the player's items unchanged since this reach.
     * effects: removes take[i] from each place, handing each removed piece to {@code sink}; each
     * store is written once, stores before the inventory so a refusing store leaves the
     * inventory as it was.
     */
    void apply(int[] take, Consumer<ItemStack> sink) {
        int i = 0;
        while (i < take.length && store[i] < 0) i++;
        int firstStorePlace = i;
        // Stores, one write each.
        while (i < take.length) {
            int k = store[i];
            int start = i;
            while (i < take.length && store[i] == k) i++;
            boolean any = false;
            for (int j = start; j < i; j++) any |= take[j] > 0;
            if (!any) continue;
            var s = stores.get(k);
            var cells = Carried.copyOf(s);
            var pieces = new ArrayList<ItemStack>();
            for (int j = start; j < i; j++) if (take[j] > 0) pieces.add(cells.get(cell[j]).split(take[j]));
            s.write(cells);
            pieces.forEach(sink);
        }
        // The inventory.
        boolean changed = false;
        for (int j = 0; j < firstStorePlace; j++) {
            if (take[j] == 0) continue;
            sink.accept(stack[j].split(take[j]));
            changed = true;
        }
        if (changed) player.getInventory().setChanged();
    }
}
