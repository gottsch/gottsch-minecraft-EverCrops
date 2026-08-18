/*
 * This file is part of EverCrops.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * EverCrops is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverCrops is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverCrops.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.evercrops.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Public entry point for the EverCrops catch-up library (v4). Expansion mods (EC:DT, EC:FD,
 * EverBees) depend on this package instead of EverCrops' internals: a stable surface for the
 * catch-up engine, the per-dimension state store, capability detection, and the host config.
 *
 * <p>Shaped after the EverFurnace API: the host binds the loader-specific pieces once at startup —
 * {@link #bindRegistry(CatchUpRegistry)} (the {@code SavedData}-backed store) and
 * {@link #bindConfig(EverCropsConfigView)} (live config) — and everything else reads through the
 * bound delegates with safe defaults. This keeps the {@code api} package free of any dependency on
 * the host's {@code core} classes (dependency arrow: {@code core → api}).
 *
 * @author Mark Gottschling
 */
public final class EverCropsApi {

    /** Average ticks between random ticks for one block (vanilla 1/1365 per game-tick ≈ 1350). */
    public static final int AVG_CALL_TICK_INTERVAL = 1350;

    private EverCropsApi() {}

    // -------------------------------------------------
    // Bound delegates (safe defaults until the host binds the real ones)
    // -------------------------------------------------

    private static volatile CatchUpRegistry registry = new CatchUpRegistry() {
        @Override public Optional<CropState> get(ServerLevel level, BlockPos pos) { return Optional.empty(); }
        @Override public void put(ServerLevel level, BlockPos pos, CropState state) {}
        @Override public void remove(ServerLevel level, BlockPos pos) {}
        @Override public int cleanup(ServerLevel level, Predicate<BlockState> isCropBlock) { return 0; }
    };

    private static volatile EverCropsConfigView config = new EverCropsConfigView() {
        @Override public boolean cropsEnabled() { return true; }
        @Override public boolean stemCropsEnabled() { return true; }
        @Override public boolean bushCropsEnabled() { return true; }
        @Override public boolean columnCropsEnabled() { return true; }
        @Override public boolean saplingCropsEnabled() { return true; }
        @Override public boolean bambooEnabled() { return true; }
        @Override public boolean twistingVinesEnabled() { return true; }
        @Override public boolean weepingVinesEnabled() { return true; }
        @Override public boolean caveVinesEnabled() { return true; }
        @Override public boolean chorusFlowerEnabled() { return true; }
        @Override public boolean beehivesEnabled() { return true; }
        @Override public int beehiveHoneyIntervalTicks() { return 1500; }
        @Override public boolean turtleEggsEnabled() { return true; }
        @Override public int turtleEggHatchIntervalTicks() { return 32_000; }
        @Override public boolean turtleEggSpawnTurtles() { return true; }
        @Override public boolean trackWildTurtleEggs() { return false; }
        @Override public boolean moddedCropsEnabled() { return true; }
        @Override public boolean trackWildVines() { return false; }
        @Override public boolean autoCleanupEnabled() { return true; }
        @Override public int autoCleanupIntervalTicks() { return 36_000; }
    };

    /** Bind the host's {@code SavedData}-backed registry. Called once during mod construction. */
    public static void bindRegistry(CatchUpRegistry impl) {
        if (impl != null) {
            registry = impl;
        }
    }

    /** Bind the host's live config view. Called once during mod construction (after config registration). */
    public static void bindConfig(EverCropsConfigView view) {
        if (view != null) {
            config = view;
        }
    }

    /** The live host config view (never null; safe defaults until {@link #bindConfig} is called). */
    public static EverCropsConfigView config() {
        return config;
    }

    // -------------------------------------------------
    // Registry facade
    // -------------------------------------------------

    public static Optional<CropState> get(ServerLevel level, BlockPos pos) {
        return registry.get(level, pos);
    }

    public static void put(ServerLevel level, BlockPos pos, CropState state) {
        registry.put(level, pos, state);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        registry.remove(level, pos);
    }

    public static int cleanup(ServerLevel level, Predicate<BlockState> isCropBlock) {
        return registry.cleanup(level, isCropBlock);
    }

    /** Register a predicate identifying block states this mod still tracks (for shared cleanup). */
    public static void registerCleanupPredicate(Predicate<BlockState> predicate) {
        CropBlockPredicates.register(predicate);
    }

    /** True if any registered cleanup predicate considers this block a live crop. */
    public static boolean isTrackedByAnyPredicate(BlockState state) {
        return CropBlockPredicates.isCropBlock(state);
    }

    // -------------------------------------------------
    // Catch-up engine
    // -------------------------------------------------

    /** Build a fresh {@link CropState} stamped with the current game time and light level. */
    public static CropState createState(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        int light = level.getRawBrightness(pos, 0);
        return new CropState()
                .setLastCallGameTime(now)
                .setLastGrowthGameTime(now)
                .setLastCallLightLevel(light)
                .setLastGrowthLightLevel(light);
    }

    /**
     * Decide whether catch-up should fire this tick and, if so, how many growth steps are owed.
     * Mutates {@code state}'s timing fields as a side effect; the caller persists it and performs
     * the actual block updates (typically via {@link #catchUp}).
     */
    public static int beginCatchUp(ServerLevel level, BlockPos pos, CropState state,
                                   int avgGrowthInterval, boolean requiresLight) {
        return CatchUpDecision.computeSteps(state, level.getGameTime(), level.getRawBrightness(pos, 0),
                level.isDay(), AVG_CALL_TICK_INTERVAL, avgGrowthInterval, requiresLight);
    }

    /**
     * Run a full catch-up pass: compute the owed steps, then apply them with {@code strategy}.
     *
     * @return true if catch-up actually grew the block this tick (caller should {@code ci.cancel()}).
     */
    public static boolean catchUp(ServerLevel level, BlockPos pos, BlockState state, CropState cropState,
                                  int avgGrowthInterval, boolean requiresLight, RandomSource random,
                                  CatchUpStrategy strategy) {
        int steps = beginCatchUp(level, pos, cropState, avgGrowthInterval, requiresLight);
        if (steps <= 0) {
            return false;
        }
        return strategy.grow(level, pos, state, steps, random);
    }

    /**
     * Detect an in-place harvest (age/stage regression) on {@code state}'s block and, if found, reset
     * the growth clock so pending catch-up isn't re-applied to the replant. Resolves the block's
     * growth property via {@link GrowthProperties}.
     *
     * @return true if a harvest reset was detected (caller should persist and skip catch-up this tick)
     */
    public static boolean handleInPlaceHarvest(ServerLevel level, BlockPos pos, CropState cropState, BlockState state) {
        IntegerProperty growth = GrowthProperties.growthPropertyOf(state.getBlock());
        int currentAge = (growth != null) ? state.getValue(growth) : -1;
        return handleInPlaceHarvest(level, pos, cropState, currentAge);
    }

    /**
     * In-place-harvest detection from a pre-resolved age ({@code -1} = no growth property).
     */
    public static boolean handleInPlaceHarvest(ServerLevel level, BlockPos pos, CropState cropState, int currentAge) {
        return CatchUpDecision.detectInPlaceHarvest(cropState,
                level.getGameTime(), level.getRawBrightness(pos, 0), currentAge);
    }
}
