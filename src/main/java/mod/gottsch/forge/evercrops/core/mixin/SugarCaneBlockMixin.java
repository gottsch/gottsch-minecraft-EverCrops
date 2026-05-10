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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for sugar cane. Vanilla growth: AGE 0-15, spawns new cane
 * above when AGE wraps, max column height 3, no light requirement.
 * Only the top block (air above) grows; catch-up walks the column upward.
 *
 * @author Mark Gottschling on 4/27/2026
 */
@Mixin(SugarCaneBlock.class)
public abstract class SugarCaneBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 1400;

    public SugarCaneBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        if (steps > 0) {
            BlockPos currentPos = pos;
            BlockState currentState = state;
            // max meaningful steps: 2 spawns * 16 age steps = 32; cap at 50 for safety
            int limit = Math.min(steps, 50);
            for (int i = 0; i < limit; i++) {
                if (!level.isEmptyBlock(currentPos.above())) break;
                // count column height (current block + cane blocks below)
                int colHeight = 1;
                while (level.getBlockState(currentPos.below(colHeight)).is((Block)(Object)this)) {
                    colHeight++;
                }
                if (colHeight >= 3) break;
                int age = currentState.getValue(SugarCaneBlock.AGE);
                if (!net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, currentPos, currentState, true)) break;
                BlockPos above = currentPos.above();
                if (age == 15) {
                    BlockState newTopState = defaultBlockState();
                    level.setBlockAndUpdate(above, newTopState);
                    net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, above, newTopState);
                    level.setBlock(currentPos, currentState.setValue(SugarCaneBlock.AGE, 0), 4);
                    currentPos = above;
                    currentState = newTopState;
                } else {
                    BlockState newState = currentState.setValue(SugarCaneBlock.AGE, age + 1);
                    level.setBlock(currentPos, newState, 4);
                    currentState = newState;
                }
            }
            if (!currentPos.equals(pos)) {
                CropRegistry.remove(level, pos);
            }
            CropRegistry.put(level, currentPos, cropState);
        } else {
            CropRegistry.put(level, pos, cropState);
        }
    }

    // Sync lastGrowthGameTime when vanilla grows (AGE == 15 spawn branch).
    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 0))
    public void everCrops_randomTick_setBlock_0(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        everCrops$syncGrowthTimestamp(level, pos);
    }

    // Sync lastGrowthGameTime when vanilla grows (AGE bump branch).
    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 1))
    public void everCrops_randomTick_setBlock_1(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        everCrops$syncGrowthTimestamp(level, pos);
    }

    @Unique
    private void everCrops$syncGrowthTimestamp(ServerLevel level, BlockPos pos) {
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
