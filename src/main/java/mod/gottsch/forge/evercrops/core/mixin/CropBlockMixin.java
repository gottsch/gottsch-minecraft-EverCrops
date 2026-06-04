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

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
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

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource, CallbackInfo ci) {
        if (!Config.SERVER.cropsEnabled.get()) return;
        // getAgeProperty() is protected in CropBlock so we cannot call it here.
        // Instead check by property name: any CropBlock subclass without an "age"
        // property (modded decorative stubs) is skipped. BeetrootBlock is handled
        // correctly because its property is also named "age" (range 0-3).
        // getAge() / getMaxAge() / getStateForAge() dispatch virtually at runtime,
        // so beetroot growth steps are computed correctly once past this guard.
        if (state.getProperties().stream().noneMatch(p -> p.getName().equals("age"))) {
            return;
        }

        Optional<CropState> cropStateOptional = CropRegistry.get(level, pos);
        if (cropStateOptional.isPresent()) {
            CropState cropState = cropStateOptional.get();
            long delta = level.getGameTime() - cropState.getLastCallGameTime();
            EverCrops.LOGGER.debug("call delta -> {}", delta);
            if (delta > AVG_CALL_TICK_INTERVAL * 2) {
                EverCrops.LOGGER.debug("greater than 2*call...");
                long growthDelta = level.getGameTime() - cropState.getLastGrowthGameTime();
                EverCrops.LOGGER.debug("growth delta -> {}", growthDelta);
                if (growthDelta > AVG_GROWTH_TICK_INTERVAL * 2) {
                    EverCrops.LOGGER.debug("greater than 2*growth...");
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
                        boolean grewAny = false;
                        for (int i = 0; i < quotient; i++) {
                            int age = ((CropBlock) (Object) this).getAge(currentState);
                            // Fully grown: remaining steps are no-ops, stop here.
                            if (age >= ((CropBlock) (Object) this).getMaxAge()) {
                                break;
                            }
                            // Another mod (land claim/protection, etc.) vetoed growth.
                            // Stop rather than spinning the remaining steps firing events.
                            if (!net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, pos, currentState, true)) {
                                break;
                            }
                            currentState = ((CropBlock) (Object) this).getStateForAge(age + 1);
                            level.setBlock(pos, currentState, 3);
                            net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, pos, currentState);
                            grewAny = true;
                        }
                        cropState.setLastGrowthGameTime(level.getGameTime() - remainder);
                        cropState.setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
                        cropState.setLastCallGameTime(level.getGameTime());
                        cropState.setLastCallLightLevel(level.getRawBrightness(pos, 0));
                        // Catch-up already advanced this crop this tick. Skip vanilla's own
                        // randomTick growth so it can't overwrite the caught-up age with a
                        // lower one (computed from the pre-catch-up state), nor double-write
                        // the growth timestamp via the setBlock inject below.
                        if (grewAny) {
                            ci.cancel();
                        }
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
        if (!Config.SERVER.cropsEnabled.get()) return;
        if (state.getProperties().stream().noneMatch(p -> p.getName().equals("age"))) {
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
