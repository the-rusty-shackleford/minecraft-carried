---
title: The audit behind Carried — who reads a player's inventory
date: 2026-09
---

# The audit behind Carried (2026-09-28)

Before the protocol was built: every place in our mods, and in the pack's other jars, that reads
or changes a player's inventory, and whether a carried bag should count there. Our mods were read
in source; the pack's jars (91 client jars and the jars they nest, Mowzie's Mobs copied from the
box, Minecraft and NeoForge 21.1.248 themselves) by walking every method's bytecode for calls into
`Inventory` and the related APIs. The box's other jars matched the client's by SHA-1; Chunky (a
server-only pregenerator) was not scanned.

## Our mods — where the bag counts

| Repo | Site | Does | Bag counts? |
|---|---|---|---|
| Village Deed | `DeedPurchase` count, take, change, deed | count, take (a payment), give | yes |
| Warehouse Manager | `Pooled` held, take, give, draw; `Index` withdrawal; the recipe-book mixin | count, take, give | yes (the recipe-book count through D-0004) |
| Ranged Weapons Mod | `RoundSources` + `BackpackPockets` (its own bag adapter) | count, take rounds | yes; the adapter retires |
| Ranged Weapons Mod | the Fill button, magazine reload, handed-back rounds | count, take, give | yes (Rusty, overturning its D-0023's magazines-mode exclusion) |
| Azimuth | lodestone compasses, recovery compass | read | yes |
| Magical Map | atlas carried | read | yes |
| Vanilla Wheels | disc and lead given back | give | yes |
| Schnappviecher | the thief's random slot | take | yes (Rusty: "yes"; its D-0002, "steal anything") |
| Craftlight | ingredients, through vanilla's placement | take | yes, through D-0004, no code change |
| Backpacks+ | D-0026 lending, D-0029 pickups | lend, give | moved here (D-0002, D-0003) |

Left out, the bag does not count there: Craftlight's output-room check (it sizes shift-click's
destination, the inventory), Ranged Weapons Mod's hand-stack set-aside, packed-vehicle recovery
(nested storage), Mobile Camp's hotbar guard, Quick Slot (hand, hotbar, rendering), Threat Mesh (the
turret's own container). The other twelve repos read only hands and armour outside their tests.

## The pack — what a hook here can reach

- **Reached**: pickups (D-0002); every bow-type weapon that asks `Player.getProjectile` (vanilla
  bows and crossbows, Mowzie's blowgun, Create's potato cannon; D-0003); vanilla's recipe book at
  the inventory, the table and the furnaces, which Craftlight rides on (D-0004); vanilla's merchant
  menu (D-0005); EMI, through Backpacks+'s handlers over the bag's real slots (Backpacks+ D-0033).
- **Not reached without a mixin into that mod** (none wanted for now, Rusty: "none for now"):
  Alex's Mobs' four ammunition loops (Blood Sprayer, Hemolymph Blaster, Pocket Sand, Stink Ray);
  Create's shop payments, track and girder cost, chain conveyors, super glue, blueprints, item hatch;
  Block Factory's Bosses' cannon; Dusty Decorations' 29 right-click procedures; vanilla's pick-block
  (it picks an inventory slot by number); Mowzie's Umvuthi trade menu; gives by other mods through
  `Inventory.add` (dozens; Backpacks+ D-0029 chose not to send them into bags).
- **Precedent**: Curios already mixes into `Inventory.contains` and `hasAnyMatching` so that "does
  the player have X" sees its slots. Architectury and Moonlight mix into other `Inventory` methods
  (its tick, the death drop), not the ones hooked here.

## Found on the way

- Create's potato cannon fired a bag's potatoes for free (fixed by D-0003).
- A packed Vanilla Wheels truck was storable in a bag (Backpacks+ D-0034).
- Village Deed counted only the main slots: emeralds in the offhand did not count.
- EMI read fixed slot ranges and never saw the bag (Backpacks+ D-0033).
