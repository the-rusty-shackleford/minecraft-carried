# Carried

Version 1.0.0, built 2026-09-28, **unreleased**: ships nested in Backpacks+ 0.6.0 and in each
migrated consumer as one pack, on Rusty's go. Minecraft 1.21.1, NeoForge 21.1.248, Java 21, both
sides. Mod id `carried`, `com.chunkworks.carried`, AGPL-3.0-or-later, by Rusty Shackleford and nfx.
No remote yet.

Rusty, 2026-09-28, after Village Deed could not see the emeralds in his bag: one modification that
makes everything that sees the inventory see the backpack. D-0001 is the protocol, D-0002 to D-0005
the vanilla hooks it carries (pickups, lent ammunition, the recipe book, trading);
`design/audit.md` is the survey behind it, what it reaches and what it does not.

## Shape

- `src/domain` (JDK-only, JUnit): `Placement` (a give's two passes), `Withdrawal` (all or nothing;
  up to), `Loan` (what a store pays for a lent projectile).
- `src/main`: `api` (`Carried`, `CarriedStore`, `CarriedProvider`, `CarriedProviders`, `Reach`),
  `hook.Lending`, six mixins (`ItemEntityMixin`, `AbstractArrowMixin`, `ProjectileWeaponItemMixin`,
  `InventoryMixin`, `ServerPlaceRecipeMixin`, `MerchantMenuMixin`).
- `src/gametest`: the `carried_gametest` mod: a fake provider and a weapon that shrinks its lent
  stack itself, as Create's potato cannon does.

## Providers and consumers

- Provider: Backpacks+ 0.6.0 (its D-0032).
- Consumers, each migrated in its own repo with its own decision: Village Deed, Warehouse Manager,
  Ranged Weapons Mod, Azimuth, Magical Map, Vanilla Wheels, Schnappviecher. Craftlight gets the bag
  through D-0004 with no change.

## Verified

14 JUnit, 14 GameTests, all green; each hook's test failed with the hook removed. Counting over a
full inventory and a 36-cell store: 150–172 ns, 0 bytes allocated over 100,000 calls. Not run with
Create; not seen in play.
