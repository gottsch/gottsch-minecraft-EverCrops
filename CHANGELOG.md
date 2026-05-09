# Changelog for EverCrops 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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