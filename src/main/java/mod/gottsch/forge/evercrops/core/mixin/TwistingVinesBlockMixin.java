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
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.NetherVines;
import net.minecraft.world.level.block.TwistingVinesBlock;
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
 * Catch-up growth for twisting vines. Targets GrowingPlantHeadBlock because
 * TwistingVinesBlock does not override randomTick — it inherits from the parent.
 * Guards with instanceof to avoid affecting kelp and weeping vines.
 *
 * Grows upward (Direction.UP). Gate probability 0.1 → AVG_GROWTH_TICK_INTERVAL ~13500.
 * No light requirement (Nether plant). Stops at AGE 25.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(GrowingPlantHeadBlock.class)
public abstract class TwistingVinesBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 13500;

    public TwistingVinesBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick_twisting(BlockState state, ServerLevel level, BlockPos pos,
                                              RandomSource random, CallbackInfo ci) {
        if (!(((Object)this) instanceof TwistingVinesBlock)) return;
        if (!Config.SERVER.twistingVinesEnabled.get()) return;
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
            int limit = Math.min(steps, 50);
            for (int i = 0; i < limit; i++) {
                int age = currentState.getValue(GrowingPlantHeadBlock.AGE);
                if (age >= 25) break;
                BlockPos next = currentPos.above(); // twisting vines grow UP
                if (!NetherVines.isValidGrowthState(level.getBlockState(next))) break;
                if (!CommonHooks.canCropGrow(level, next, currentState, true)) break;
                BlockState newHead = currentState.cycle(GrowingPlantHeadBlock.AGE);
                level.setBlockAndUpdate(next, newHead);
                CommonHooks.fireCropGrowPost(level, next, level.getBlockState(next));
                currentPos = next;
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
}
