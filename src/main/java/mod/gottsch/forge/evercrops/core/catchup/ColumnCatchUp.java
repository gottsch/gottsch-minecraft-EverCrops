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
package mod.gottsch.forge.evercrops.core.catchup;

import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.mixin.IBambooStalkBlockMixin;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Shared catch-up logic for {@link GrowingPlantHeadBlock} column crops whose head walks one block per
 * growth step (kelp, twisting vines, weeping vines, cave vines). These differ only in growth
 * direction and the validity check for the next block; everything else — wild-vine gating,
 * head-walking, entry relocation, vanilla-cancel, and online relocation — is identical, and lives
 * here so the four mixins are thin wrappers.
 *
 * <p>The growth axis is the shared {@link GrowingPlantHeadBlock#AGE} (max 25); each step cycles AGE
 * and places a new head at the next position via {@code setBlockAndUpdate}.
 *
 * @author Mark Gottschling
 */
public final class ColumnCatchUp {

    /** Max head-walk iterations per tick (safety bound; a column can't meaningfully grow more). */
    private static final int STEP_LIMIT = 50;

    private ColumnCatchUp() {}

    /** Whether a head may grow into {@code next} (water for kelp, valid nether-vine state, air, …). */
    @FunctionalInterface
    public interface GrowthCheck {
        boolean canGrowInto(ServerLevel level, BlockPos next);
    }

    /**
     * One growth step for a spawn-above column crop (sugar cane / cactus). Called only when the block
     * above ({@code above}) is empty and the column is below its height cap. Implementations perform
     * their own grow-event hooks and {@code setBlock}s, advancing age in place (return {@code currentPos})
     * or spawning a new top block (return {@code above}); return {@code null} to stop (e.g. vetoed growth).
     */
    @FunctionalInterface
    public interface SpawnStep {
        BlockPos grow(ServerLevel level, BlockPos currentPos, BlockState currentState, BlockPos above);
    }

    /**
     * The shared HEAD-inject body: catch up a head-walking column crop. Assumes the caller has already
     * applied its {@code instanceof}/config guards. Cancels the vanilla tick (via {@code ci}) when
     * catch-up moved the head, so vanilla can't add a step and orphan the relocated entry.
     */
    public static void headCatchUp(ServerLevel level, BlockPos pos, BlockState state, int avgGrowthInterval,
                                   Direction growthDir, GrowthCheck check, CallbackInfo ci) {
        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Wild (worldgen) heads are tracked only when the player opts in. Placed ones are
            // registered via the place event, and tracking follows the head via TAIL relocation.
            if (Config.SERVER.trackWildVines.get()) {
                CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            }
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, avgGrowthInterval, false);
        if (steps <= 0) {
            CropRegistry.put(level, pos, cropState);
            return;
        }

        BlockPos currentPos = pos;
        BlockState currentState = state;
        int limit = Math.min(steps, STEP_LIMIT);
        for (int i = 0; i < limit; i++) {
            int age = currentState.getValue(GrowingPlantHeadBlock.AGE);
            if (age >= 25) break;
            BlockPos next = currentPos.relative(growthDir);
            if (!check.canGrowInto(level, next)) break;
            if (!ForgeHooks.onCropsGrowPre(level, next, currentState, true)) break;
            BlockState newHead = currentState.cycle(GrowingPlantHeadBlock.AGE);
            level.setBlockAndUpdate(next, newHead);
            ForgeHooks.onCropsGrowPost(level, next, level.getBlockState(next));
            currentPos = next;
            currentState = newHead;
        }

        if (!currentPos.equals(pos)) {
            // Catch-up moved the head: relocate the entry and cancel vanilla so it can't grow one
            // more step and orphan the entry we just relocated.
            CropRegistry.remove(level, pos);
            CropRegistry.put(level, currentPos, cropState);
            ci.cancel();
        } else {
            CropRegistry.put(level, pos, cropState);
        }
    }

    /**
     * The shared HEAD-inject body for spawn-above column crops (sugar cane / cactus): catch up a column
     * that grows by cycling AGE 0&ndash;15 and, on wrap, spawning a new top block, up to {@code maxColumnHeight}.
     * Assumes the caller has already applied its config guard. The per-block growth action is supplied by
     * {@code step}; this owns the empty-state gating, timing, the "above empty"/height checks and relocation.
     *
     * <p>Unlike the head-walking vines, these do not cancel the vanilla tick (their HEAD inject is
     * non-cancellable) — vanilla's own AGE/height checks gate any same-tick double-grow, and the
     * {@code setBlock} INVOKE injects keep the growth timestamp in sync.
     */
    public static void spawnColumnCatchUp(ServerLevel level, BlockPos pos, BlockState state,
                                          int avgGrowthInterval, int maxColumnHeight, SpawnStep step) {
        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, avgGrowthInterval, false);
        if (steps <= 0) {
            CropRegistry.put(level, pos, cropState);
            return;
        }

        Block block = state.getBlock();
        BlockPos currentPos = pos;
        BlockState currentState = state;
        int limit = Math.min(steps, STEP_LIMIT);
        for (int i = 0; i < limit; i++) {
            BlockPos above = currentPos.above();
            if (!level.isEmptyBlock(above)) break;
            int colHeight = 1;
            while (level.getBlockState(currentPos.below(colHeight)).is(block)) {
                colHeight++;
            }
            if (colHeight >= maxColumnHeight) break;
            BlockPos result = step.grow(level, currentPos, currentState, above);
            if (result == null) break;
            currentPos = result;
            currentState = level.getBlockState(currentPos);
        }

        if (!currentPos.equals(pos)) {
            CropRegistry.remove(level, pos);
        }
        CropRegistry.put(level, currentPos, cropState);
    }

    /**
     * Catch up a bamboo stalk, which grows by placing a new bamboo block above (via the vanilla
     * {@code growBamboo} invoker) up to {@code maxHeight}. Bamboo-specific gating — STAGE must be 0,
     * sky-only light &gt;= 9 at the block above, and the height cap — is applied per step. Unlike the
     * cane/cactus path it has no {@code setBlock} sync inject and never cancels the vanilla tick
     * (vanilla's own STAGE/height checks gate any same-tick double-grow).
     *
     * @param bamboo the stalk block's {@code growBamboo}/{@code getHeightBelowUpToMax} invoker
     */
    public static void bambooCatchUp(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                     int avgGrowthInterval, int maxHeight, IBambooStalkBlockMixin bamboo) {
        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Always register on first tick: the bamboo item isn't a BlockItem, so no place event fires
            // (see BambooSaplingBlockMixin) — the first-tick fallback is the only registration path.
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, avgGrowthInterval, false);
        if (steps <= 0) {
            CropRegistry.put(level, pos, cropState);
            return;
        }

        Block block = state.getBlock();
        BlockPos currentPos = pos;
        BlockState currentState = state;
        int limit = Math.min(steps, maxHeight + 2); // a couple of extra tries past the cap
        for (int i = 0; i < limit; i++) {
            // Bamboo only grows when STAGE == 0 (STAGE 1 never passes isRandomlyTicking).
            if (currentState.getValue(BambooStalkBlock.STAGE) != 0) break;
            // Sky-only light at the block above — bamboo needs sky exposure, not torchlight.
            if (level.getRawBrightness(currentPos.above(), 0) < 9) break;
            if (!level.isEmptyBlock(currentPos.above())) break;
            int height = bamboo.invokeGetHeightBelowUpToMax(level, currentPos) + 1;
            if (height >= maxHeight) break;
            // Bypass vanilla's random 1/3 gate for catch-up; still honour grow vetoes.
            if (!ForgeHooks.onCropsGrowPre(level, currentPos, currentState, true)) break;
            bamboo.invokeGrowBamboo(currentState, level, currentPos, random, height);
            ForgeHooks.onCropsGrowPost(level, currentPos, currentState);
            BlockPos above = currentPos.above();
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.is(block)) break; // growBamboo failed silently
            currentPos = above;
            currentState = aboveState;
        }

        if (!currentPos.equals(pos)) {
            CropRegistry.remove(level, pos);
        }
        CropRegistry.put(level, currentPos, cropState);
    }

    /**
     * Shared body for the {@code setBlock} INVOKE injects: stamp the growth clock to now when vanilla
     * itself grows a tracked column block (kept identical across sugar cane / cactus).
     */
    public static void syncGrowthTimestamp(ServerLevel level, BlockPos pos) {
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        }
    }

    /**
     * The shared TAIL-inject body: after vanilla grows the head one block (the common online case),
     * {@code pos} is now a body block. Relocate the tracking entry to the new head so it follows the
     * plant instead of being orphaned; remove it if the head is gone entirely.
     */
    public static void headRelocate(ServerLevel level, BlockPos pos, Block headBlock, Direction growthDir) {
        Optional<CropState> entry = CropRegistry.get(level, pos);
        if (entry.isEmpty()) return;
        if (level.getBlockState(pos).is(headBlock)) return; // head still here
        CropRegistry.remove(level, pos);
        BlockPos newHead = pos.relative(growthDir);
        if (level.getBlockState(newHead).is(headBlock)) {
            CropRegistry.put(level, newHead, entry.get());
        }
    }
}
