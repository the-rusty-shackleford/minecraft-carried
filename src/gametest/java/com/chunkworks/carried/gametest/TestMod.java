/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.gametest;

import com.chunkworks.carried.api.CarriedProviders;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Development-only entry point; never packaged in the production jar. */
@Mod("carried_gametest")
public final class TestMod {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("carried_gametest");
    static final DeferredItem<Item> SELF_FEEDING_GUN = ITEMS.registerItem("self_feeding_gun", SelfFeedingGun::new, new Item.Properties().stacksTo(1));

    public TestMod(IEventBus bus) {
        ITEMS.register(bus);
        CarriedProviders.register(new FakeStores());
    }
}
