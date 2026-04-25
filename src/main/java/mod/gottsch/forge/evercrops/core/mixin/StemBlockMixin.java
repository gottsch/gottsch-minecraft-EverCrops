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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Almost identical to CropBlockMixin, but targets StemBlock which extends
 * BushBlock rather than CropBlock, and handles fruit spreading at age 7.
 *
 * @author by Mark Gottschling on 3/19/2025
 */
@Mixin(StemBlock.class)
public abstract class StemBlockMixin extends BushBlock implements BonemealableBlock, IStemBlockMixin {

    @Unique
    private static final int AVG_CALL_TICK_INTERVAL = 1350;
    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public StemBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        // Guard against mods that extend StemBlock but use block states that don't have
        // the AGE property (e.g. decorative stem-like blocks).
        if (!state.hasProperty(StemBlock.AGE)) {
            return;
        }

        Optional<CropState> cropStateOptional = CropRegistry.get(level, pos);
        if (cropStateOptional.isPresent()) {
            CropState cropState = cropStateOptional.get();
            long delta = level.getGameTime() - cropState.getLastCallGameTime();
            if (delta > AVG_CALL_TICK_INTERVAL * 2) {
                long growthDelta = level.getGameTime() - cropState.getLastGrowthGameTime();
                if (growthDelta > AVG_GROWTH_TICK_INTERVAL * 2) {
                    boolean grow = false;

                    if (level.getRawBrightness(pos, 0) >= 9) {
                        grow = true;
                    } else if (!level.isDay()) {
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
                            if (net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, pos, currentState, true)) {
                                int age = currentState.getValue(StemBlock.AGE);
                                if (age < 7) {
                                    currentState = currentState.setValue(StemBlock.AGE, age + 1);
                                    level.setBlock(pos, currentState, 3);
                                } else {
                                    IStemBlockMixin stemBlock = (IStemBlockMixin) (Object) this;
                                    Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(randomSource);
                                    BlockPos blockpos = pos.relative(direction);
                                    BlockState blockstate = level.getBlockState(blockpos.below());
                                    if (level.isEmptyBlock(blockpos) && (blockstate.canSustainPlant(level, blockpos.below(), Direction.UP, stemBlock.getFruit()) || blockstate.is(Blocks.FARMLAND) || blockstate.is(BlockTags.DIRT))) {
                                        level.setBlockAndUpdate(blockpos, stemBlock.getFruit().defaultBlockState());
                                        level.setBlockAndUpdate(pos, stemBlock.getFruit().getAttachedStem().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction));
                                    }
                                }
                                net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, pos, currentState);
                            }
                        }
                        cropState.setLastGrowthGameTime(level.getGameTime() - remainder);
                        cropState.setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
                        cropState.setLastCallGameTime(level.getGameTime());
                        cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));
                    } else {
                        cropState.setLastCallGameTime(level.getGameTime());
                        cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));
                    }
                }
            } else {
                cropState.setLastCallGameTime(level.getGameTime());
                cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));
            }
            CropRegistry.put(level, pos, cropState);
        } else {
            CropRegistry.put(level, pos, everCrops$createCropState(level, pos));
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!state.hasProperty(StemBlock.AGE)) {
            return;
        }
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else {
            CropRegistry.put(level, pos, everCrops$createCropState(level, pos));
        }
    }

    @Unique
    private CropState everCrops$createCropState(ServerLevel level, BlockPos pos) {
        CropState cropState = new CropState();
        cropState.setLastCallGameTime(level.getGameTime())
                .setLastGrowthGameTime(level.getGameTime())
                .setLastCallLightLevel(level.getRawBrightness(pos, 0))
                .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
        return cropState;
    }
}
