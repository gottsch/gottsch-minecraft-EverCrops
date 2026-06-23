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
package mod.gottsch.forge.evercrops.core.mixin;

import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for bamboo saplings (BambooSaplingBlock).
 *
 * <p>BambooItem is not a BlockItem, so EntityPlaceEvent never fires on player placement.
 * The first-tick path is therefore the only registration route for player-placed bamboo.
 *
 * <p>When the chunk has been offline long enough, we call performBonemeal (one growBamboo
 * call: places a BambooStalkBlock at pos.above(); the sapling at pos self-converts via
 * updateShape). The CropState timestamps are left unchanged so the stalk at pos inherits
 * the full offline delta and applies multi-step column catch-up on its own first randomTick.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(BambooSaplingBlock.class)
public abstract class BambooSaplingBlockMixin extends Block implements BonemealableBlock {

    /** Mirrors the 1/3 random gate in BambooSaplingBlock.randomTick (same as stalk). */
    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 4500;

    public BambooSaplingBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Always register on first tick (see class javadoc for rationale).
            // No cancel — let vanilla attempt natural growth on this same tick.
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }

        CropState cropState = existing.get();
        long now         = level.getGameTime();
        long callDelta   = now - cropState.getLastCallGameTime();
        long growthDelta = now - cropState.getLastGrowthGameTime();

        if (callDelta > 2 * 1350L && growthDelta > 2L * AVG_GROWTH_TICK_INTERVAL) {
            // Chunk was offline long enough — attempt one sapling→stalk growth step.
            if (level.isEmptyBlock(pos.above()) && level.getRawBrightness(pos.above(), 0) >= 9) {
                BambooSaplingBlock self = (BambooSaplingBlock)(Object) this;
                self.performBonemeal(level, random, pos, state);
                // Timestamps intentionally left unchanged. The BambooStalkBlock now at pos
                // inherits this CropState's stale lastGrowthGameTime and will trigger
                // multi-step column catch-up via BambooStalkBlockMixin on its first tick.
                CropRegistry.put(level, pos, cropState);
                ci.cancel();
            } else {
                // Growth blocked (ceiling above or low light) — persist and let vanilla run.
                CropRegistry.put(level, pos, cropState);
            }
        } else {
            // Normal loaded-chunk tick: refresh call timestamp for future delta calibration.
            cropState.setLastCallGameTime(now)
                     .setLastCallLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState);
            // No cancel — vanilla attempts natural growth.
        }
    }
}
