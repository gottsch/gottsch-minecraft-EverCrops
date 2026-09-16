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
package mod.gottsch.forge.evercrops.core.catchup;

import mod.gottsch.forge.evercrops.api.CatchUpStrategy;
import mod.gottsch.forge.evercrops.core.mixin.IPitcherCropBlockMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Catch-up strategy for the pitcher plant. Unlike {@link CropBlockStrategy} this cannot simply write
 * a higher age: at age 3 the plant goes double-tall, and vanilla's growth writes a <i>second</i>
 * block above with {@code HALF=UPPER}. Advancing the age in place would leave a headless half-plant.
 *
 * <p>So this drives vanilla's own {@code grow} through {@link IPitcherCropBlockMixin} instead of
 * reimplementing it. That method already owns the light gate, the max-age clamp, the room-above
 * check and both block writes, so a caught-up plant is built exactly the way a watched one is.
 *
 * <p><b>One age at a time, deliberately.</b> {@code grow} clamps {@code age + increment} to 4 and
 * then asks {@code canGrow} about that <i>final</i> age. Handing it the whole owed step count in one
 * call would therefore ask about age 4 — so a young plant with a block above it would fail the
 * room-above check and grow nothing at all, where vanilla would have walked it up to age 2 and
 * stalled there. Stepping by one and stopping as soon as the age stops moving reproduces vanilla
 * exactly and costs at most {@code MAX_AGE} iterations.
 *
 * @author Mark Gottschling
 */
public final class PitcherCropStrategy implements CatchUpStrategy {

    public static final PitcherCropStrategy INSTANCE = new PitcherCropStrategy();

    private PitcherCropStrategy() {}

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        if (!(state.getBlock() instanceof PitcherCropBlock pitcher) || !state.hasProperty(PitcherCropBlock.AGE)) {
            return false;
        }
        IPitcherCropBlockMixin invoker = (IPitcherCropBlockMixin) pitcher;

        // More owed steps than ages can never help — the ladder is only 0..MAX_AGE long.
        int limit = Math.min(steps, PitcherCropBlock.MAX_AGE);
        BlockState current = state;
        boolean grew = false;
        for (int i = 0; i < limit; i++) {
            int before = current.getValue(PitcherCropBlock.AGE);
            invoker.invokeGrow(level, current, pos, 1);

            // Re-read rather than assume: grow() silently does nothing when its own gating refuses
            // (max age, too dark, no room above for the upper half), and that is our signal to stop.
            current = level.getBlockState(pos);
            if (!current.hasProperty(PitcherCropBlock.AGE) || current.getValue(PitcherCropBlock.AGE) == before) {
                break;
            }
            grew = true;
        }
        return grew;
    }
}
