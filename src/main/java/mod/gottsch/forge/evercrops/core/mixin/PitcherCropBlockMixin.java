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
import mod.gottsch.forge.evercrops.core.catchup.CatchUpEngine;
import mod.gottsch.forge.evercrops.core.catchup.PitcherCropStrategy;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for the pitcher plant. {@code PitcherCropBlock} extends {@link DoublePlantBlock},
 * not {@code CropBlock}, so {@code CropBlockMixin} never reached it — a pitcher crop was tracked but
 * never grown. This closes that gap; the growth action lives in {@code PitcherCropStrategy}.
 *
 * <p>The pace matches {@code CropBlockMixin} exactly ({@code AVG_GROWTH_TICK_INTERVAL} 7000, light
 * required) because {@code randomTick} uses the identical {@code nextInt((int)(25.0F / f) + 1)}
 * formula as {@code CropBlock} and vanilla's growth is gated on sufficient light.
 *
 * <p>Only the lower half is ever seen here — {@code isRandomlyTicking} is false for the upper half
 * and for a fully-grown plant — so the tracking entry always sits on the lower half.
 *
 * @author Mark Gottschling
 */
@Mixin(net.minecraft.world.level.block.PitcherCropBlock.class)
public abstract class PitcherCropBlockMixin extends DoublePlantBlock implements BonemealableBlock {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public PitcherCropBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        if (CropCatchUp.handleInPlaceHarvest(level, pos, cropState, state)) {
            CropRegistry.put(level, pos, cropState);
            return;
        }

        boolean grew = CatchUpEngine.run(level, pos, state, cropState,
                AVG_GROWTH_TICK_INTERVAL, true, randomSource, PitcherCropStrategy.INSTANCE);

        // A plant that reached max age stops random-ticking, so its entry can never be revisited —
        // drop it rather than leaving the auto-cleanup scan to find it later.
        if (grew && !CropEligibility.isEligible(level.getBlockState(pos))) {
            CropRegistry.remove(level, pos);
        } else {
            CropRegistry.put(level, pos, cropState);
        }

        if (grew) {
            ci.cancel();
        }
    }

    /**
     * Clock stamp for vanilla's own growth. Unlike the other crop mixins there is no {@code setBlock}
     * to hook in {@code randomTick} — the writes live inside the private {@code grow} — so the invoke
     * of {@code grow} itself is the marker. It is called exactly once, and only after the probability
     * gate passes, which is precisely the event {@code AVG_GROWTH_TICK_INTERVAL} models.
     */
    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/PitcherCropBlock;grow(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;I)V"))
    public void everCrops_randomTick_grow(BlockState state, ServerLevel level, BlockPos pos,
                                          RandomSource randomSource, CallbackInfo ci) {
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
