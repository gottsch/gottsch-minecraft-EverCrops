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

import mod.gottsch.forge.evercrops.api.CatchUpRegistry;
import mod.gottsch.forge.evercrops.api.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Binds the public {@link CatchUpRegistry} SPI to this loader's {@code SavedData}-backed
 * {@link CropRegistry}. Registered into {@code EverCropsApi} once at startup, so the API and add-ons
 * read/write the same per-dimension store the base mod's mixins use.
 *
 * @author Mark Gottschling
 */
public final class CropRegistryAdapter implements CatchUpRegistry {

    @Override
    public Optional<CropState> get(ServerLevel level, BlockPos pos) {
        return CropRegistry.get(level, pos);
    }

    @Override
    public void put(ServerLevel level, BlockPos pos, CropState state) {
        CropRegistry.put(level, pos, state);
    }

    @Override
    public void remove(ServerLevel level, BlockPos pos) {
        CropRegistry.remove(level, pos);
    }

    @Override
    public int cleanup(ServerLevel level, Predicate<BlockState> isCropBlock) {
        return CropRegistry.cleanup(level, isCropBlock);
    }
}
