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
import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Internal convenience facade kept for the base mod's own mixins; delegates to {@link EverCropsApi#catchUp}.
 *
 * @author Mark Gottschling
 */
public final class CatchUpEngine {

    private CatchUpEngine() {}

    public static boolean run(ServerLevel level, BlockPos pos, BlockState state, CropState cropState,
                              int avgGrowthInterval, boolean requiresLight, RandomSource random,
                              CatchUpStrategy strategy) {
        return EverCropsApi.catchUp(level, pos, state, cropState, avgGrowthInterval, requiresLight, random, strategy);
    }
}
