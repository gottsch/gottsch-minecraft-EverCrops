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
package mod.gottsch.forge.evercrops.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * SPI for the per-dimension catch-up state store. The host (base EverCrops) binds a loader-specific
 * implementation (a {@code SavedData}-backed registry) once at startup via
 * {@link EverCropsApi#bindRegistry}; the API and addons then read/write through
 * {@link EverCropsApi}'s registry methods without depending on the loader's persistence classes.
 *
 * <p>Kept as an SPI (rather than the API calling the host directly) so the {@code api} package has
 * no dependency on {@code core} — the dependency arrow points one way: {@code core → api}.
 *
 * @author Mark Gottschling
 */
public interface CatchUpRegistry {

    Optional<CropState> get(ServerLevel level, BlockPos pos);

    void put(ServerLevel level, BlockPos pos, CropState state);

    void remove(ServerLevel level, BlockPos pos);

    /**
     * Remove stale entries in loaded chunks (positions whose block no longer matches {@code isCropBlock}).
     *
     * @return number of entries removed
     */
    int cleanup(ServerLevel level, Predicate<BlockState> isCropBlock);
}
