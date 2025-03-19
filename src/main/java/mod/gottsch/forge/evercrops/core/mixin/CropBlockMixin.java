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

import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import mod.gottsch.forge.evercrops.core.persistence.DimensionalBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * @author by Mark Gottschling on 3/13/2025
 */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin extends BushBlock implements BonemealableBlock {

    @Unique
    private static final int AVG_CALL_TICK_INTERVAL = 1350;
    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public CropBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!CropRegistry.isStarted()) {
            return;
        }

        // create dimensional pos
        ResourceLocation dimension = level.dimension().location();
        DimensionalBlockPos dimPos = new DimensionalBlockPos(dimension, pos);
//        EverCrops.LOGGER.debug("randomTick called at {}:{}", dimension, pos.toShortString());

        Optional<CropState> cropStateOptional = CropRegistry.get(dimPos);
        if (cropStateOptional.isPresent()) {
            // this is where the meat happens
            CropState cropState = cropStateOptional.get();
            // check the delta
            long delta = level.getGameTime() - cropState.getLastCallGameTime();
//            EverCrops.LOGGER.debug("call delta -> {}", delta);
            if (delta > AVG_CALL_TICK_INTERVAL * 2) {
//                EverCrops.LOGGER.debug("greater than 2*call...");
                // assume that the chunk was unloaded and reloaded
                long growthDelta = level.getGameTime() - cropState.getLastGrowthGameTime();
//                EverCrops.LOGGER.debug("growth delta -> {}", growthDelta);
                // if growth delta is > avg*2, then apply growth for how many times the avg goes into the delta
                if (growthDelta > AVG_GROWTH_TICK_INTERVAL * 2) {
//                    EverCrops.LOGGER.debug("greater than 2*growth...");
                    boolean grow = false;

                    if (level.getRawBrightness(pos, 0) >= 9) {
                        // there is enough light, don't need any other info
                        grow = true;
                    } else if (!level.isDay()) {
                        // if day and not enough light, then don't grow.
                        // however, if it is night, need to check previous light levels.
                        if (cropState.getLastCallLightLevel() >= 9) {
                            grow = true;
                        } else if (cropState.getLastGrowthLightLevel() >= 9) {
                            grow = true;
                        }
                    }

                    if (grow) {
                        BlockState currentState = state;
                        int quotient = (int) (Math.floor((double) growthDelta / AVG_GROWTH_TICK_INTERVAL));
                        long remainder = growthDelta % AVG_GROWTH_TICK_INTERVAL;
                        for (int i = 0; i < quotient; i++) {
                            // apply growth
                            int age = ((CropBlock) (Object) this).getAge(currentState);
                            if (age < ((CropBlock) (Object) this).getMaxAge()) {
//                                EverCrops.LOGGER.debug("age is still good -> {}", age);
                                if (net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, pos, currentState, true)) {
//                                    EverCrops.LOGGER.debug("growing at -> {}", pos);
                                    currentState = ((CropBlock) (Object) this).getStateForAge(age + 1);
//                                    EverCrops.LOGGER.debug("current state.age -> {}", currentState.getValue(CropBlock.AGE));
                                    level.setBlock(pos, currentState, 3);
                                    net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, pos, currentState);
                                }
                            }
                        }
                        // update all properties
                        cropState.setLastGrowthGameTime(level.getGameTime() - remainder);
                        cropState.setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
                        cropState.setLastCallGameTime(level.getGameTime());
                        cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));

                    } else {
                        // update only the call properties
                        cropState.setLastCallGameTime(level.getGameTime());
                        cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));
                    }
                }
            } else {
                // update only the call properties
                cropState.setLastCallGameTime(level.getGameTime());
                cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));
            }
            CropRegistry.put(dimPos, cropState);
        } else {
            CropRegistry.put(dimPos, everCrops_1_20_1$createCropState(level, pos));
        }
//        if (EverCrops.LOGGER.isDebugEnabled()) {
//            Optional<CropState> stateCheck = CropRegistry.get(dimPos);
//            stateCheck.ifPresent(c -> EverCrops.LOGGER.debug("randomTick stateCheck -> {}", c));
//        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!CropRegistry.isStarted()) {
            return;
        }

        // create dimensional pos
        ResourceLocation dimension = level.dimension().location();
        DimensionalBlockPos dimPos = new DimensionalBlockPos(dimension, pos);
//        EverCrops.LOGGER.debug("randomTick.setBlock called at {}:{}", dimension, pos);

        Optional<CropState> cropState = CropRegistry.get(dimPos);
        if (cropState.isPresent()) {
            // update time and light level
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(dimPos, cropState.get());
        } else {
            CropRegistry.put(dimPos, everCrops_1_20_1$createCropState(level, pos));
        }
//        if (EverCrops.LOGGER.isDebugEnabled()) {
//            Optional<CropState> stateCheck = CropRegistry.get(dimPos);
//            stateCheck.ifPresent(c -> EverCrops.LOGGER.debug("randomTick.setBlock stateCheck -> {}", c));
//        }
    }

    @Unique
    private CropState everCrops_1_20_1$createCropState(ServerLevel level, BlockPos pos) {
        CropState cropState = new CropState();
        cropState.setLastCallGameTime(level.getGameTime())
                .setLastGrowthGameTime(level.getGameTime())
                .setLastCallLightLevel(level.getRawBrightness(pos, 0))
                .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
        return cropState;
    }
}
