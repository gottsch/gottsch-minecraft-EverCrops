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
import net.minecraft.block.*;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Random;

/**
 * almost identical to CropBlockMixin, but need it since StemBlock extends
 * BushBlock and not CropBlock. also there is a slight difference in the
 * processing of randomTick().
 * NOTE StemBlock uses the same age and growth speed as CropBlock.
 * @author by Mark Gottschling on 3/19/2025
 */
@Mixin(StemBlock.class)
public abstract class StemBlockMixin extends BushBlock implements IGrowable, IStemBlockMixin {

    @Unique
    private static final int AVG_CALL_TICK_INTERVAL = 1350;
    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public StemBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!CropRegistry.isStarted()) {
            return;
        }

        // create dimensional pos
        ResourceLocation dimension = world.dimension().location();
        DimensionalBlockPos dimPos = new DimensionalBlockPos(dimension, pos);
//        EverCrops.LOGGER.debug("randomTick called at {}:{}", dimension, pos.toShortString());

        Optional<CropState> cropStateOptional = CropRegistry.get(dimPos);
        if (cropStateOptional.isPresent()) {
            // this is where the meat happens
            CropState cropState = cropStateOptional.get();
            // check the delta
            long delta = world.getGameTime() - cropState.getLastCallGameTime();
//            EverCrops.LOGGER.debug("call delta -> {}", delta);
            if (delta > AVG_CALL_TICK_INTERVAL * 2) {
//                EverCrops.LOGGER.debug("greater than 2*call...");
                // assume that the chunk was unloaded and reloaded
                long growthDelta = world.getGameTime() - cropState.getLastGrowthGameTime();
//                EverCrops.LOGGER.debug("growth delta -> {}", growthDelta);
                // if growth delta is > avg*2, then apply growth for how many times the avg goes into the delta
                if (growthDelta > AVG_GROWTH_TICK_INTERVAL * 2) {
//                    EverCrops.LOGGER.debug("greater than 2*growth...");
                    boolean grow = false;

                    if (world.getRawBrightness(pos, 0) >= 9) {
                        // there is enough light, don't need any other info
                        grow = true;
                    } else if (!world.isDay()) {
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
//                                EverCrops.LOGGER.debug("age is still good -> {}", age);
                            if (net.minecraftforge.common.ForgeHooks.onCropsGrowPre(world, pos, currentState, true)) {
                                int age = currentState.getValue(StemBlock.AGE);
                                if (age < 7) {
                                    //                                    EverCrops.LOGGER.debug("growing at -> {}", pos);
                                    currentState = currentState.setValue(StemBlock.AGE, age + 1);
//                                    EverCrops.LOGGER.debug("current state.age -> {}", currentState.getValue(CropBlock.AGE));
                                    world.setBlock(pos, currentState, 3);
                                } else {
                                    IStemBlockMixin stemBlock = (IStemBlockMixin)(Object)this;

                                    Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                                    BlockPos blockpos = pos.relative(direction);
                                    BlockState blockstate = world.getBlockState(blockpos.below());
                                    Block block = blockstate.getBlock();
                                    if (world.isEmptyBlock(blockpos) && (blockstate.canSustainPlant(world, blockpos.below(), Direction.UP, (StemBlock)(Object)this) || block == Blocks.FARMLAND || block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL || block == Blocks.GRASS_BLOCK)) {

                                        world.setBlockAndUpdate(blockpos, stemBlock.getFruit().defaultBlockState());
                                        world.setBlockAndUpdate(pos, stemBlock.getFruit().getAttachedStem().defaultBlockState().setValue(HorizontalBlock.FACING, direction));
                                    }
                                }
                                net.minecraftforge.common.ForgeHooks.onCropsGrowPost(world, pos, currentState);
                            }
                        }
                        // update all properties
                        cropState.setLastGrowthGameTime(world.getGameTime() - remainder);
                        cropState.setLastGrowthLightLevel(world.getRawBrightness(pos, 0));
                        cropState.setLastCallGameTime(world.getGameTime());
                        cropState.setLastCallLightLevel(world.getRawBrightness(pos, 0));

                    } else {
                        // update only the call properties
                        cropState.setLastCallGameTime(world.getGameTime());
                        cropState.setLastCallLightLevel(world.getRawBrightness(pos, 0));
                    }
                }
            } else {
                // update only the call properties
                cropState.setLastCallGameTime(world.getGameTime());
                cropState.setLastCallLightLevel(world.getRawBrightness(pos, 0));
            }
            CropRegistry.put(dimPos, cropState);
        } else {
            CropRegistry.put(dimPos, everCrops_1_20_1$createCropState(world, pos));
        }
//        if (EverCrops.LOGGER.isDebugEnabled()) {
//            Optional<CropState> stateCheck = CropRegistry.get(dimPos);
//            stateCheck.ifPresent(c -> EverCrops.LOGGER.debug("randomTick stateCheck -> {}", c));
//        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;setBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerWorld world, BlockPos pos, Random randomSource, CallbackInfo ci) {
        if (!CropRegistry.isStarted()) {
            return;
        }

        // create dimensional pos
        ResourceLocation dimension = world.dimension().location();
        DimensionalBlockPos dimPos = new DimensionalBlockPos(dimension, pos);
//        EverCrops.LOGGER.debug("randomTick.setBlock called at {}:{}", dimension, pos);

        Optional<CropState> cropState = CropRegistry.get(dimPos);
        if (cropState.isPresent()) {
            // update time and light level
            cropState.get().setLastGrowthGameTime(world.getGameTime())
                    .setLastGrowthLightLevel(world.getRawBrightness(pos, 0));
            CropRegistry.put(dimPos, cropState.get());
        } else {
            CropRegistry.put(dimPos, everCrops_1_20_1$createCropState(world, pos));
        }
//        if (EverCrops.LOGGER.isDebugEnabled()) {
//            Optional<CropState> stateCheck = CropRegistry.get(dimPos);
//            stateCheck.ifPresent(c -> EverCrops.LOGGER.debug("randomTick.setBlock stateCheck -> {}", c));
//        }
    }

    @Unique
    private CropState everCrops_1_20_1$createCropState(ServerWorld world, BlockPos pos) {
        CropState cropState = new CropState();
        cropState.setLastCallGameTime(world.getGameTime())
                .setLastGrowthGameTime(world.getGameTime())
                .setLastCallLightLevel(world.getRawBrightness(pos, 0))
                .setLastGrowthLightLevel(world.getRawBrightness(pos, 0));
        return cropState;
    }
}
