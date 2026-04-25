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
package mod.gottsch.forge.evercrops.core.persistence.data;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Data-gathering registry — was used for pre-mod crop growth analysis.
 * The backing store (MapDB) has been removed; all methods are stubs.
 * Not used by the production mod; referenced only by the inactive data-mixin classes.
 *
 * @author Mark Gottschling on 3/13/2025
 */
public class CropDataRegistry {

    private CropDataRegistry() {}

    public static void start() {}

    public static void stop() {}

    public static Optional<CropGrowthData> get(BlockPos pos) {
        return Optional.empty();
    }

    public static Optional<CropGrowthData> put(BlockPos pos, CropGrowthData data) {
        return Optional.empty();
    }

    public static void commit() {}
}
