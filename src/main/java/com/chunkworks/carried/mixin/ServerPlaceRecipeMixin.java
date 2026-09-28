/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.mixin;

import com.chunkworks.carried.api.Carried;
import net.minecraft.core.component.DataComponents;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The recipe book's fill takes from the stores when the inventory has none (D-0004): vanilla
 * moves one ingredient stack at a time into the grid, from an inventory slot it finds, and gives
 * up when it finds none; here the stores are asked before it gives up, for the same kind of
 * stack vanilla would accept (the same item and components, undamaged, unenchanted, unnamed).
 * The inventory is still first. Paired with {@link InventoryMixin}: counting without taking would
 * show a recipe craftable and then place half of it. */
@Mixin(ServerPlaceRecipe.class)
abstract class ServerPlaceRecipeMixin {
    @Shadow protected Inventory inventory;

    @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
    private void carried$fromStores(Slot slot, ItemStack stack, int maxAmount, CallbackInfoReturnable<Integer> cir) {
        if (inventory == null || inventory.findSlotMatchingUnusedItem(stack) != -1) return;
        var got = Carried.draw(inventory.player, s -> ItemStack.isSameItemSameComponents(stack, s)
                && !s.isDamaged() && !s.isEnchanted() && !s.has(DataComponents.CUSTOM_NAME), maxAmount, true);
        if (got.isEmpty()) return;
        if (slot.getItem().isEmpty()) slot.set(got);
        else slot.getItem().grow(got.getCount());
        cir.setReturnValue(maxAmount - got.getCount());
    }
}
