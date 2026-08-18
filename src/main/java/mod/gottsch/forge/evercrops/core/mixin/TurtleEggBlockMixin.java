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

import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.catchup.CatchUpEngine;
import mod.gottsch.forge.evercrops.core.catchup.TurtleEggStrategy;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up hatching for turtle eggs. Vanilla ladder: {@code hatch} 0-2, then a final step that
 * removes the cluster and releases one baby turtle per egg. Requires sand below; no light gate —
 * the real vanilla clock is time of day (a short window near dawn), which the flat
 * {@code turtleEggHatchIntervalTicks} approximates.
 *
 * <p>Unlike the plant mixins this one owns the registry entry's whole lifecycle, because the growth
 * action can <i>delete</i> the block: after a catch-up pass the entry is re-persisted only if a
 * cluster is still standing there, and a second injector drops the entry when <b>vanilla</b> hatches
 * a cluster during loaded play (a vanilla hatch fires neither {@code BreakEvent} nor
 * {@code updateOrDestroy}, so nothing else would reap it).
 *
 * @author Mark Gottschling
 */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggBlockMixin extends Block {

    public TurtleEggBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Wild (worldgen) beach nests are tracked only when the player opts in — coastlines can
            // carry a lot of them. Placed clusters are registered by the place handlers regardless.
            if (EverCropsApi.config().trackWildTurtleEggs()) {
                CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            }
            return;
        }
        CropState cropState = existing.get();
        // HATCH only ever climbs, so this never actually trips — kept for uniformity with the other
        // mixins, and it keeps lastAge recorded for /evercrops inspect.
        if (CropCatchUp.handleInPlaceHarvest(level, pos, cropState, state)) {
            CropRegistry.put(level, pos, cropState);
            return;
        }

        boolean grew = CatchUpEngine.run(level, pos, state, cropState,
                EverCropsApi.config().turtleEggHatchIntervalTicks(), false, randomSource,
                TurtleEggStrategy.INSTANCE);

        // Catch-up may have hatched the cluster out of existence; re-persisting then would resurrect
        // a stale entry, so check what is actually standing here now.
        if (level.getBlockState(pos).getBlock() instanceof TurtleEggBlock) {
            CropRegistry.put(level, pos, cropState);
        } else {
            CropRegistry.remove(level, pos);
        }

        if (grew) {
            ci.cancel();
        }
    }

    /**
     * Clock stamp for vanilla's own cracking. {@code setBlock} appears exactly once in
     * {@code randomTick} — the {@code HATCH < 2} branch — so this keeps {@code lastGrowthGameTime}
     * fresh during loaded play, which is what stops normal progress being counted twice as catch-up.
     */
    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos,
                                              RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else if (EverCropsApi.config().trackWildTurtleEggs()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        }
    }

    /**
     * Vanilla hatched the cluster while loaded — the block is about to vanish, so drop its entry.
     * {@code removeBlock} appears only in the {@code HATCH == 2} branch of {@code randomTick}.
     */
    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    public void everCrops_randomTick_removeBlock(BlockState state, ServerLevel level, BlockPos pos,
                                                 RandomSource randomSource, CallbackInfo ci) {
        CropRegistry.remove(level, pos);
    }
}
