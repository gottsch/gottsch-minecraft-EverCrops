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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for kelp. Targets GrowingPlantHeadBlock (kelp's randomTick
 * is inherited from there, not overridden in KelpBlock). Guards with instanceof
 * to avoid affecting other GrowingPlantHeadBlock subclasses (vines, etc.).
 *
 * Vanilla growth: AGE 0-25, each tick places a new KelpBlock above (cycling
 * AGE by 1) and converts the old head to KelpPlantBlock via updateShape.
 * Growth stops when AGE reaches 25. No light requirement (underwater).
 * Gate: random.nextDouble() < 0.14 per tick — AVG_GROWTH_TICK_INTERVAL ~9800.
 *
 * @author Mark Gottschling on 4/27/2026
 */
@Mixin(GrowingPlantHeadBlock.class)
public abstract class KelpBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 9800;

    public KelpBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!(((Object)this) instanceof KelpBlock)) return;
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        if (!state.hasProperty(GrowingPlantHeadBlock.AGE)) return;

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
            // max meaningful steps bounded by remaining age (25 - current age); cap at 50
            int limit = Math.min(steps, 50);
            for (int i = 0; i < limit; i++) {
                int age = currentState.getValue(GrowingPlantHeadBlock.AGE);
                if (age >= 25) break; // kelp stops growing at max age
                BlockPos above = currentPos.above();
                // kelp grows only into water source blocks
                if (!level.getBlockState(above).is(Blocks.WATER)) break;
                if (!CommonHooks.canCropGrow(level, above, currentState, true)) break;
                // cycle(AGE) increments age by 1 (0→1, 24→25, 25→0 — but we break at 25)
                BlockState newHead = currentState.cycle(GrowingPlantHeadBlock.AGE);
                level.setBlockAndUpdate(above, newHead);
                // updateShape on currentPos converts it from KelpBlock to KelpPlantBlock
                CommonHooks.fireCropGrowPost(level, above, level.getBlockState(above));
                currentPos = above;
                currentState = newHead;
            }
            if (!currentPos.equals(pos)) {
                CropRegistry.remove(level, pos);
            }
            CropRegistry.put(level, currentPos, cropState);
        } else {
            CropRegistry.put(level, pos, cropState);
        }
    }

    // Sync lastGrowthGameTime when vanilla kelp grows normally.
    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    public void everCrops_randomTick_setBlockAndUpdate(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!(((Object)this) instanceof KelpBlock)) return;
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        if (!state.hasProperty(GrowingPlantHeadBlock.AGE)) return;
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
