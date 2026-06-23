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

import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ties the catch-up decision ({@link CropCatchUp#beginCatchUp}) to a {@link CatchUpStrategy}: the
 * decision computes how many growth steps are owed (mutating the timing fields on {@code cropState}
 * as a side effect), then the strategy performs them.
 *
 * <p>The caller (a catch-up mixin) is responsible for persisting {@code cropState} via
 * {@code CropRegistry.put} regardless of the return value (the decision may have refreshed the call
 * clock even when no growth is owed) and for cancelling vanilla's own tick when this returns true.
 *
 * @author Mark Gottschling
 */
public final class CatchUpEngine {

    private CatchUpEngine() {}

    /**
     * @return true if catch-up actually grew the block this tick (caller should {@code ci.cancel()}).
     */
    public static boolean run(ServerLevel level, BlockPos pos, BlockState state, CropState cropState,
                              int avgGrowthInterval, boolean requiresLight, RandomSource random,
                              CatchUpStrategy strategy) {
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, avgGrowthInterval, requiresLight);
        if (steps <= 0) {
            return false;
        }
        return strategy.grow(level, pos, state, steps, random);
    }
}
