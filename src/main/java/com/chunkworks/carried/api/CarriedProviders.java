/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** The ordered registry of providers (D-0001), as the village and AzimuthLocation protocols keep
 * theirs: mods register from their constructors; the order of registration is the order stores
 * are drawn on.
 * <p>RI: no two providers share an id. The array is replaced, never changed, so a reader holding
 * it sees a consistent registry without a lock or an iterator. */
public final class CarriedProviders {
    private CarriedProviders() {}
    private static volatile CarriedProvider[] providers = new CarriedProvider[0];

    /** effects: adds the provider after those already registered.
     * throws: IllegalArgumentException when a provider with the same id is registered. */
    public static synchronized void register(CarriedProvider provider) {
        Objects.requireNonNull(provider);
        for (var p : providers) if (p.id().equals(provider.id())) throw new IllegalArgumentException("provider " + provider.id() + " already registered");
        var next = Arrays.copyOf(providers, providers.length + 1);
        next[providers.length] = provider;
        providers = next;
    }

    /** effects: the registered providers in order. */
    public static List<CarriedProvider> all() { return List.of(providers); }

    /** effects: the registry's current array, for iteration without allocation; never modified. */
    static CarriedProvider[] array() { return providers; }
}
