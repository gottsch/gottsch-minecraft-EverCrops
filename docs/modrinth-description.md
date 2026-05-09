# EverCrops

**Crops keep growing while you're away from your farm.**

Tired of trekking back to your wheat farm only to find it stuck at the same age it was when you left? EverCrops fixes that. Vanilla crops only tick when their chunk is loaded, which means a base you visit once a week effectively never grows. EverCrops makes your crops respect the time you spent away.

---

## How it works

EverCrops doesn't simulate growth on unloaded chunks (that would be expensive and laggy). Instead, it tracks **when each crop last ticked**, and on the next random tick after the chunk reloads, it **catches the crop up** based on how much game time has elapsed.

When a crop is placed, EverCrops registers it and stamps it with the current game time. From then on, every time you leave and return, the elapsed time is applied as catch-up growth. Walk away for an hour, a day, a week — when you come back and the chunk reloads, your crops advance as if they had been ticking the whole time. Same end result as if the chunk had stayed loaded, but with zero overhead while you're away.

> **Pre-existing crops:** Crops that already existed in your world before EverCrops was installed get registered the first time their chunk reloads near you, so they'll start catching up from that point forward.

> **Note:** In singleplayer, the world only ticks while you're playing. EverCrops catches crops up based on **in-game time elapsed**, not real-world time, so closing the game and coming back tomorrow won't fast-forward your farm.

---

## Supported crops

- 🌾 Wheat
- 🥕 Carrots
- 🥔 Potatoes
- 🌱 Beetroots
- 🍉 Melon stems (including fruit spread)
- 🎃 Pumpkin stems (including fruit spread)
- 🫐 Sweet berry bushes
- 🌶️ Nether wart
- 🌰 Cocoa pods
- 🎋 Sugar cane
- 🌵 Cactus
- 🌿 Kelp
- 🌳 Saplings (oak, birch, spruce, jungle, acacia, dark oak, cherry, mangrove, and modded saplings that extend `SaplingBlock`)
- ✨ Most modded crops that subclass any of the above vanilla classes

---

## Compatibility

EverCrops uses **only** `@Inject`, `@Accessor`, and `@Invoker` mixins — no overwrites, no replacements. It introduces **no custom blocks and no block entities**. This makes it broadly compatible with other mixin-based mods and means uninstalling it is safe: your world keeps all its existing crops, they just go back to vanilla behavior.

- ✅ Server-side friendly (clients don't need it installed for the growth logic to work)
- ✅ No worldgen changes
- ✅ No new items or blocks
- ✅ Works alongside other crop and farming mods that subclass `CropBlock` / `StemBlock`

---

## Why use EverCrops?

- **Long-distance bases stay productive.** Build a farm at your remote outpost and actually find food when you come back.
- **Multiplayer-friendly.** On servers, players who log in occasionally aren't penalized.
- **Lightweight.** No background ticking, no extra block entities, no chunk-loading.
- **Catch-up only.** Crops never advance further than they would have under vanilla rules — just no slower.

---

## Commands

EverCrops ships with admin/debug commands under `/evercrops` (requires op level 2). All commands target the block at the given position.

| Command | Purpose |
| --- | --- |
| `/evercrops inspect [pos]` | Print the crop's tracked state: position, block, age, last-call/last-growth game times, last light levels, and computed deltas. |
| `/evercrops tick [pos]` | Force a single random tick on the crop, so you can verify behavior without waiting. |
| `/evercrops simulate [pos] [ticks]` | Advance the crop's tracked "last call" and "last growth" times by a number of game ticks, then tick it — useful for testing how the catch-up math behaves over long absences. |

These are intended for server admins and modpack debugging. They have no effect on normal gameplay.

---

## Companion mods

Pair with **[EverFurnace](https://modrinth.com/mod/everfurnace)** to give the same catch-up treatment to your furnaces, blast furnaces, and smokers.
Pair with **[EverHopper](https://modrinth.com/mod/everhopper)** to give the same catch-up treatment to your hoppers.
---

## License

Licensed under **LGPL-3.0**. Source available on [GitHub](https://github.com/gottsch/EverCrops).
