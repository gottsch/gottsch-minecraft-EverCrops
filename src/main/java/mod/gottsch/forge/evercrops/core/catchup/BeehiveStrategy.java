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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Catch-up strategy for beehives and bee nests: advance the block's {@code honey_level} toward the
 * vanilla maximum ({@link BeehiveBlock#MAX_HONEY_LEVELS}). Unlike the crop strategies this is a single
 * clamped write rather than a per-state loop — honey is a plain bounded integer with no successor
 * block or per-step veto — and it mirrors vanilla's own {@code setBlockAndUpdate} (flag 3).
 *
 * <p>The owed {@code steps} are decided upstream by {@code computeStepsUnlit} using the hive's learned
 * production rate (see {@link BeehiveDecision}); this class only applies the clamped result.
 *
 * @author Mark Gottschling
 */
public final class BeehiveStrategy implements CatchUpStrategy {

    public static final BeehiveStrategy INSTANCE = new BeehiveStrategy();

    private BeehiveStrategy() {}

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        if (!state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            return false;
        }
        int honey = state.getValue(BeehiveBlock.HONEY_LEVEL);
        int target = Math.min(BeehiveBlock.MAX_HONEY_LEVELS, honey + steps);
        if (target <= honey) {
            return false;
        }
        level.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, target), 3);
        return true;
    }
}
