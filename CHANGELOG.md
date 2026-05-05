# Changelog for EverCrops (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
