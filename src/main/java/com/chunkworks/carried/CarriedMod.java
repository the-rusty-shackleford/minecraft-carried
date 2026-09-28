/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried;

import com.chunkworks.carried.hook.Lending;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** The protocol's mod entry: registers the lending listeners. Providers register themselves with
 * CarriedProviders from their own constructors. */
@Mod("carried")
public final class CarriedMod {
    public CarriedMod(IEventBus bus) { Lending.register(); }
}
