# Changelog for EverCrops (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

## [3.5.0] - 2026-5-6

### Added

- **Cave vines** (the glow berry vines hanging from cave ceilings) now catch up on growth while you're away. They grow downward and don't need any light. Glow berry state is preserved as normal.
- **Chorus flowers** (End dimension) now catch up while you're away. Because chorus flowers can branch in unpredictable directions, only one growth step is applied per catch-up event to keep things from getting out of hand.
- Added on/off settings for cave vines and chorus flowers in the server config. Both are on by default.

---

## [3.4.0] - 2026-5-5

### Added

- **Bamboo** now catches up on growth when you've been away. It needs skylight above it (torches don't count) and won't grow past 16 blocks tall — same rules as vanilla.
- **Twisting vines** (the tall green ones in crimson forests) now catch up while you're gone. They grow upward and don't need any light.
- **Weeping vines** (the red hanging ones in the Nether) now catch up while you're gone. They grow downward and don't need any light.
- Added on/off settings for bamboo, twisting vines, and weeping vines in the server config. All three are on by default.

## [3.3.1] - 2026-5-3

### Fixed

- **Beetroot** (and some modded crops) was being silently skipped and never catching up on growth while you were away. Fixed.

## [3.3.0] - 2026-5-2

### Added

- **Saplings** now catch up while you're gone — oak, birch, spruce, jungle, acacia, dark oak, cherry, and mangrove. They need enough light above them, same as in vanilla. Once the tree finishes growing, it stops being tracked.
- Added an on/off setting for saplings in the server config (on by default).

### Fixed

- Fixed a crash that could happen when kelp was growing in certain situations.

## [3.2.0] - 2026-4-27

### Added

- **Sugar cane**, **cactus**, and **kelp** now catch up on growth while you're away.
- Added on/off settings for each of these in the server config (all on by default).

## [3.1.0] - 2026-4-26

### Added

- **Sweet berry bushes**, **nether wart**, and **cocoa pods** now catch up on growth while you're away. Sweet berry bushes need enough light (same as vanilla). Nether wart and cocoa pods don't care about light at all.
- Added on/off settings for each of these in the server config (all on by default).

## [3.0.0] - 2026-4-25

### Added

- `/evercrops simulate <ticks> <radius>` — pretends a chunk has been unloaded for a given number of ticks, so nearby tracked crops will catch up on the next tick. Great for testing.
- `/evercrops tick <radius>` — forces every tracked crop nearby to try to grow right now, without waiting for the game to randomly pick it.
- `/evercrops inspect [x y z]` — shows catch-up info for a crop at a given position (defaults to the block at your feet).

### Changed

- Crop data is now saved directly inside your world folder instead of a separate database. Simpler, more reliable, and no extra files to worry about.

### Fixed

- Fixed a crash that happened when certain mods added plants that EverCrops didn't know how to handle. Those plants are now safely ignored.
