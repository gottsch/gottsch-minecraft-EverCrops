# EverCrops

**Crops keep growing while you're away from your farm.**

EverCrops uses Mixin hooks to track when each crop block last grew. When you return to a loaded chunk, any elapsed offline time is applied as catch-up growth on the next random tick — no block entities, no extra world gen, no new blocks.

---

## How it works

EverCrops does **not** grow crops while chunks are unloaded. Instead, it records the last time each crop block was seen and, when the chunk loads again and the block receives a random tick, calculates how many growth steps should have occurred during the gap. Those steps are applied immediately (up to a cap). The result looks like multiple bonemeal applications.

Note: in single-player worlds, time does not pass while you are not playing, so no catch-up accumulates.

---

## Supported blocks

| Category | Blocks |
|---|---|
| **Standard crops** | Wheat, carrots, potatoes, beetroot, torchflower |
| **Stem crops** | Melon stems, pumpkin stems (including fruit spread) |
| **Bush / special crops** | Sweet berry bushes, nether wart, cocoa pods |
| **Column crops** | Sugar cane, cactus, kelp |
| **Bamboo** | Bamboo (sky-light required, max height 16) |
| **Nether vines** | Twisting vines (grows up), weeping vines (grows down) |
| **Cave vines** | Glow berries (grows down) |
| **Chorus flowers** | Chorus flowers (End; one growth step per catch-up event) |
| **Saplings** | Oak, birch, spruce, jungle, acacia, dark oak, cherry, mangrove |
| **Beehives** | Beehives and bee nests (honey level; daytime only, per-hive learned rate) |
| **Turtle eggs** | Turtle egg clusters on sand (crack, then hatch into one baby turtle per egg) |

Most modded crops that subclass any of the above vanilla classes are also supported automatically.

---

## Server config

EverCrops adds a per-world server config (`serverconfig/evercrops-server.toml`) with toggle flags for each crop category:

```toml
[crops]
    # Per-category toggles
    cropsEnabled = true
    stemCropsEnabled = true
    bushCropsEnabled = true
    columnCropsEnabled = true
    saplingCropsEnabled = true
    bambooEnabled = true
    twistingVinesEnabled = true
    weepingVinesEnabled = true
    caveVinesEnabled = true
    chorusFlowerEnabled = true
    beehivesEnabled = true
    turtleEggsEnabled = true

    # Rate tuning
    beehiveHoneyIntervalTicks = 1500      # cold-start ticks per honey level, until a hive's own rate is learned
    turtleEggHatchIntervalTicks = 32000   # ticks per hatch stage (3 stages from fresh egg to turtles)

    # Behaviour
    turtleEggSpawnTurtles = true          # false = crack eggs offline, but leave the hatch to vanilla
    trackWildVines = false                # track wild kelp/vines/chorus, not just placed ones
    trackWildTurtleEggs = false           # track wild beach nests, not just placed ones
    moddedCropsEnabled = true             # modded growables detected by capability but matching no category
    denylist = ["minecraft:fire", "minecraft:soul_fire", "minecraft:frosted_ice"]

[cleanup]
    autoCleanupEnabled = true
    autoCleanupIntervalTicks = 36000
```

---

## Commands

| Command                                | Description |
|----------------------------------------|---|
| `/evercrops simulate [ticks] [radius]` | Backdates all tracked crops within `radius` blocks of the player by `ticks`, triggering catch-up on the next random tick. Useful for testing. |
| `/evercrops tick [radius]`             | Forces a `randomTick` on every tracked crop within `radius` blocks of the player right now. |
| `/evercrops inspect [x y z]`           | Shows the stored `CropState` for a position (defaults to the player's feet). |
| `/evercrops cleanup`                   | Removes tracking entries in loaded chunks whose block is no longer a tracked crop or hive. |

---

## Compatibility

EverCrops only uses `@Inject`, `@Accessor`, and `@Invoker` Mixin annotations. No new blocks, items, or block entities are introduced. It should be compatible with any mod that also uses Mixins.

---

## Technical notes

Crop state is stored as NBT in `<world>/data/evercrops.dat` per dimension using Minecraft's built-in `SavedData` system; beehive state lives alongside it in `evercrops_bees.dat`. No native libraries or manual lifecycle management required.
