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
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for bamboo (BambooBlock). Bamboo grows by placing a new
 * BambooBlock one block above the current top; the column cap is 16 blocks.
 *
 * In 1.20.1 bamboo is a single class (BambooBlock); the stalk/sapling split
 * happened in 1.21.x. This mixin targets BambooBlock directly.
 *
 * Key differences from sugar-cane/cactus column crops:
 * - Growth is gated on STAGE == 0 (STAGE 1 = done growing / bonemeal-boosted state).
 * - Light check is sky-only at pos.above(): getRawBrightness(pos.above(), 0) >= 9.
 * - Max column height is 16 (not 3).
 * - growBamboo() is a separate protected method; called via @Invoker.
 * - No ci.cancel() needed: vanilla's own STAGE and height checks gate any
 *   double-grow after this HEAD inject returns.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(BambooStalkBlock.class)
public abstract class BambooStalkBlockMixin extends Block implements BonemealableBlock {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 4500;

    public BambooStalkBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.bambooEnabled.get()) return;
        if (!state.hasProperty(BambooStalkBlock.STAGE)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Always register on first tick (see BambooSaplingBlockMixin for rationale).
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }

        CropState cropState = existing.get();
        // requiresLight=false: we apply the bamboo-specific sky-only light check
        // (pos.above(), getRawBrightness) manually inside the loop below.
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        if (steps > 0) {
            BlockPos currentPos = pos;
            BlockState currentState = state;
            // Bamboo cap is 16 blocks; allow 18 iterations for a couple of extra tries
            int limit = Math.min(steps, 18);

            for (int i = 0; i < limit; i++) {
                // Bamboo only grows when STAGE == 0 (STAGE 1 never passes isRandomlyTicking)
                if (currentState.getValue(BambooStalkBlock.STAGE) != 0) break;

                // Sky-only light at the block above — bamboo needs sky exposure, not torchlight
                if (level.getRawBrightness(currentPos.above(), 0) < 9) break;

                // The block above must be air
                if (!level.isEmptyBlock(currentPos.above())) break;

                // Current column height; stop if at cap
                int height = ((IBambooStalkBlockMixin)(Object)this)
                        .invokeGetHeightBelowUpToMax(level, currentPos) + 1;
                if (height >= 16) break;

                // Bypass the random 1/3 gate (p_261766_.nextInt(3) == 0) for catch-up
                if (!net.minecraftforge.common.ForgeHooks.onCropsGrowPre(level, currentPos, currentState, true)) break;

                // Places a new BambooBlock at currentPos.above()
                ((IBambooStalkBlockMixin)(Object)this)
                        .invokeGrowBamboo(currentState, level, currentPos, random, height);
                net.minecraftforge.common.ForgeHooks.onCropsGrowPost(level, currentPos, currentState);

                // Advance tracking to the new top block
                BlockPos above = currentPos.above();
                BlockState aboveState = level.getBlockState(above);
                if (!aboveState.is((Block)(Object)this)) break; // growBamboo failed silently
                currentPos = above;
                currentState = aboveState;
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
