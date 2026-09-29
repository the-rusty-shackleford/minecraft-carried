# Carried

One answer to "what does this player carry?" for Minecraft 1.21.1 / NeoForge 21.1: the inventory
and every store another mod lets a player carry, such as a worn backpack. A mod that counts, takes
or gives a player's items asks Carried, and sees the bags; a mod that adds carried storage
registers with it once, and every such mod sees it. By Rusty Shackleford and nfx, AGPL-3.0-or-later.

Carried also teaches vanilla to use the stores: items picked up off the ground, ammunition for bows
and crossbows (and anything that asks the game for a projectile), the recipe book's count and fill,
and a villager trade's payment.

## Using it

Nest it in your jar and compile against it (`./gradlew publishToMavenLocal` here first):

```groovy
repositories { mavenLocal { content { includeGroup "com.chunkworks.carried" } } }
dependencies {
    jarJar(implementation("com.chunkworks.carried:carried")) { version { strictly "[1.0,2.0)"; prefer "1.0.0" } }
}
```

- Reads, on either side: `Carried.count(player, Items.EMERALD)`, `has`, `find`, `at`, `forEach`.
- Changes, on the server: `Carried.take(player, s -> s.is(Items.EMERALD), 30, taken -> {})` is all
  or nothing (a payment); `draw` fills a slot; `give` and `giveOrDrop` place a stack as the
  inventory would with the stores in it.

## Providing storage

Implement `CarriedProvider` and `CarriedStore` and call `CarriedProviders.register(...)` from your
mod's constructor. A store is storage, never holstered gear; its `peek` is read-only; its `write`
replaces its contents as one change. Backpacks+ (`BagStores`, `BagStore`) is the reference provider.

## Status

1.0.0, released 2026-09-29, nested in Backpacks+ 0.6.0 and its consumers. `knowledge/decisions/` has the rules and why;
`knowledge/design/audit.md` the survey they rest on.
