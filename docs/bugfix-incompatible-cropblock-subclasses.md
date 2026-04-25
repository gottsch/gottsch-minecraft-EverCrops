# Bug Fix: "Cannot get property age" crash with modded CropBlock subclasses

## Symptom

```
java.lang.IllegalArgumentException: Cannot get property IntegerProperty{name=age, ..., values=[0,1,2,3,4,5,6,7]}
    as it does not exist in Block{minecraft:oxeye_daisy}
    at net.minecraft.world.level.block.state.StateHolder.getValue(...)
    at net.minecraft.world.level.block.CropBlock.getAge(CropBlock.java:51)
    at ...CropBlock.handler$...$everCrops_randomTick(CropBlock.java:...)
```

The world tick crashes, usually triggered after a chunk is reloaded and EverCrops
attempts to apply offline growth to a crop position.

## Root Cause

EverCrops uses `@Mixin(CropBlock.class)` and `@Mixin(StemBlock.class)` to intercept
`randomTick`. These mixins fire on **every** class that extends `CropBlock` or
`StemBlock` — including blocks added by other mods.

Some mods create decorative or growth-capable blocks that extend `CropBlock` but
register them under vanilla block IDs (e.g. `minecraft:oxeye_daisy`) or otherwise
build a `StateDefinition` that does **not** include `CropBlock.AGE`
(`IntegerProperty{name=age, values=[0..7]}`).

When EverCrops finds a stored `CropState` for such a position and enters the offline
growth path, it calls:

```java
int age = ((CropBlock)(Object)this).getAge(currentState);
// internally: currentState.getValue(this.getAgeProperty())
```

Because the block state's `StateDefinition` never registered `CropBlock.AGE`,
`BlockState.getValue()` throws `IllegalArgumentException`.

Mods confirmed to trigger this (visible in the mixin chain in the stack trace):
- Naturalist
- FarmAndCharm
- Furniture mod (various)
- Taniwha's plant mods
- Any mod whose crops extend `CropBlock` but define a custom or absent age property

A secondary problem: if a stale `CropState` entry exists from a previous crop at
that position, the `else` branch in `setPlacedBy` can create a new entry for an
incompatible block, priming the position for the crash on the next chunk reload.

## Fix

Add a property-existence guard at the top of every mixin method that accesses the
age property, and strengthen the `setPlacedBy` guard to never create entries for
incompatible blocks.

### CropBlockMixin.java

In **both** `@Inject` methods (`randomTick` HEAD and `randomTick` INVOKE/setBlock),
add this as the first statement:

```java
if (!state.hasProperty(((CropBlock)(Object)this).getAgeProperty())) {
    return;
}
```

`CropBlock.AGE` is a `public static` field, so no access transformer or accessor
mixin is needed. Note: this also skips CropBlock subclasses that override
`getAgeProperty()` with a custom property — that is intentional, since our growth
logic would call `getAge()` / `getStateForAge()` which internally use the block's own
property, and applying those to an incompatible state would crash identically.

Full example for the HEAD injection:

```java
@Inject(method = "randomTick", at = @At(value = "HEAD"))
public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                  RandomSource randomSource, CallbackInfo ci) {
    if (!state.hasProperty(CropBlock.AGE)) {
        return;
    }
    // ... rest of method unchanged
}
```

### StemBlockMixin.java

`StemBlock.AGE` is a `public static` field, so the check is simpler. Add to both
`@Inject` methods:

```java
if (!state.hasProperty(StemBlock.AGE)) {
    return;
}
```

### BlockMixin.java — setPlacedBy injection

Change the `instanceof` check so a `CropState` entry is only created when the placed
block's state actually carries the age property:

**Before:**
```java
if (block instanceof CropBlock || block instanceof StemBlock) {
    // create and store CropState ...
}
```

**After:**
```java
if ((block instanceof CropBlock && state.hasProperty(CropBlock.AGE))
        || (block instanceof StemBlock && state.hasProperty(StemBlock.AGE))) {
    // create and store CropState ...
}
```

## Why this is enough

- The HEAD guard prevents the offline growth path from ever reaching `getAge()` on
  an incompatible state.
- The `setBlock` INVOKE guard prevents a spurious `put` from recording growth time
  on an incompatible block.
- The `setPlacedBy` guard stops stale entries from being created in the first place,
  so incompatible blocks never accumulate `CropState` records across sessions.

## Affected versions

Any EverCrops version that mixes into `CropBlock.randomTick` without this guard.
Apply the same three-location fix to all earlier branches.
