# Changelog for EverCrops 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-4-25

### Added

- `/evercrops simulate <ticks>` command — backdates all tracked crop entries in the current dimension by the given number of ticks, allowing offline-growth logic to be triggered immediately on the next random tick. Useful for testing.
- `/evercrops tick <radius>` command — forces a `randomTick` on every tracked crop block within the given radius of the player, applying growth instantly without waiting for random tick scheduling.
- `/evercrops inspect [x y z]` command — displays the stored `CropState` for a block position (defaults to the player's feet), including call/growth deltas and whether offline growth would trigger on the next tick.

### Changed

- Replaced RocksDB persistence with Minecraft's built-in `SavedData` system. Crop state is now stored as NBT in `<world>/data/evercrops.dat` per dimension — no native libraries, no manual lifecycle management, and no platform-specific binaries required.

### Fixed

- Fixed crash (`IllegalArgumentException: Cannot get property age`) caused by mods that extend `CropBlock` or `StemBlock` but register blocks (e.g. `minecraft:oxeye_daisy`) whose `StateDefinition` does not include the standard age property. The mixin now guards all age-property access and skips incompatible blocks silently.