/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.mixin;

import com.chunkworks.carried.api.Carried;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trading pays out of the stores too (D-0005). Choosing a trade fills its payment slots from the
 * menu's inventory slots only; on the server the stores then top each slot up with the same kind.
 * Closing the screen puts what was not spent back where a give puts it, the stores included, so a
 * payment drawn from a bag goes back into it rather than onto the ground when the inventory is
 * full. */
@Mixin(MerchantMenu.class)
abstract class MerchantMenuMixin {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;

    @Inject(method = "moveFromInventoryToPaymentSlot", at = @At("TAIL"))
    private void carried$topUpFromStores(int paymentSlot, ItemCost cost, CallbackInfo ci) {
        if (trader.isClientSide()) return;
        var player = trader.getTradingPlayer();
        if (player == null || player.level().isClientSide()) return;
        ItemStack there = tradeContainer.getItem(paymentSlot);
        int room = there.isEmpty() ? cost.itemStack().getMaxStackSize() : there.getMaxStackSize() - there.getCount();
        if (room <= 0) return;
        var got = Carried.draw(player, s -> cost.test(s) && (there.isEmpty() || ItemStack.isSameItemSameComponents(there, s)), room, true);
        if (got.isEmpty()) return;
        if (there.isEmpty()) tradeContainer.setItem(paymentSlot, got);
        else {
            there.grow(got.getCount());
            tradeContainer.setItem(paymentSlot, there);
        }
    }

    @WrapOperation(method = "removed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V"))
    private void carried$backWhereAGiveGoes(Inventory inventory, ItemStack stack, Operation<Void> original) {
        Carried.give(inventory.player, stack);
        if (!stack.isEmpty()) original.call(inventory, stack);
    }
}
