/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.mixin;

import com.chunkworks.carried.api.Carried;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The recipe book counts what the stores hold (D-0004): vanilla's book, on the client to show a
 * recipe craftable and on the server to decide it can place one, asks the inventory for its
 * stacks here and nowhere else. The stores' simple stacks join the inventory's main slots, which
 * is all vanilla counts. */
@Mixin(Inventory.class)
abstract class InventoryMixin {
    @Shadow @Final public Player player;

    @Inject(method = "fillStackedContents", at = @At("TAIL"))
    private void carried$countStores(StackedContents contents, CallbackInfo ci) {
        Carried.forEachStored(player, (store, cell, stack) -> contents.accountSimpleStack(stack));
    }
}
