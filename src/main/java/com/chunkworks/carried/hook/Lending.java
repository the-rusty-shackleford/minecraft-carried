/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.hook;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.carried.domain.Loan;
import java.util.IdentityHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Projectiles in a store count for the weapon in hand (D-0003, carrying Backpacks+ D-0026). After
 * vanilla has looked in the hands and the inventory and found nothing, the first stored stack the
 * weapon accepts is lent as a marked copy: a store's cells are read-only, so the weapon cannot be
 * handed the store's own stack.
 *
 * <p>The store pays for exactly what the copy lost ({@link Loan#debit}). A loan is settled when
 * the weapon takes its shot through vanilla's {@code useAmmo} (so a crossbow's cell is one short
 * the moment it is loaded) and again at the end of the server tick, which catches a weapon that
 * shrinks the stack it was handed itself, as Create's potato cannon does; a lookup the weapon
 * never used settles to nothing. The marker is dropped from the copy and from what the weapon
 * took, so the arrow that flies stacks with plain arrows.
 *
 * <p>RI: {@code OPEN} holds only copies lent on the logical server this tick, keyed by identity;
 * it is touched on the server thread only and emptied at the end of every tick.
 */
public final class Lending {
    private Lending() {}

    private static final class Open {
        final Player player;
        final String store;
        final int cell;
        final ItemStack kind;
        int settled;
        Open(Player player, String store, int cell, ItemStack kind, int settled) {
            this.player = player; this.store = store; this.cell = cell; this.kind = kind; this.settled = settled;
        }
    }
    private static final IdentityHashMap<ItemStack, Open> OPEN = new IdentityHashMap<>();

    /** effects: listens for the game's projectile lookup and the end of each server tick. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(Lending::lend);
        NeoForge.EVENT_BUS.addListener(Lending::endOfTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> OPEN.clear());
    }

    static void lend(LivingGetProjectileEvent event) {
        if (!event.getProjectileItemStack().isEmpty() || !(event.getEntity() instanceof Player player)) return;
        var weapon = event.getProjectileWeaponItemStack();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem item)) return;
        var place = Carried.find(player, item.getAllSupportedProjectiles(weapon), true);
        if (place == null) return;
        var lent = place.stack().copy();
        var tag = new CompoundTag();
        tag.putString("store", place.store());
        tag.putInt("cell", place.cell());
        CustomData.update(DataComponents.CUSTOM_DATA, lent, t -> t.put(Carried.LENT, tag));
        event.setProjectileItemStack(lent);
        if (!player.level().isClientSide()) OPEN.put(lent, new Open(player, place.store(), place.cell(), place.stack().copyWithCount(1), lent.getCount()));
    }

    /** effects: after a weapon took {@code taken} out of {@code ammo} (vanilla's useAmmo): drops
     * the marker from both; on the server, settles {@code ammo}'s loan now. */
    public static void used(LivingEntity shooter, ItemStack ammo, ItemStack taken) {
        strip(ammo);
        strip(taken);
        if (shooter.level().isClientSide()) return;
        var open = OPEN.get(ammo);
        if (open != null) settle(ammo, open);
    }

    static void endOfTick(ServerTickEvent.Post event) {
        if (OPEN.isEmpty()) return;
        for (var e : OPEN.entrySet()) settle(e.getKey(), e.getValue());
        OPEN.clear();
    }

    private static void settle(ItemStack copy, Open open) {
        int now = copy.getCount();
        if (now < open.settled && !open.player.isRemoved()) {
            var there = Carried.at(open.player, open.store, open.cell);
            int held = ItemStack.isSameItemSameComponents(there, open.kind) ? there.getCount() : 0;
            int debit = Loan.debit(open.settled, now, held);
            if (debit > 0) Carried.takeFrom(open.player, open.store, open.cell, debit);
        }
        open.settled = now;
    }

    /** effects: removes the marker (and Backpacks+ 0.5's, should one survive in a world); a
     * custom-data component left empty is removed whole, so the stack matches an unmarked one. */
    static void strip(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !(data.contains(Carried.LENT) || data.contains(LEGACY))) return;
        var tag = data.copyTag();
        tag.remove(Carried.LENT);
        tag.remove(LEGACY);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    private static final String LEGACY = "backpacksplus_ammo";
}
