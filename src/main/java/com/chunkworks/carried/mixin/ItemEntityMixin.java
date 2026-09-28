/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.mixin;

import com.chunkworks.carried.api.Carried;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An item picked up off the ground goes into the player's stores too (D-0002, carrying
 * Backpacks+ D-0029). A wrap, not a redirect, so another mod's wrap of the same call still runs:
 * it is the inventory's own step. */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean carried$pickUp(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        return Carried.pickUp(inventory.player, stack, s -> original.call(inventory, s));
    }
}
