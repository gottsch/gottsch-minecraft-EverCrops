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

import mod.gottsch.forge.evercrops.core.catchup.ColumnCatchUp;
import mod.gottsch.forge.evercrops.core.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up growth for cactus. Vanilla growth: AGE 0-15, spawns a new cactus above when AGE wraps, max
 * column height 3, no light requirement. Mirrors sugar cane but also calls {@code neighborChanged}
 * after each spawn. The column-walk skeleton is shared in {@link ColumnCatchUp}.
 *
 * @author Mark Gottschling on 4/27/2026
 */
@Mixin(CactusBlock.class)
public abstract class CactusBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 1400;

    public CactusBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.columnCropsEnabled.get()) return;

        ColumnCatchUp.spawnColumnCatchUp(level, pos, state, AVG_GROWTH_TICK_INTERVAL, 3,
                (lvl, currentPos, currentState, above) -> {
                    int age = currentState.getValue(CactusBlock.AGE);
                    if (!net.neoforged.neoforge.common.CommonHooks.canCropGrow(lvl, above, currentState, true)) return null;
                    if (age == 15) {
                        BlockState newTopState = defaultBlockState();
                        lvl.setBlockAndUpdate(above, newTopState);
                        BlockState resetState = currentState.setValue(CactusBlock.AGE, 0);
                        lvl.setBlock(currentPos, resetState, 4);
                        // vanilla calls neighborChanged after spawning a cactus above
                        lvl.neighborChanged(resetState, above, (Block) (Object) this, currentPos, false);
                        net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(lvl, currentPos, currentState);
                        return above;
                    } else {
                        BlockState oldState = currentState;
                        BlockState newState = currentState.setValue(CactusBlock.AGE, age + 1);
                        lvl.setBlock(currentPos, newState, 4);
                        net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(lvl, currentPos, oldState);
                        return currentPos;
                    }
                });
    }

    // Sync lastGrowthGameTime when vanilla grows (AGE == 15 spawn branch).
    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 0))
    public void everCrops_randomTick_setBlock_0(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        ColumnCatchUp.syncGrowthTimestamp(level, pos);
    }

    // Sync lastGrowthGameTime when vanilla grows (AGE bump branch).
    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 1))
    public void everCrops_randomTick_setBlock_1(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        ColumnCatchUp.syncGrowthTimestamp(level, pos);
    }
}
