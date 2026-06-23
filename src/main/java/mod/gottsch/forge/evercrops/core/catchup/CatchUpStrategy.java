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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The "how" half of catch-up growth (v4 §B). {@link mod.gottsch.forge.evercrops.core.catchup.CatchUpDecision}
 * decides <i>when</i> and <i>how many</i> steps; a strategy applies those steps with the block's own
 * growth action (advance a property in place, relocate a column head, branch a chorus flower, …).
 *
 * <p>This replaces the per-block bespoke growth loops that were copied across the catch-up mixins.
 * Eligibility (capability detection) is governed by {@code CropEligibility}; the strategy is purely
 * the growth action once the engine has decided growth should happen.
 *
 * @author Mark Gottschling
 */
@FunctionalInterface
public interface CatchUpStrategy {

    /**
     * Apply up to {@code steps} growth steps starting from {@code state} at {@code pos}.
     * Implementations own their per-step gating (max age, {@code ForgeHooks.onCropsGrowPre} veto,
     * environment checks) and must stop early when growth can no longer occur.
     *
     * @return true if any growth actually occurred (the caller should cancel vanilla's own tick)
     */
    boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random);
}
