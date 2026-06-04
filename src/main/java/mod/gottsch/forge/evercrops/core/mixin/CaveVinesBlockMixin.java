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
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for cave vines. Targets GrowingPlantHeadBlock because
 * CaveVinesBlock does not override randomTick — it inherits from the parent.
 * Guards with instanceof to avoid affecting kelp and nether vines.
 *
 * Grows downward (Direction.DOWN). Gate probability 0.1 → AVG_GROWTH_TICK_INTERVAL ~13500.
 * No light requirement. Stops at AGE 25. canGrowInto requires air below.
 * BERRIES property is ignored for natural growth (only affects bonemeal).
 *
 * @author Mark Gottschling on 5/6/2026
 */
@Mixin(GrowingPlantHeadBlock.class)
public abstract class CaveVinesBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 13500;

    public CaveVinesBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick_cave(BlockState state, ServerLevel level, BlockPos pos,
                                          RandomSource random, CallbackInfo ci) {
        if (!(((Object)this) instanceof CaveVinesBlock)) return;
        if (!Config.SERVER.caveVinesEnabled.get()) return;
        if (!state.hasProperty(GrowingPlantHeadBlock.AGE)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Wild (worldgen) heads are tracked only when the player opts in. Placed ones are
            // registered via the place event, and tracking follows the head via TAIL relocation.
            if (Config.SERVER.trackWildVines.get()) {
                CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            }
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        if (steps > 0) {
            BlockPos currentPos = pos;
            BlockState currentState = state;
            int limit = Math.min(steps, 50);
            for (int i = 0; i < limit; i++) {
                int age = currentState.getValue(GrowingPlantHeadBlock.AGE);
                if (age >= 25) break;
                BlockPos next = currentPos.below(); // cave vines grow DOWN
                if (!level.isEmptyBlock(next)) break; // canGrowInto checks air only
                if (!net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, next, currentState, true)) break;
                BlockState newHead = currentState.cycle(GrowingPlantHeadBlock.AGE);
                level.setBlockAndUpdate(next, newHead);
                net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, next, level.getBlockState(next));
                currentPos = next;
                currentState = newHead;
            }
            if (!currentPos.equals(pos)) {
                // Catch-up moved the head: relocate the entry and cancel vanilla so it can't
                // grow one more step and orphan the entry we just relocated.
                CropRegistry.remove(level, pos);
                CropRegistry.put(level, currentPos, cropState);
                ci.cancel();
            } else {
                CropRegistry.put(level, pos, cropState);
            }
        } else {
            CropRegistry.put(level, pos, cropState);
        }
    }

    /**
     * After vanilla grows the head one block down (the common online case), {@code pos} is now a
     * body block. Relocate the tracking entry to the new head so it follows the plant instead of
     * being orphaned. Removes the entry if the head is gone entirely.
     */
    @Inject(method = "randomTick", at = @At("TAIL"))
    public void everCrops_randomTick_cave_relocate(BlockState state, ServerLevel level, BlockPos pos,
                                                   RandomSource random, CallbackInfo ci) {
        if (!(((Object)this) instanceof CaveVinesBlock)) return;
        if (!Config.SERVER.caveVinesEnabled.get()) return;
        Optional<CropState> entry = CropRegistry.get(level, pos);
        if (entry.isEmpty()) return;
        if (level.getBlockState(pos).is((Block)(Object) this)) return; // head still here
        CropRegistry.remove(level, pos);
        BlockPos newHead = pos.below(); // cave vines grow down
        if (level.getBlockState(newHead).is((Block)(Object) this)) {
            CropRegistry.put(level, newHead, entry.get());
        }
    }
}
