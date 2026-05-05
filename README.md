# EverCrops

**Crops keep growing while you're away from your farm.**

EverCrops uses Mixin hooks to track when each crop block last grew. When you return to a loaded chunk, any elapsed offline time is applied as catch-up growth on the next random tick — no block entities, no extra world gen, no new blocks.

---

## How it works

EverCrops does **not** grow crops while chunks are unloaded. Instead, it records the last time each crop block was seen and, when the chunk loads again and the block receives a random tick, calculates how many growth steps should have occurred during the gap. Those steps are applied immediately (up to a cap). The result looks like multiple bonemeal applications.

Note: in single-player worlds, time does not pass while you are not playing, so no catch-up accumulates.

---

## Supported crops

| Category | Crops |
|---|---|
| **Standard crops** | Wheat, carrots, potatoes, beetroot, pitcher plant, torchflower |
| **Stem crops** | Melon stems, pumpkin stems (including fruit spread) |
| **Bush / special crops** | Sweet berry bushes, nether wart, cocoa pods |
| **Column crops** | Sugar cane, cactus, kelp |
| **Bamboo** | Bamboo (sky-light required, max height 16) |
| **Nether vines** | Twisting vines (grows up), weeping vines (grows down) |
| **Saplings** | Oak, birch, spruce, jungle, acacia, dark oak, cherry, mangrove |

Most modded crops that subclass any of the above vanilla classes are also supported automatically.

---

## Server config

EverCrops adds a per-world server config (`serverconfig/evercrops-server.toml`) with toggle flags for each crop category:

```toml
[crops]
    # Enable catch-up growth for standard crops
    cropsEnabled = true
    # Enable catch-up growth for stem crops (melon/pumpkin)
    stemCropsEnabled = true
    # Enable catch-up growth for bush/special crops
    bushCropsEnabled = true
    # Enable catch-up growth for column crops (cane/cactus/kelp)
    columnCropsEnabled = true
    # Enable catch-up growth for saplings
    saplingCropsEnabled = true
    # Enable catch-up growth for bamboo
    bambooEnabled = true
    # Enable catch-up growth for twisting vines
    twistingVinesEnabled = true
    # Enable catch-up growth for weeping vines
    weepingVinesEnabled = true
```

---

## Commands

| Command                                | Description |
|----------------------------------------|---|
| `/evercrops simulate [ticks] [radius]` | Backdates all tracked crops within `radius` blocks of the player by `ticks`, triggering catch-up on the next random tick. Useful for testing. |
| `/evercrops tick [radius]`             | Forces a `randomTick` on every tracked crop within `radius` blocks of the player right now. |
| `/evercrops inspect [x y z]`           | Shows the stored `CropState` for a position (defaults to the player's feet). |

---

## Compatibility

EverCrops only uses `@Inject`, `@Accessor`, and `@Invoker` Mixin annotations. No new blocks, items, or block entities are introduced. It should be compatible with any mod that also uses Mixins.

---

## Technical notes

Crop state is stored as NBT in `<world>/data/evercrops.dat` per dimension using Minecraft's built-in `SavedData` system. No native libraries or manual lifecycle management required.
