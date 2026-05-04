# Changelog for EverCrops (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.3.1] - 2026-5-3

### Fixed

- Beetroot (and any `CropBlock` subclass that overrides `getAgeProperty()` to return a property other than `CropBlock.AGE`) was silently excluded from catch-up tracking. The `CropBlockMixin` guard and `ModEvents.isTracked()` now use virtual dispatch via `getAgeProperty()` so all crop subclasses are handled correctly regardless of which age property they declare.

## [3.3.0] - 2026-5-2

### Added

- Catch-up growth for **saplings** — oak, birch, spruce, jungle, acacia, dark oak, cherry, mangrove, and any modded sapling that extends `SaplingBlock`. Uses the vanilla two-stage STAGE property (0 → 1 → tree). Light check uses sky+block light on the block above (matching vanilla). After a successful tree grow, the CropState entry is removed and vanilla's `randomTick` is cancelled to prevent overwriting the new tree structure.
- `saplingCropsEnabled` server config flag (default `true`) — disable to opt out of sapling catch-up while keeping other categories active.
- `ModEvents` placement/break tracking extended to cover saplings.

### Fixed

- Removed invalid INVOKE inject from `KelpBlockMixin` — `GrowingPlantHeadBlock.randomTick` does not directly call `setBlockAndUpdate` in its bytecode (1.21.1), causing a critical injection failure at startup. The HEAD inject alone is sufficient for kelp catch-up.

## [3.2.0] - 2026-4-27

### Added

- Catch-up growth for **sugar cane** — AGE 0–15, spawns new cane above when AGE wraps, max column height 3, no light requirement.
- Catch-up growth for **cactus** — AGE 0–15, same wrapping pattern as sugar cane, max column height 3.
- Catch-up growth for **kelp** — AGE 0–25, each growth step places a new kelp head above (converting the old head to a kelp plant body), stops at AGE 25. Growth gated at ~14% per tick (mirroring vanilla probability).
- `ModEvents` placement/break tracking extended to cover sugar cane, cactus, and kelp.


## [3.1.0] - 2026-4-26

### Added

- Catch-up growth for **sweet berry bushes** — ages 0–3, requires light level ≥ 9 (matches vanilla).
- Catch-up growth for **nether wart** — ages 0–3, no light requirement.
- Catch-up growth for **cocoa pods** — ages 0–2, no light requirement.
- New `CropCatchUp` helper class consolidating the timing/threshold/light-gating logic shared by the new mixins.

### Changed

- `ModEvents` placement/break tracking generalized via a single `isTracked(BlockState)` guard now covering crops, stems, sweet berry bushes, nether wart, and cocoa pods.

## [3.0.0] - 2026-4-25

### Added

- `/evercrops simulate <ticks>` command — backdates all tracked crop entries in the current dimension by the given number of ticks, allowing offline-growth logic to be triggered immediately on the next random tick. Useful for testing.
- `/evercrops tick <radius>` command — forces a `randomTick` on every tracked crop block within the given radius of the player, applying growth instantly without waiting for random tick scheduling.
- `/evercrops inspect [x y z]` command — displays the stored `CropState` for a block position (defaults to the player's feet), including call/growth deltas and whether offline growth would trigger on the next tick.

### Changed

- Replaced RocksDB persistence with Minecraft's built-in `SavedData` system. Crop state is now stored as NBT in `<world>/data/evercrops.dat` per dimension — no native libraries, no manual lifecycle management, and no platform-specific binaries required.

### Fixed

- Fixed crash (`IllegalArgumentException: Cannot get property age`) caused by mods that extend `CropBlock` or `StemBlock` but register blocks (e.g. `minecraft:oxeye_daisy`) whose `StateDefinition` does not include the standard age property. The mixin now guards all age-property access and skips incompatible blocks silently.