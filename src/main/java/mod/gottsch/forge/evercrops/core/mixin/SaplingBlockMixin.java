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

import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.api.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for saplings. Vanilla growth uses a two-stage STAGE property
 * (0 → 1 → tree). Stage advance and tree-grow attempt each gate on
 * random.nextInt(7) == 0 and light level >= 9 on the block above the sapling.
 *
 * Key differences from AGE-based crops:
 * - Light check: getMaxLocalRawBrightness(pos.above()) >= 9 (sky+block light on block above)
 * - advanceTree() is public on SaplingBlock — called directly for stage 1 → tree
 * - After a successful tree grow the sapling block is consumed; CropRegistry must
 *   be cleaned up immediately and vanilla's randomTick cancelled so it cannot
 *   overwrite the new tree structure with a sapling state.
 *
 * @author Mark Gottschling on 5/2/2026
 */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin extends BushBlock implements BonemealableBlock {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 9450;

    public SaplingBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }

        CropState cropState = existing.get();
        // Pass requiresLight=false: we do the sapling-specific light check
        // (pos.above(), sky+block) manually inside the loop below.
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        if (steps > 0) {
            BlockState currentState = state;
            // Saplings have at most 2 meaningful stages; cap at 3 to be safe
            int limit = Math.min(steps, 3);
            for (int i = 0; i < limit; i++) {
                // Sapling-specific light check: sky+block light on the block ABOVE
                if (level.getMaxLocalRawBrightness(pos.above()) < 9) break;

                int stage = currentState.getValue(SaplingBlock.STAGE);
                if (stage == 0) {
                    // Advance STAGE 0 → 1
                    currentState = currentState.cycle(SaplingBlock.STAGE);
                    level.setBlock(pos, currentState, 4);
                    // Continue — if steps remain the next iteration will attempt tree growth
                } else {
                    // STAGE 1: attempt to grow a tree via the public advanceTree method
                    ((SaplingBlock)(Object)this).advanceTree(level, pos, currentState, random);
                    if (!level.getBlockState(pos).is((Block)(Object)this)) {
                        // Tree grew successfully — sapling is gone.
                        // Clean up registry and cancel vanilla's randomTick so it cannot
                        // overwrite the new tree structure using the stale BlockState parameter.
                        CropRegistry.remove(level, pos);
                        ci.cancel();
                        return;
                    }
                    // growTree failed (insufficient space) — nothing we can do this tick
                    break;
                }
            }
            // Catch-up applied some steps (or broke early); suppress vanilla this tick
            // so it doesn't double-advance the same sapling on top of catch-up work.
            CropRegistry.put(level, pos, cropState);
            ci.cancel();
        } else {
            CropRegistry.put(level, pos, cropState);
            // steps == 0: let vanilla's randomTick run normally
        }
    }
}
