/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.api;

import java.util.List;
import net.minecraft.world.entity.player.Player;

/** A mod that lets players carry storage: it lists a player's stores when asked. Registered once
 * with {@link CarriedProviders#register} from the mod's constructor (D-0001). */
public interface CarriedProvider {
    /** effects: this provider's namespaced id, e.g. {@code backpacksplus}. */
    String id();

    /**
     * requires: nothing about the side: called on the logical server and on the owning player's
     * client alike.
     * effects: appends to {@code out} every store {@code player} carries now, in the order the
     * player draws on them (a worn bag before one in a pocket). Changes nothing in the world.
     * Called per frame by HUDs and per tick by gunnery, so it should allocate nothing on the
     * steady state (reuse store objects).
     */
    void stores(Player player, List<CarriedStore> out);
}
