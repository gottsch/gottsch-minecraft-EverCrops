/*
 * This file is part of EverCrops.
 * Copyright (c) 2025 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.evercrops.core.catchup.CatchUpEngine;
import mod.gottsch.forge.evercrops.core.catchup.StemBlockStrategy;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for {@link StemBlock} (melon / pumpkin stems). The growth action — advance AGE,
 * then spread one fruit at maturity — lives in {@link StemBlockStrategy}.
 *
 * @author by Mark Gottschling on 3/19/2025
 */
@Mixin(StemBlock.class)
public abstract class StemBlockMixin extends BushBlock implements BonemealableBlock, IStemBlockMixin {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public StemBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        // Harvested in place (age dropped without a break/place event). Reset the growth clock so
        // pending catch-up isn't re-applied to the replant.
        if (CropCatchUp.handleInPlaceHarvest(level, pos, cropState, state)) {
            CropRegistry.put(level, pos, cropState);
            return;
        }

        boolean grew = CatchUpEngine.run(level, pos, state, cropState,
                AVG_GROWTH_TICK_INTERVAL, true, randomSource, StemBlockStrategy.INSTANCE);
        CropRegistry.put(level, pos, cropState);
        // Catch-up already advanced this stem this tick. Skip vanilla's own randomTick growth so it
        // can't overwrite the caught-up age, spread a second fruit, nor double-write the timestamp.
        if (grew) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        }
    }
}
