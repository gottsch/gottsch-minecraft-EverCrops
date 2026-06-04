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

import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for cocoa pods. Vanilla growth: ages 0-2, no light
 * requirement, gated by random.nextInt(5) == 0.
 *
 * @author Mark Gottschling on 4/26/2026
 */
@Mixin(CocoaBlock.class)
public abstract class CocoaBlockMixin extends HorizontalDirectionalBlock implements BonemealableBlock {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    protected CocoaBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!Config.SERVER.bushCropsEnabled.get()) return;
        if (!state.hasProperty(CocoaBlock.AGE)) {
            return;
        }
        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        boolean grewAny = false;
        if (steps > 0) {
            BlockState currentState = state;
            for (int i = 0; i < steps; i++) {
                int age = currentState.getValue(CocoaBlock.AGE);
                if (age < 2 && net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, pos, currentState, true)) {
                    currentState = currentState.setValue(CocoaBlock.AGE, age + 1);
                    level.setBlock(pos, currentState, 2);
                    net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, pos, currentState);
                    grewAny = true;
                }
            }
        }
        CropRegistry.put(level, pos, cropState);
        // Catch-up already advanced this cocoa this tick. Skip vanilla's own randomTick
        // growth so it can't overwrite the caught-up age, nor double-write the growth
        // timestamp via the setBlock inject below.
        if (grewAny) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!Config.SERVER.bushCropsEnabled.get()) return;
        if (!state.hasProperty(CocoaBlock.AGE)) {
            return;
        }
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
