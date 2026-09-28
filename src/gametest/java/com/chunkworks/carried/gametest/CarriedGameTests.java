/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.gametest;

import com.chunkworks.carried.api.Carried;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The protocol's reads and changes on a real server, against the fake provider (D-0001).
 * <p>Partitions. Reads: stacks in the hotbar, main slots, offhand, armour, one store, two stores;
 * count by item and by predicate; has; find in everything and in the stores alone. Take: enough
 * in the inventory alone, split over inventory and a store, exactly all, a shortfall. Draw: one
 * kind decided by the first match, a stack of another kind between. Give: each of the four steps
 * reached; a store refusing; everything full (the rest stays, or drops). Pickup: overflow into a
 * store. Cost: counting allocates nothing. */
@GameTestHolder("carried")
@PrefixGameTestTemplate(false)
public final class CarriedGameTests {
    public CarriedGameTests() {}

    static Player player(GameTestHelper h) {
        var p = h.makeMockPlayer(GameType.SURVIVAL);
        p.getInventory().clearContent();
        FakeStores.carry(p);
        return p;
    }
    static ItemStack emeralds(int n) { return new ItemStack(Items.EMERALD, n); }
    static void fillMain(Player p) {
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
    }

    @GameTest(template = "empty") public void readsSeeTheInventoryThenTheStoresAndNeverArmour(GameTestHelper h) {
        var p = player(h);
        p.getInventory().setItem(0, emeralds(3));
        p.getInventory().setItem(20, emeralds(5));
        p.getInventory().setItem(Carried.OFFHAND, emeralds(2));
        p.getInventory().setItem(39, emeralds(7));
        var a = new FakeStore("test:a", 4).with(2, emeralds(10));
        var b = new FakeStore("test:b", 2).with(0, new ItemStack(Items.EMERALD_BLOCK));
        FakeStores.carry(p, a, b);
        h.assertValueEqual(Carried.count(p, Items.EMERALD), 20, "hotbar, main, offhand and a store; not the helmet slot");
        h.assertValueEqual(Carried.count(p, s -> s.is(Items.EMERALD) || s.is(Items.EMERALD_BLOCK)), 21, "a predicate over both kinds");
        h.assertTrue(Carried.has(p, Items.EMERALD_BLOCK), "the second store is seen");
        h.assertTrue(!Carried.has(p, Items.DIAMOND), "nothing carried, nothing had");
        var order = new ArrayList<String>();
        Carried.forEach(p, (store, cell, stack) -> order.add(store + "/" + cell));
        h.assertValueEqual(order, List.of("minecraft:inventory/0", "minecraft:inventory/20", "minecraft:inventory/40", "test:a/2", "test:b/0"), "reading order");
        var first = Carried.find(p, s -> s.is(Items.EMERALD), true);
        h.assertTrue(first != null && first.store().equals("test:a") && first.cell() == 2, "the first stored emerald: " + first);
        h.assertTrue(Carried.find(p, s -> s.is(Items.EMERALD), false).store().equals(Carried.INVENTORY), "the inventory's first when it counts");
        h.assertValueEqual(Carried.at(p, "test:b", 0).getCount(), 1, "a place read directly");
        h.assertTrue(Carried.at(p, "test:gone", 0).isEmpty() && Carried.at(p, "test:a", 99).isEmpty(), "a store not carried, a cell it lacks: empty");
        h.succeed();
    }

    @GameTest(template = "empty") public void takeIsAllOrNothingAndTheInventoryGoesFirst(GameTestHelper h) {
        var p = player(h);
        p.getInventory().setItem(4, emeralds(10));
        var a = new FakeStore("test:a", 3).with(0, emeralds(12)).with(1, new ItemStack(Items.STONE, 3)).with(2, emeralds(8));
        FakeStores.carry(p, a);
        int[] got = {0};
        h.assertTrue(Carried.take(p, s -> s.is(Items.EMERALD), 15, s -> got[0] += s.getCount()), "fifteen of thirty");
        h.assertValueEqual(got[0], 15, "the pieces handed over total the take");
        h.assertTrue(p.getInventory().getItem(4).isEmpty(), "the inventory's ten went first");
        h.assertValueEqual(a.cell(0).getCount(), 7, "then the store's first cell");
        h.assertValueEqual(a.cell(2).getCount(), 8, "the store's later cell untouched");
        h.assertValueEqual(a.cell(1).getCount(), 3, "another kind untouched");
        h.assertValueEqual(a.writes, 1, "the store written once");
        h.assertTrue(!Carried.take(p, s -> s.is(Items.EMERALD), 16, s -> got[0] += 1000), "sixteen of fifteen: refused");
        h.assertValueEqual(got[0], 15, "nothing handed over on a shortfall");
        h.assertValueEqual(a.cell(0).getCount() + a.cell(2).getCount(), 15, "nothing taken on a shortfall");
        h.assertValueEqual(a.writes, 1, "no write on a shortfall");
        h.assertTrue(Carried.take(p, s -> s.is(Items.EMERALD), 15, s -> {}), "exactly all");
        h.assertTrue(a.cell(0).isEmpty() && a.cell(2).isEmpty(), "the store emptied of emeralds");
        h.assertTrue(Carried.take(p, s -> s.is(Items.EMERALD), 0, s -> {}), "nothing asked, nothing refused");
        h.succeed();
    }

    @GameTest(template = "empty") public void drawTakesOneKindUpToTheMost(GameTestHelper h) {
        var p = player(h);
        var named = emeralds(5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
        var a = new FakeStore("test:a", 3).with(0, emeralds(5)).with(1, named).with(2, emeralds(4));
        FakeStores.carry(p, a);
        p.getInventory().setItem(0, emeralds(30));
        var got = Carried.draw(p, s -> s.is(Items.EMERALD), 8, true);
        h.assertTrue(got.is(Items.EMERALD) && got.getCount() == 8 && !got.has(DataComponents.CUSTOM_NAME), "eight plain from the store: " + got);
        h.assertTrue(a.cell(0).isEmpty() && a.cell(1).getCount() == 5 && a.cell(2).getCount() == 1, "the named stack is another kind");
        h.assertValueEqual(p.getInventory().getItem(0).getCount(), 30, "stores only: the inventory untouched");
        var piece = Carried.takeFrom(p, "test:a", 1, 2);
        h.assertTrue(piece.getCount() == 2 && piece.has(DataComponents.CUSTOM_NAME) && a.cell(1).getCount() == 3, "a take from one place keeps its components");
        h.assertValueEqual(Carried.takeFrom(p, Carried.INVENTORY, 0, 64).getCount(), 30, "an inventory slot, no more than it holds");
        h.assertTrue(Carried.draw(p, s -> s.is(Items.DIAMOND), 5, false).isEmpty(), "nothing of the kind: empty");
        h.succeed();
    }

    @GameTest(template = "empty") public void giveTakesTheFourStepsInOrder(GameTestHelper h) {
        var p = player(h);
        p.getInventory().setItem(9, emeralds(60));
        var a = new FakeStore("test:a", 4).with(3, emeralds(50));
        FakeStores.carry(p, a);
        var gift = emeralds(20);
        Carried.give(p, gift);
        h.assertTrue(gift.isEmpty(), "all twenty placed");
        h.assertValueEqual(p.getInventory().getItem(9).getCount(), 64, "one: the inventory's stack topped up");
        h.assertValueEqual(a.cell(3).getCount(), 64, "two: the store's stack topped up, before an empty slot");
        h.assertValueEqual(p.getInventory().getItem(0).getCount(), 2, "three: the rest in the first empty slot");
        h.assertTrue(a.cell(0).isEmpty(), "the store's empty cells wait for a full inventory");
        fillMain(p);
        var cobble = new ItemStack(Items.COBBLESTONE, 70);
        Carried.give(p, cobble);
        h.assertTrue(cobble.isEmpty() && a.cell(0).getCount() == 64 && a.cell(1).getCount() == 6, "four: the store's empty cells: " + a.cell(0) + " " + a.cell(1));
        h.succeed();
    }

    @GameTest(template = "empty") public void aRefusedOrUnplaceableStackStaysOrDrops(GameTestHelper h) {
        var p = player(h);
        fillMain(p);
        var a = new FakeStore("test:a", 2, s -> s.is(Items.SHULKER_BOX));
        FakeStores.carry(p, a);
        var box = new ItemStack(Items.SHULKER_BOX);
        Carried.give(p, box);
        h.assertTrue(box.getCount() == 1 && a.cell(0).isEmpty(), "the store refuses it; it stays in the stack");
        a.with(0, new ItemStack(Items.STONE, 64)).with(1, new ItemStack(Items.STONE, 64));
        var sticks = new ItemStack(Items.STICK, 5);
        Carried.giveOrDrop(p, sticks);
        var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(4));
        h.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(Items.STICK) && e.getItem().getCount() == 5), "nowhere to go: dropped at the feet");
        dropped.forEach(ItemEntity::discard);
        h.succeed();
    }

    @GameTest(template = "empty") public void aPickupOverflowsIntoAStore(GameTestHelper h) {
        var p = player(h);
        fillMain(p);
        var a = new FakeStore("test:a", 3).with(1, new ItemStack(Items.IRON_INGOT, 60));
        FakeStores.carry(p, a);
        var e = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.IRON_INGOT, 10));
        e.setNoPickUpDelay();
        h.getLevel().addFreshEntity(e);
        e.playerTouch(p);
        h.assertTrue(e.isRemoved(), "the whole pickup taken");
        h.assertValueEqual(a.cell(1).getCount(), 64, "onto the store's stack first");
        h.assertValueEqual(a.cell(0).getCount(), 6, "the rest in its first empty cell");
        h.succeed();
    }

    /** Counting runs per frame in HUDs and per tick in gunnery: it must not allocate. Measured with
     * the JVM's per-thread allocation counter after a warm-up, with a full inventory and a store
     * of 36 cells; the time per call is logged for the record. */
    @GameTest(template = "empty", timeoutTicks = 400) public void countingAllocatesNothing(GameTestHelper h) {
        var p = player(h);
        fillMain(p);
        var a = new FakeStore("test:a", 36);
        for (int i = 0; i < 36; i++) a.with(i, new ItemStack(i % 2 == 0 ? Items.ARROW : Items.STONE, 32));
        FakeStores.carry(p, a);
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long sink = 0;
        for (int i = 0; i < 200_000; i++) sink += Carried.count(p, Items.ARROW);
        int calls = 100_000;
        long before = threads.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
        for (int i = 0; i < calls; i++) sink += Carried.count(p, Items.ARROW);
        long elapsed = System.nanoTime() - t0, bytes = threads.getCurrentThreadAllocatedBytes() - before;
        h.assertValueEqual(Carried.count(p, Items.ARROW), 18 * 32, "the count itself");
        com.mojang.logging.LogUtils.getLogger().info("carried: count over 36 slots and 36 cells: {} ns/call, {} bytes allocated over {} calls (sink {})", elapsed / calls, bytes, calls, sink);
        h.assertTrue(bytes < calls, "no allocation per call: " + bytes + " bytes over " + calls + " calls");
        h.succeed();
    }
}
