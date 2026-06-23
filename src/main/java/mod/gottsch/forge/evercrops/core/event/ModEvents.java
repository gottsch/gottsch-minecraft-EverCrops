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
package mod.gottsch.forge.evercrops.core.event;

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.persistence.CropBlockPredicates;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Game-bus event listeners for EverCrops.
 * Registers a CropState on placement and removes it on break for every block
 * class that has a randomTick mixin in this mod.
 *
 * @author Mark Gottschling on 2026-05-10
 */
@Mod.EventBusSubscriber(modid = EverCrops.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    /**
     * Explicitly register this mod's crop predicate with the shared cleanup set.
     * Called from {@link mod.gottsch.forge.evercrops.core.EverCrops} constructor so
     * registration is guaranteed before any game events fire.
     */
    public static void registerPredicates() {
        CropBlockPredicates.register(ModEvents::isCropBlock);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getPlacedBlock();
        if (!isTracked(state)) return;
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        CropRegistry.put(serverLevel, pos, CropCatchUp.createState(serverLevel, pos));
    }

    /**
     * Remove (or relocate) the CropState entry when a player breaks a tracked crop block.
     *
     * <p><b>HEAD breaks:</b> the body block adjacent to the root side will convert to a
     * new head via vanilla {@code updateShape}. The existing CropState is moved there so
     * the vine stays tracked with its full offline history intact.
     *
     * <p><b>BODY breaks:</b> vanilla cascades the head off but {@code BreakEvent} does not
     * fire for cascade-broken blocks. We scan for the now-stale head entry and pre-register
     * the body block on the root side of the break (which will convert to the new head) so
     * it is already tracked when its first {@code randomTick} fires.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        Block block = state.getBlock();

        if (isTracked(state)) {
            // HEAD broken directly. Move the CropState to the body block that will become
            // the new head via updateShape, preserving the vine's offline history.
            Optional<CropState> existing = CropRegistry.get(serverLevel, pos);
            CropRegistry.remove(serverLevel, pos);
            if (existing.isPresent()) {
                BlockPos newHeadPos = vineRootSidePos(block, pos);
                if (newHeadPos != null && isVineBodyBlock(serverLevel.getBlockState(newHeadPos))) {
                    CropRegistry.put(serverLevel, newHeadPos, existing.get());
                }
            }
            return;
        }

        // BODY broken — cascade removes the head (no BreakEvent for cascade-broken blocks).
        // Relocate the stale head entry to the new head pos on the root side of the break.
        if (Config.SERVER.columnCropsEnabled.get() && block instanceof KelpPlantBlock) {
            relocateVineEntry(serverLevel, pos, Direction.UP, pos.below());
        } else if (Config.SERVER.twistingVinesEnabled.get() && block instanceof TwistingVinesPlantBlock) {
            relocateVineEntry(serverLevel, pos, Direction.UP, pos.below());
        } else if (Config.SERVER.weepingVinesEnabled.get() && block instanceof WeepingVinesPlantBlock) {
            relocateVineEntry(serverLevel, pos, Direction.DOWN, pos.above());
        } else if (Config.SERVER.caveVinesEnabled.get() && block instanceof CaveVinesPlantBlock) {
            relocateVineEntry(serverLevel, pos, Direction.DOWN, pos.above());
        }
    }

    /**
     * Returns the position adjacent to the root side of a vine head block (the body block
     * that will convert to a new head via updateShape when the current head is broken).
     * Returns {@code null} if the block is not an orphan-relocating vine head.
     */
    private static BlockPos vineRootSidePos(Block block, BlockPos pos) {
        // Upward-growing: head is top, root side is below
        if (block instanceof KelpBlock || block instanceof TwistingVinesBlock) return pos.below();
        // Downward-growing: head is bottom, root side is above
        if (block instanceof WeepingVinesBlock || block instanceof CaveVinesBlock) return pos.above();
        return null;
    }

    /**
     * Returns true if this block state is a vine body type that converts to a head block
     * via {@code updateShape} when its head-side neighbour is removed.
     */
    private static boolean isVineBodyBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof KelpPlantBlock
            || b instanceof TwistingVinesPlantBlock
            || b instanceof WeepingVinesPlantBlock
            || b instanceof CaveVinesPlantBlock;
    }

    /**
     * Relocates a vine's tracking entry from the (cascade-destroyed) head position to the
     * new head that will appear via {@code updateShape} after a body block is player-broken.
     *
     * <p>Scans up to 30 blocks in {@code headDir} from the broken body block to find the
     * existing CropState, then moves it to {@code newHeadPos}. If no entry is found (the
     * vine was untracked, e.g. wild), creates a fresh CropState so the vine stays tracked.
     */
    private static void relocateVineEntry(ServerLevel level, BlockPos brokenPos,
                                          Direction headDir, BlockPos newHeadPos) {
        BlockPos scanPos = brokenPos.relative(headDir);
        for (int i = 0; i < 30; i++) {
            Optional<CropState> existing = CropRegistry.get(level, scanPos);
            if (existing.isPresent()) {
                CropRegistry.remove(level, scanPos);
                CropRegistry.put(level, newHeadPos, existing.get());
                return;
            }
            Block b = level.getBlockState(scanPos).getBlock();
            if (!(b instanceof GrowingPlantBodyBlock) && !(b instanceof GrowingPlantHeadBlock)) break;
            scanPos = scanPos.relative(headDir);
        }
        // No existing entry — create a fresh one so the vine stays tracked after the harvest.
        CropRegistry.put(level, newHeadPos, CropCatchUp.createState(level, newHeadPos));
    }

    /**
     * Periodic automatic cleanup of stale registry entries.
     *
     * <p>Fires every game tick per loaded dimension, but the body is gated behind a
     * modulo check so actual work runs only every {@code autoCleanupIntervalTicks}
     * ticks (default 36 000 = 30 minutes). Only ServerLevel dimensions are scanned;
     * client-side levels and the modulo check itself are skipped cheaply.
     *
     * <p>This is the safety net for the {@link #onBlockBroken} gap: BreakEvent only
     * fires for player mining, so crops removed by pistons, explosions, fluids or
     * other mods would otherwise leave orphaned entries in the registry forever.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (!Config.SERVER.autoCleanupEnabled.get()) return;

        int interval = Config.SERVER.autoCleanupIntervalTicks.get();
        if (serverLevel.getGameTime() % interval != 0) return;

        int removed = CropRegistry.cleanup(serverLevel, CropBlockPredicates::isCropBlock);
        if (removed > 0) {
            EverCrops.LOGGER.info("Auto-cleanup removed {} stale crop entr{} in {}.",
                    removed, removed == 1 ? "y" : "ies", serverLevel.dimension().location());
        } else {
            EverCrops.LOGGER.debug("Auto-cleanup: no stale entries in {}.",
                    serverLevel.dimension().location());
        }
    }

    /**
     * Whether a block should be tracked by EverCrops, respecting the per-category
     * config toggles. Used by the place/break handlers.
     *
     * <p>Delegates to {@link CropEligibility#isTrackingEnabled(BlockState)} — capability
     * detection (random-ticking + a recognized growth property) gated by the category toggle.
     */
    public static boolean isTracked(BlockState state) {
        return CropEligibility.isTrackingEnabled(state);
    }

    /**
     * Whether a block state is a tracked crop type, independent of the per-category config
     * toggles. Used by registry cleanup so that disabling a category does not cause its
     * still-valid entries to be purged — cleanup only removes entries whose block is
     * genuinely no longer a crop.
     *
     * <p>Delegates to {@link CropEligibility#isEligible(BlockState)} (pure capability).
     */
    public static boolean isCropBlock(BlockState state) {
        return CropEligibility.isEligible(state);
    }
}
