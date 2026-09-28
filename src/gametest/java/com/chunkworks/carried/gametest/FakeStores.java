/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.gametest;

import com.chunkworks.carried.api.CarriedProvider;
import com.chunkworks.carried.api.CarriedStore;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Player;

/** The test provider: the stores a test handed a player, in the order handed. */
final class FakeStores implements CarriedProvider {
    private static final Map<Player, List<FakeStore>> CARRIED = new WeakHashMap<>();

    /** effects: the player carries exactly these stores from now on. */
    static void carry(Player player, FakeStore... stores) { CARRIED.put(player, List.of(stores)); }

    @Override public String id() { return "carried_gametest"; }
    @Override public void stores(Player player, List<CarriedStore> out) {
        var mine = CARRIED.get(player);
        if (mine == null) return;
        for (int i = 0; i < mine.size(); i++) out.add(mine.get(i));
    }
}
