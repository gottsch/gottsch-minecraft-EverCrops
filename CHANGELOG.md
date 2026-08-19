# Changelog for EverCrops (Forge 1.20.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [4.1.0] - 2026-08-18

### Added

- 🐢 **Turtle eggs** now keep hatching while you're away. A nest sitting on sand carries on cracking, and eventually hatches into baby turtles — one per egg in the cluster, just like it would if you'd stood there and watched. Come back to a beach nest you left days ago and you'll find turtles, not the same untouched eggs. Eggs that aren't on sand still don't do anything, same as normal.
- New **Turtle eggs** setting (`turtleEggsEnabled`, on by default) to turn egg catch-up on or off, plus a setting to tune how fast eggs hatch while you're gone (`turtleEggHatchIntervalTicks`).
- If you'd rather not come back to a pile of new turtles all at once, `turtleEggSpawnTurtles` lets you have eggs crack all the way while you're away but wait for you to be nearby before actually hatching.
- Beaches can have a lot of wild turtle nests, so by default only nests you placed yourself are kept track of. Turn on `trackWildTurtleEggs` if you want wild beach nests to hatch while you're away too.
- `/evercrops inspect` on a turtle egg nest now shows whether it's on sand, how many eggs are in it, how many cracking stages are left, and roughly how long that will take.

---

## [4.0.0] - 2026-06-24

### Added

- 🐝 **Beehives and bee nests** now keep filling with honey while you're away. If a hive had bees and a flower nearby when you left, it keeps building up its honey level (up to full) based on how long you were gone — so you won't come back to a hive frozen exactly where you left it. Honey only builds during the daytime part of your time away (bees don't work at night or in the rain), and while you're nearby EverCrops quietly watches each hive to learn how fast it really produces, so the catch-up matches that hive's own pace rather than a one-size-fits-all guess. A hive with no bees, or no flowers in reach, won't gain honey while you're gone.
- New **Beehives** setting (`beehivesEnabled`, on by default) to turn hive catch-up on or off, plus a setting to tune the starting fill speed used for a hive EverCrops hasn't watched produce yet (`beehiveHoneyIntervalTicks`).
- `/evercrops inspect` on a beehive now shows its honey level, how fast it's filling, whether its bees and flower make it eligible, and whether it would catch up. `/evercrops simulate` and `/evercrops tick` now include hives too, so you can fast-forward and test honey catch-up just like crops. `/evercrops cleanup` also tidies leftover hive data.

### Changed

- Rebuilt the behind-the-scenes system that catches your plants up on the growth they missed while you were away. Every plant type — crops, melon and pumpkin stems, sugar cane, cactus, bamboo, kelp, vines, saplings, and chorus flowers — now runs through one shared, consistent growth engine instead of separate copies of the logic. Nothing changes about how fast or how much things grow; this is groundwork for upcoming features and makes EverCrops more reliable and easier to expand.

---

## [3.6.0] - 2026-06-23

### Added

- New **Modded Crops** setting (`moddedCropsEnabled`, on by default). EverCrops now recognizes a plant as a crop by how it behaves — a block that grows on its own through an age or stage — instead of relying only on a fixed built-in list. This setting controls whether such modded plants (that aren't one of the known vanilla types) are tracked.
- New **Denylist** setting — a list of block ids to skip during detection, for the rare block that looks like a crop but isn't. Fire and frosted ice are excluded by default; add your own ids if a pack needs it.

### Changed

- Reworked internally how EverCrops decides which blocks to track: a single behavior check replaces the old hardcoded block lists. All existing vanilla crops behave exactly as before — this mainly tidies the internals and lays the groundwork for broader crop support.

### Fixed

- Fixed a crash that could happen with torchflowers. A torchflower seed first grows into a small crop and then turns into the full torchflower plant. If you'd been away long enough for one to finish growing while you were gone, EverCrops could trip over the finished flower and crash the game. Torchflowers now finish growing cleanly, and a fully-grown one is no longer tracked once it's done.

---

## [3.5.3] - 2026-06-20

### Fixed

- Fixed a compatibility issue with right-click harvesting mods such as Harvest With Ease. After you'd been away and a crop caught up on its missed growth, harvesting and replanting it could make it instantly grow back — sometimes several times — because the replanted crop was mistaken for the same fully-grown one. Harvesting now properly resets a crop's growth timer, so replanted crops grow back at the normal rate. (This also stops vanilla sweet berry bushes from instantly refilling right after you pick them.)

---

## [3.5.2] - 2026-06-06

### Changed

- Version updated to stay in step with the NeoForge version of EverCrops. No gameplay changes.

---

## [3.5.1] - 2026-06-03

### Added

- Automatic background cleanup (and a new `/evercrops cleanup` command) that clears out leftover tracking entries for crops that were removed by something other than a player breaking them — pistons, explosions, water, or other mods. This keeps your world's crop data tidy over time. New server settings let you change how often it runs, or turn it off (it runs every 30 minutes by default).
- A new **Track Wild Plants** server setting (`trackWildVines`, off by default). When off, naturally-occurring wild kelp, vines, and chorus flowers aren't tracked for catch-up — keeping your world's saved data small even near big oceans and the Nether. Plants you place (or that villagers and machines plant) are always tracked. Turn it on if you want wild plants to catch up too. Bamboo is not affected by this setting — it always tracks regardless.

### Changed

- `/evercrops inspect` now also shows the actual block at that spot and its current growth stage/age, making progress easy to see — especially for saplings, whose first growth step is invisible until the tree pops up.

### Fixed

- Melon and pumpkin stems could sometimes spawn more than one melon or pumpkin at the same moment while catching up after you'd been away. A stem now produces at most one, just like normal.
- Crops left alone for a very long time, or sitting in a protected or claimed area, could cause a brief stutter when their chunk reloaded. Catch-up now stops doing extra work as soon as a crop is fully grown or its growth is blocked.
- Fixed a case where a crop could lose a little of its catch-up growth in the very instant it caught up. Crops now keep all of it.
- Kelp and vines no longer leave leftover tracking data behind each time they grow a block — their tracking now follows the plant as it grows up or down. Previously this could slowly pile up near oceans and the Nether.
- Bamboo placed by the player was never being tracked and wouldn't catch up on growth while you were away. Fixed.
- Bamboo sprouts (the small starting plant before the stalk appears) now properly shoot up to a full stalk during catch-up, instead of waiting for a random tick after you return.
- Kelp and vines no longer lose their catch-up tracking when you harvest them. Whether you break the tip or a block anywhere in the middle of the plant, the remaining piece keeps its catch-up data and stays tracked.

---

## [3.5.0] - 2026-5-9

### Added

- **Sweet berry bushes**, **nether wart**, and **cocoa pods** now catch up on growth while you're away. Sweet berry bushes need enough light (same as vanilla). Nether wart and cocoa pods don't care about light at all.
- **Sugar cane**, **cactus**, and **kelp** now catch up on growth while you're away.
- **Saplings** now catch up while you're gone — oak, birch, spruce, jungle, acacia, dark oak, cherry, and mangrove. They need enough light above them, same as in vanilla. Once the tree finishes growing, it stops being tracked.
- **Bamboo** now catches up on growth when you've been away. It needs skylight above it (torches don't count) and won't grow past 16 blocks tall — same rules as vanilla.
- **Twisting vines** (the tall green ones in crimson forests) now catch up while you're gone. They grow upward and don't need any light.
- **Weeping vines** (the red hanging ones in the Nether) now catch up while you're gone. They grow downward and don't need any light.
- **Cave vines** (the glow berry vines hanging from cave ceilings) now catch up on growth while you're away. They grow downward and don't need any light. Glow berry state is preserved as normal.
- **Chorus flowers** (End dimension) now catch up while you're away. Because chorus flowers can branch in unpredictable directions, only one growth step is applied per catch-up event to keep things from getting out of hand.
- Added on/off settings for all of the above in the server config (all on by default).
- `/evercrops simulate <ticks> <radius>` — pretends a chunk has been unloaded for a given number of ticks, so nearby tracked crops will catch up on the next tick. Great for testing.
- `/evercrops tick <radius>` — forces every tracked crop nearby to try to grow right now, without waiting for the game to randomly pick it.
- `/evercrops inspect [x y z]` — shows catch-up info for a crop at a given position (defaults to the block at your feet).

### Changed

- Crop data is now saved directly inside your world folder instead of a separate database. Simpler, more reliable, and no extra files to worry about.
- removed GottschCore dependency. It was only used for Config.

### Fixed

- Fixed a crash that happened when certain mods added plants that EverCrops didn't know how to handle. Those plants are now safely ignored.
- Fixed beetroot (and some modded crops) being silently skipped and never catching up on growth while you were away.
- Fixed a crash that could happen when kelp was growing in certain situations.

---

## [3.0.0] - 2026-4-25

### Added

- `/evercrops simulate <ticks>` command — backdates all tracked crop entries in the current dimension by the given number of ticks, allowing offline-growth logic to be triggered immediately on the next random tick. Useful for testing.
- `/evercrops tick <radius>` command — forces a `randomTick` on every tracked crop block within the given radius of the player, applying growth instantly without waiting for random tick scheduling.
- `/evercrops inspect [x y z]` command — displays the stored `CropState` for a block position (defaults to the player's feet), including call/growth deltas and whether offline growth would trigger on the next tick.

### Changed

- Replaced RocksDB persistence with Minecraft's built-in `SavedData` system. Crop state is now stored as NBT in `<world>/data/evercrops.dat` per dimension — no native libraries, no manual lifecycle management, and no platform-specific binaries required.

### Fixed

- Fixed crash (`IllegalArgumentException: Cannot get property age`) caused by mods that extend `CropBlock` or `StemBlock` but register blocks (e.g. `minecraft:oxeye_daisy`) whose `StateDefinition` does not include the standard age property. The mixin now guards all age-property access and skips incompatible blocks silently.

## [2.0.0] - 2025-5-16

### Changed

- Switched from MapDb to RocksDb for Crop management.

## [1.0.1] - 2025-3-21

### Changed

- Added gottschcore dependency to mods.toml

## [1.0.0] - 2025-3-19
- Initial release.