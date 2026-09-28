/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.api;

import com.chunkworks.carried.domain.Placement;
import com.chunkworks.carried.domain.Placement.Held;
import com.chunkworks.carried.domain.Placement.Pass;
import com.chunkworks.carried.domain.Withdrawal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a player carries, as one thing (D-0001): the inventory's main slots (the hotbar first, as
 * the inventory numbers them, 0–35), then the offhand (40), then every store the registered
 * providers list, in their order, cells in order. Armour is never carried stock, and no store is
 * gear a player holstered (a backpack's mounts). Whoever counts, takes or gives a player's items
 * asks here, and a bag is then counted everywhere at once.
 *
 * <p>Reads work on both sides (a store's contents reach the owning client with its item) and
 * allocate nothing. Changes happen on the logical server only: they are planned over everything
 * in reach first and then applied, and each store is written at most once per step. Nothing here
 * keeps a stack or a store past the call.
 */
public final class Carried {
    private Carried() {}

    /** The store name of the player's own inventory in {@link Visitor} calls and places. */
    public static final String INVENTORY = "minecraft:inventory";
    /** The offhand's slot number, as the inventory numbers it. */
    public static final int OFFHAND = Inventory.SLOT_OFFHAND;
    private static final int MAIN = Inventory.INVENTORY_SIZE;

    /** The key under a stack's custom data that marks a projectile lent out of a store (D-0003). */
    public static final String LENT = "carried_lent";

    /** effects: whether the stack is a projectile lent out of a store and not yet used. */
    public static boolean lent(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.contains(LENT);
    }

    /** Receives a carried stack in reading order; the stack is read-only. */
    @FunctionalInterface public interface Visitor { void visit(String store, int cell, ItemStack stack); }

    /** A carried stack and where it is: {@code store} is {@link #INVENTORY} or a store's address.
     * <p>AF: cell {@code cell} of {@code store}, holding {@code stack} when found (read-only). */
    public record Place(String store, int cell, ItemStack stack) {}

    // ---- reads, either side ----

    /** effects: visits every non-empty carried stack in reading order. */
    public static void forEach(Player player, Visitor visitor) {
        var inventory = player.getInventory();
        for (int i = 0; i < MAIN; i++) {
            var s = inventory.items.get(i);
            if (!s.isEmpty()) visitor.visit(INVENTORY, i, s);
        }
        var off = inventory.offhand.get(0);
        if (!off.isEmpty()) visitor.visit(INVENTORY, OFFHAND, off);
        forEachStored(player, visitor);
    }

    /** effects: visits every non-empty stack in the player's stores only, in reading order. */
    public static void forEachStored(Player player, Visitor visitor) {
        var scratch = Scratch.open(player);
        try {
            for (int k = 0; k < scratch.size(); k++) {
                var store = scratch.get(k);
                String address = store.address();
                for (int c = 0, n = store.size(); c < n; c++) {
                    var s = store.peek(c);
                    if (!s.isEmpty()) visitor.visit(address, c, s);
                }
            }
        } finally {
            Scratch.close(scratch);
        }
    }

    /** effects: how many of the stacks {@code kind} accepts the player carries. */
    public static int count(Player player, Predicate<ItemStack> kind) { return tally(player, Objects.requireNonNull(kind), null, Integer.MAX_VALUE); }

    /** effects: how many of {@code item} the player carries, whatever their components. */
    public static int count(Player player, Item item) { return tally(player, null, Objects.requireNonNull(item), Integer.MAX_VALUE); }

    /** effects: whether the player carries any stack {@code kind} accepts. */
    public static boolean has(Player player, Predicate<ItemStack> kind) { return tally(player, Objects.requireNonNull(kind), null, 1) > 0; }

    /** effects: whether the player carries any {@code item}. */
    public static boolean has(Player player, Item item) { return tally(player, null, Objects.requireNonNull(item), 1) > 0; }

    /** effects: the first carried stack {@code kind} accepts, in reading order (the stores alone
     * when {@code storesOnly}), or null. */
    public static Place find(Player player, Predicate<ItemStack> kind, boolean storesOnly) {
        if (!storesOnly) {
            var inventory = player.getInventory();
            for (int i = 0; i < MAIN; i++) {
                var s = inventory.items.get(i);
                if (!s.isEmpty() && kind.test(s)) return new Place(INVENTORY, i, s);
            }
            var off = inventory.offhand.get(0);
            if (!off.isEmpty() && kind.test(off)) return new Place(INVENTORY, OFFHAND, off);
        }
        var scratch = Scratch.open(player);
        try {
            for (int k = 0; k < scratch.size(); k++) {
                var store = scratch.get(k);
                for (int c = 0, n = store.size(); c < n; c++) {
                    var s = store.peek(c);
                    if (!s.isEmpty() && kind.test(s)) return new Place(store.address(), c, s);
                }
            }
            return null;
        } finally {
            Scratch.close(scratch);
        }
    }

    /** effects: the stack at a place (read-only), EMPTY when the store is not carried now or has
     * no such cell. For the inventory, any slot the inventory numbers (0–40). */
    public static ItemStack at(Player player, String store, int cell) {
        if (INVENTORY.equals(store)) {
            var inventory = player.getInventory();
            return cell >= 0 && cell < inventory.getContainerSize() ? inventory.getItem(cell) : ItemStack.EMPTY;
        }
        var scratch = Scratch.open(player);
        try {
            for (int k = 0; k < scratch.size(); k++) {
                var s = scratch.get(k);
                if (s.address().equals(store)) return cell >= 0 && cell < s.size() ? s.peek(cell) : ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        } finally {
            Scratch.close(scratch);
        }
    }

    private static int tally(Player player, Predicate<ItemStack> kind, Item item, int enough) {
        int n = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < MAIN && n < enough; i++) {
            var s = inventory.items.get(i);
            if (!s.isEmpty() && (kind != null ? kind.test(s) : s.is(item))) n += s.getCount();
        }
        var off = inventory.offhand.get(0);
        if (n < enough && !off.isEmpty() && (kind != null ? kind.test(off) : off.is(item))) n += off.getCount();
        if (n >= enough) return n;
        var scratch = Scratch.open(player);
        try {
            for (int k = 0; k < scratch.size() && n < enough; k++) {
                var store = scratch.get(k);
                for (int c = 0, size = store.size(); c < size; c++) {
                    var s = store.peek(c);
                    if (!s.isEmpty() && (kind != null ? kind.test(s) : s.is(item))) n += s.getCount();
                }
            }
            return n;
        } finally {
            Scratch.close(scratch);
        }
    }

    // ---- changes, logical server only ----

    /**
     * requires: the logical server; n ≥ 0.
     * effects: when the player carries at least {@code n} of what {@code kind} accepts, removes
     * exactly {@code n}, the inventory's before the stores' and first places first, hands each
     * removed piece (with its components) to {@code taken}, and returns true; otherwise changes
     * nothing and returns false. For payments: never a partial charge.
     * throws: IllegalStateException on a client; IllegalArgumentException when n < 0.
     */
    public static boolean take(Player player, Predicate<ItemStack> kind, int n, Consumer<ItemStack> taken) {
        requireServer(player);
        if (n < 0) throw new IllegalArgumentException("n ≥ 0");
        var reach = Reach.of(player, false);
        var plan = Withdrawal.plan(reach.held(kind), n);
        if (plan.isEmpty()) return false;
        reach.apply(plan.get(), taken);
        return true;
    }

    /**
     * requires: the logical server; max ≥ 0.
     * effects: takes up to {@code max} of one kind: the first carried stack {@code kind} accepts
     * (in the stores alone when {@code storesOnly}) decides it, and only stacks with the same item
     * and components follow. Returns what was taken as one stack, EMPTY when nothing was.
     * throws: IllegalStateException on a client; IllegalArgumentException when max < 0.
     */
    public static ItemStack draw(Player player, Predicate<ItemStack> kind, int max, boolean storesOnly) {
        requireServer(player);
        if (max < 0) throw new IllegalArgumentException("max ≥ 0");
        var first = find(player, kind, storesOnly);
        if (first == null || max == 0) return ItemStack.EMPTY;
        var like = first.stack().copyWithCount(1);
        var reach = Reach.of(player, storesOnly);
        int[] plan = Withdrawal.upTo(reach.held(s -> kind.test(s) && ItemStack.isSameItemSameComponents(s, like)), max);
        int[] total = {0};
        reach.apply(plan, piece -> total[0] += piece.getCount());
        return like.copyWithCount(total[0]);
    }

    /**
     * requires: the logical server; n ≥ 0.
     * effects: removes up to {@code n} from one place and returns it, EMPTY when the place is
     * empty or not carried now. For the inventory, any slot it numbers.
     * throws: IllegalStateException on a client; IllegalArgumentException when n < 0.
     */
    public static ItemStack takeFrom(Player player, String store, int cell, int n) {
        requireServer(player);
        if (n < 0) throw new IllegalArgumentException("n ≥ 0");
        if (n == 0) return ItemStack.EMPTY;
        if (INVENTORY.equals(store)) {
            var inventory = player.getInventory();
            if (cell < 0 || cell >= inventory.getContainerSize()) return ItemStack.EMPTY;
            var piece = inventory.removeItem(cell, n);
            if (!piece.isEmpty()) inventory.setChanged();
            return piece;
        }
        var stores = new ArrayList<CarriedStore>(4);
        listStores(player, stores);
        for (var s : stores) {
            if (!s.address().equals(store)) continue;
            if (cell < 0 || cell >= s.size() || s.peek(cell).isEmpty()) return ItemStack.EMPTY;
            var cells = copyOf(s);
            var piece = cells.get(cell).split(n);
            s.write(cells);
            return piece;
        }
        return ItemStack.EMPTY;
    }

    /**
     * requires: the logical server.
     * effects: puts as much of {@code stack} as fits where the inventory would put it with the
     * stores in it (Backpacks+ D-0029): on the inventory's stacks of the same thing (the selected
     * slot, the offhand, then the rest), then on the stores' stacks of the same thing, then into
     * the inventory's empty main slots, then into the stores' empty cells that admit it. Shrinks
     * {@code stack} by what was placed; what did not fit stays in it.
     * throws: IllegalStateException on a client.
     */
    public static void give(Player player, ItemStack stack) {
        requireServer(player);
        place(player, stack, null);
    }

    /** requires: the logical server. effects: {@link #give}, then drops what did not fit at the
     * player's feet, as the inventory's placeItemBackInInventory does.
     * throws: IllegalStateException on a client. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        give(player, stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    /**
     * requires: {@code inventoryAdd} is the inventory's own add for this stack as the caller
     * would have run it (a pickup's, possibly wrapped by other mods).
     * effects: as {@link #give}, with {@code inventoryAdd} as the third step; returns whether any
     * of the stack was taken. On a client, or when the player carries no store, exactly
     * {@code inventoryAdd}.
     */
    public static boolean pickUp(Player player, ItemStack stack, Predicate<ItemStack> inventoryAdd) {
        if (player.level().isClientSide() || stack.isEmpty()) return inventoryAdd.test(stack);
        var scratch = Scratch.open(player);
        boolean none;
        try { none = scratch.isEmpty(); } finally { Scratch.close(scratch); }
        if (none) return inventoryAdd.test(stack);
        return place(player, stack, inventoryAdd);
    }

    private static boolean place(Player player, ItemStack stack, Predicate<ItemStack> inventoryAdd) {
        if (stack.isEmpty()) return false;
        int before = stack.getCount();
        var inventory = player.getInventory();
        topUp(inventory, stack);
        if (!stack.isEmpty()) intoStores(player, stack, Pass.TOP_UP);
        boolean took = false;
        if (!stack.isEmpty()) took = inventoryAdd != null ? inventoryAdd.test(stack) : fill(inventory, stack);
        if (!stack.isEmpty()) intoStores(player, stack, Pass.FILL_EMPTY);
        return took || stack.getCount() < before;
    }

    /** effects: tops up the inventory's stacks of the same thing in its own order (selected slot,
     * offhand, then the rest), as its add does before touching an empty slot. */
    private static void topUp(Inventory inventory, ItemStack stack) {
        int slot;
        boolean changed = false;
        while (!stack.isEmpty() && (slot = inventory.getSlotWithRemainingSpace(stack)) != -1) {
            ItemStack held = inventory.getItem(slot);
            int n = Math.min(stack.getCount(), inventory.getMaxStackSize(held) - held.getCount());
            if (n <= 0) break;
            held.grow(n);
            held.setPopTime(5);
            stack.shrink(n);
            changed = true;
        }
        if (changed) inventory.setChanged();
    }

    /** effects: fills the inventory's empty main slots, first free first; whether any moved. Never
     * deletes (the inventory's add discards overflow in creative; a give must not). */
    private static boolean fill(Inventory inventory, ItemStack stack) {
        int slot;
        boolean moved = false;
        while (!stack.isEmpty() && (slot = inventory.getFreeSlot()) != -1) {
            int n = Math.min(stack.getCount(), inventory.getMaxStackSize(stack));
            var put = stack.split(n);
            put.setPopTime(5);
            inventory.setItem(slot, put);
            moved = true;
        }
        if (moved) inventory.setChanged();
        return moved;
    }

    /** effects: one pass of the placement over every store, store by store, each written once. */
    private static void intoStores(Player player, ItemStack stack, Pass pass) {
        var stores = new ArrayList<CarriedStore>(4);
        listStores(player, stores);
        for (var store : stores) {
            if (stack.isEmpty()) return;
            int size = store.size();
            var held = new ArrayList<Held>(size);
            for (int c = 0; c < size; c++) {
                var in = store.peek(c);
                boolean admits = store.admits(c, stack);
                held.add(in.isEmpty() ? (admits ? Held.EMPTY : Held.REFUSED)
                        : admits && ItemStack.isSameItemSameComponents(in, stack) ? Held.same(in.getCount()) : Held.other(in.getCount()));
            }
            int[] into = Placement.place(held, stack.getCount(), stack.getMaxStackSize(), pass);
            List<ItemStack> cells = null;
            for (int c = 0; c < size; c++) {
                if (into[c] == 0) continue;
                if (cells == null) cells = copyOf(store);
                cells.set(c, stack.copyWithCount(cells.get(c).getCount() + into[c]));
            }
            if (cells == null) continue;
            store.write(cells);
            int placed = 0;
            for (int n : into) placed += n;
            stack.shrink(placed);
        }
    }

    // ---- plumbing ----

    static void requireServer(Player player) {
        if (player.level().isClientSide()) throw new IllegalStateException("Carried changes a player's items on the logical server only");
    }

    /** effects: appends every store the player carries, providers in order. */
    static void listStores(Player player, List<CarriedStore> out) {
        for (var p : CarriedProviders.array()) p.stores(player, out);
    }

    /** effects: independent copies of a store's cells, for a write. */
    static List<ItemStack> copyOf(CarriedStore store) {
        int size = store.size();
        var cells = new ArrayList<ItemStack>(size);
        for (int c = 0; c < size; c++) cells.add(store.peek(c).copy());
        return cells;
    }

    /** Per-thread reusable store lists, so reads list stores without allocating. A stack of lists
     * rather than one, because a provider or visitor may itself ask Carried something. */
    private static final class Scratch {
        private static final ThreadLocal<Scratch> LOCAL = ThreadLocal.withInitial(Scratch::new);
        private final ArrayList<ArrayList<CarriedStore>> lists = new ArrayList<>();
        private int depth;

        static ArrayList<CarriedStore> open(Player player) {
            var s = LOCAL.get();
            if (s.depth == s.lists.size()) s.lists.add(new ArrayList<>(4));
            var list = s.lists.get(s.depth++);
            list.clear();
            try {
                listStores(player, list);
            } catch (RuntimeException | Error e) {
                close(list);
                throw e;
            }
            return list;
        }
        static void close(ArrayList<CarriedStore> list) {
            list.clear();
            LOCAL.get().depth--;
        }
    }
}
