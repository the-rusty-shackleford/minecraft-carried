/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.gametest;

import com.chunkworks.carried.api.Carried;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The vanilla hooks on a real server, against the fake provider.
 * <p>Partitions. Lending (D-0003): a bow shot paid at once; a weapon that shrinks the copy itself
 * paid at the end of the tick; a lookup never used paid nothing; Infinity paid nothing; the
 * inventory's arrows first. Recipe book (D-0004): counted and placed from a store; the inventory
 * first; shift-fill takes all. Trading (D-0005): the payment topped up from a store; what is not
 * spent goes back to the inventory with room, to the store when the inventory is full. */
@GameTestHolder("carried")
@PrefixGameTestTemplate(false)
public final class HookGameTests {
    public HookGameTests() {}

    private static Player archer(GameTestHelper h, ItemStack weapon) {
        var p = CarriedGameTests.player(h);
        p.getInventory().setItem(0, weapon);
        p.moveTo(h.absoluteVec(new Vec3(2.5, 2, 2.5)));
        return p;
    }
    /** effects: the arrows this test's player shot. Tests in a batch run side by side, closer
     * than a shot flies, so a box alone would count, or clear, a neighbour's. */
    private static java.util.List<AbstractArrow> arrowsOf(GameTestHelper h, Player p) {
        return h.getLevel().getEntitiesOfClass(AbstractArrow.class, new AABB(h.absoluteVec(new Vec3(-16, -16, -16)), h.absoluteVec(new Vec3(32, 48, 32))), a -> a.getOwner() == p);
    }
    private static void clearArrows(GameTestHelper h, Player p) { arrowsOf(h, p).forEach(AbstractArrow::discard); }

    @GameTest(template = "empty", timeoutTicks = 100) public void aBowShotIsPaidByTheStoreAtOnce(GameTestHelper h) {
        var bow = new ItemStack(Items.BOW);
        var p = archer(h, bow);
        var quiver = new FakeStore("test:quiver", 2).with(1, new ItemStack(Items.ARROW, 16));
        FakeStores.carry(p, quiver);
        var lent = p.getProjectile(bow);
        h.assertTrue(lent.is(Items.ARROW) && lent.getCount() == 16 && Carried.lent(lent), "the bow sees the store's sixteen, marked: " + lent);
        h.assertTrue(!Carried.lent(quiver.cell(1)), "the store's own stack is unmarked");
        bow.getItem().releaseUsing(bow, h.getLevel(), p, 72000 - 20);
        h.assertValueEqual(quiver.cell(1).getCount(), 15, "paid the moment the shot was taken");
        h.runAfterDelay(2, () -> {
            h.assertValueEqual(quiver.cell(1).getCount(), 15, "and not again at the end of the tick");
            var arrows = arrowsOf(h, p);
            h.assertTrue(arrows.size() == 1 && !Carried.lent(arrows.get(0).getPickupItemStackOrigin()), "one unmarked arrow flew");
            clearArrows(h, p);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100) public void aWeaponThatShrinksTheCopyItselfIsPaidAtTheEndOfTheTick(GameTestHelper h) {
        var gun = new ItemStack(TestMod.SELF_FEEDING_GUN.get());
        var p = archer(h, gun);
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var quiver = new FakeStore("test:quiver", 1).with(0, new ItemStack(Items.ARROW, 8));
        FakeStores.carry(p, quiver);
        gun.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        gun.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getProjectile(gun).getCount() == 8, "a lookup the weapon never uses lends a full copy");
        h.runAfterDelay(1, () -> {
            h.assertValueEqual(quiver.cell(0).getCount(), 6, "two shots, two arrows: the potato cannon's path is paid for");
            h.runAfterDelay(1, () -> {
                h.assertValueEqual(quiver.cell(0).getCount(), 6, "settled once, never again");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100) public void infinityPaysNothingAndTheInventoryComesFirst(GameTestHelper h) {
        var bow = new ItemStack(Items.BOW);
        var p = archer(h, bow);
        var quiver = new FakeStore("test:quiver", 1).with(0, new ItemStack(Items.ARROW, 8));
        FakeStores.carry(p, quiver);
        p.getInventory().setItem(5, new ItemStack(Items.SPECTRAL_ARROW, 3));
        var found = p.getProjectile(bow);
        h.assertTrue(found.is(Items.SPECTRAL_ARROW) && !Carried.lent(found), "the inventory's arrows first, unmarked");
        p.getInventory().setItem(5, ItemStack.EMPTY);
        bow.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.INFINITY), 1);
        bow.getItem().releaseUsing(bow, h.getLevel(), p, 72000 - 20);
        h.runAfterDelay(2, () -> {
            h.assertValueEqual(quiver.cell(0).getCount(), 8, "Infinity took nothing");
            clearArrows(h, p);
            h.succeed();
        });
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<CraftingRecipe> planks(GameTestHelper h) {
        return (RecipeHolder<CraftingRecipe>) h.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
    }
    private static int logsInGrid(Player p) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (p.inventoryMenu.getSlot(i).getItem().is(Items.OAK_LOG)) n += p.inventoryMenu.getSlot(i).getItem().getCount();
        return n;
    }

    @GameTest(template = "empty") public void theRecipeBookCountsAndPlacesFromAStore(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        var store = new FakeStore("test:bag", 2).with(1, new ItemStack(Items.OAK_LOG, 3));
        FakeStores.carry(p, store);
        var recipe = planks(h);
        p.awardRecipes(List.of(recipe));
        var contents = new StackedContents();
        p.getInventory().fillStackedContents(contents);
        h.assertTrue(contents.canCraft(recipe.value(), null), "the book counts the store's logs");
        p.inventoryMenu.handlePlacement(false, recipe, p);
        h.assertValueEqual(logsInGrid(p), 1, "one log placed in the grid");
        h.assertValueEqual(store.cell(1).getCount(), 2, "taken from the store");
        p.inventoryMenu.handlePlacement(true, recipe, p);
        h.assertValueEqual(logsInGrid(p), 3, "shift-fill: every log");
        h.assertTrue(store.cell(1).isEmpty(), "the store gave them all");
        h.succeed();
    }

    @GameTest(template = "empty") public void theRecipeBookTakesTheInventoryFirst(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        p.getInventory().setItem(12, new ItemStack(Items.OAK_LOG, 1));
        var store = new FakeStore("test:bag", 1).with(0, new ItemStack(Items.OAK_LOG, 3));
        FakeStores.carry(p, store);
        var recipe = planks(h);
        p.awardRecipes(List.of(recipe));
        p.inventoryMenu.handlePlacement(false, recipe, p);
        h.assertValueEqual(logsInGrid(p), 1, "one log placed");
        h.assertTrue(p.getInventory().getItem(12).isEmpty(), "the inventory's log");
        h.assertValueEqual(store.cell(0).getCount(), 3, "the store untouched");
        h.succeed();
    }

    private static MerchantMenu trade(GameTestHelper h, Player p) {
        var villager = h.spawn(EntityType.VILLAGER, 2, 2, 2);
        villager.setNoAi(true);
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD), 10, 1, 0.05f));
        villager.setOffers(offers);
        villager.setTradingPlayer(p);
        return new MerchantMenu(1, p.getInventory(), villager);
    }

    /** A server player: vanilla hands an unspent payment back only to one (a plain player's is
     * left in the menu). */
    private static Player trader(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        FakeStores.carry(p);
        return p;
    }

    @GameTest(template = "empty") public void aTradeIsPaidFromAStoreAndTheRestGoesBack(GameTestHelper h) {
        var p = trader(h);
        var bag = new FakeStore("test:bag", 2).with(0, CarriedGameTests.emeralds(20));
        FakeStores.carry(p, bag);
        var menu = trade(h, p);
        menu.tryMoveItems(0);
        h.assertValueEqual(menu.getSlot(0).getItem().getCount(), 20, "the payment slot filled from the bag");
        h.assertTrue(bag.cell(0).isEmpty(), "the bag paid");
        menu.removed(p);
        h.assertValueEqual(Carried.count(p, Items.EMERALD), 20, "nothing lost on closing");
        h.assertValueEqual(p.getInventory().getItem(0).getCount(), 20, "with room, back into an empty slot, as a give goes");
        h.succeed();
    }

    @GameTest(template = "empty") public void withAFullInventoryTheUnspentPaymentGoesBackIntoTheStore(GameTestHelper h) {
        var p = trader(h);
        CarriedGameTests.fillMain(p);
        var bag = new FakeStore("test:bag", 2).with(0, CarriedGameTests.emeralds(20));
        FakeStores.carry(p, bag);
        var menu = trade(h, p);
        menu.tryMoveItems(0);
        h.assertValueEqual(menu.getSlot(0).getItem().getCount(), 20, "the payment slot filled from the bag");
        menu.removed(p);
        h.assertValueEqual(bag.cell(0).getCount(), 20, "back in the bag, not on the ground");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(p.blockPosition()).inflate(4)).isEmpty(), "nothing dropped");
        h.succeed();
    }
}
