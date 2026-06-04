/*
 * This file is part of EverCrops.
 * Copyright (c) 2025 Mark Gottschling (gottsch)
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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Static facade over {@link CropSavedData}.
 * SavedData is always available for a loaded ServerLevel — no explicit
 * start/stop lifecycle is needed.
 *
 * @author Mark Gottschling on 4/25/2025
 */
public class CropRegistry {

    public static Optional<CropState> get(ServerLevel level, BlockPos pos) {
        return CropSavedData.getOrCreate(level).get(pos);
    }

    public static void put(ServerLevel level, BlockPos pos, CropState state) {
        CropSavedData.getOrCreate(level).put(pos, state);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        CropSavedData.getOrCreate(level).remove(pos);
    }

    /**
     * Removes stale registry entries in loaded chunks (positions whose block is no
     * longer a tracked crop). See {@link CropSavedData#cleanupStale(ServerLevel, Predicate)}.
     *
     * @return number of entries removed
     */
    public static int cleanup(ServerLevel level, Predicate<BlockState> isCropBlock) {
        return CropSavedData.getOrCreate(level).cleanupStale(level, isCropBlock);
    }
}
