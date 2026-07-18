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
package mod.gottsch.forge.evercrops.core.persistence;

import mod.gottsch.forge.evercrops.api.BeehiveState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Static facade over {@link BeehiveSavedData}, mirroring {@link CropRegistry} for the bee store.
 * SavedData is always available for a loaded ServerLevel — no explicit start/stop lifecycle needed.
 *
 * @author Mark Gottschling
 */
public final class BeehiveRegistry {

    private BeehiveRegistry() {}

    public static Optional<BeehiveState> get(ServerLevel level, BlockPos pos) {
        return BeehiveSavedData.getOrCreate(level).get(pos);
    }

    public static void put(ServerLevel level, BlockPos pos, BeehiveState state) {
        BeehiveSavedData.getOrCreate(level).put(pos, state);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        BeehiveSavedData.getOrCreate(level).remove(pos);
    }

    /**
     * Removes stale entries in loaded chunks (positions whose block is no longer a beehive).
     *
     * @return number of entries removed
     */
    public static int cleanup(ServerLevel level, Predicate<BlockState> isBeehive) {
        return BeehiveSavedData.getOrCreate(level).cleanupStale(level, isBeehive);
    }
}
