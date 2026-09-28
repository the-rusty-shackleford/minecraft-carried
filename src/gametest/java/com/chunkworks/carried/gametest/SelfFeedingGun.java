/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.gametest;

import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/** A weapon that finds its ammunition as Create's potato cannon does: it asks the player for a
 * projectile and shrinks the stack it is handed itself, never calling useAmmo. Fires nothing. */
final class SelfFeedingGun extends ProjectileWeaponItem {
    SelfFeedingGun(Properties properties) { super(properties); }
    @Override public Predicate<ItemStack> getAllSupportedProjectiles() { return s -> s.is(Items.ARROW); }
    @Override public int getDefaultProjectileRange() { return 15; }
    @Override protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {}
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var gun = player.getItemInHand(hand);
        var ammo = player.getProjectile(gun);
        if (ammo.isEmpty()) return InteractionResultHolder.fail(gun);
        if (!level.isClientSide() && !player.isCreative()) {
            ammo.shrink(1);
            if (ammo.isEmpty()) player.getInventory().removeItem(ammo);
        }
        return InteractionResultHolder.consume(gun);
    }
}
