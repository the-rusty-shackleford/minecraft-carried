/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.mixin;

import com.chunkworks.carried.hook.Lending;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Where a bow or crossbow takes its shot out of the stack it was handed: a lent copy is settled
 * with its store at once and the arrow loses the marker (D-0003). */
@Mixin(ProjectileWeaponItem.class)
abstract class ProjectileWeaponItemMixin {
    @Inject(method = "useAmmo", at = @At("RETURN"))
    private static void carried$settle(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean intangible, CallbackInfoReturnable<ItemStack> cir) {
        Lending.used(shooter, ammo, cir.getReturnValue());
    }
}
