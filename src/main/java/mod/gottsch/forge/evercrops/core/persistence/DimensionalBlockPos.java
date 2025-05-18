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
import net.minecraft.resources.ResourceLocation;

import java.io.Serializable;
import java.util.Objects;

/**
 * CropRegistry is exists for all dimensions, so in order to avoid collisions,
 * a dimension is combined with a BlockPos.
 *
 * @author by Mark Gottschling on 3/17/2025
 */
public class DimensionalBlockPos implements Serializable {
    private ResourceLocation dimension;
    private BlockPos pos;

    public DimensionalBlockPos() {}

    public DimensionalBlockPos(ResourceLocation dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    public DimensionalBlockPos(String dimension, int x, int y, int z) {
        this.dimension = new ResourceLocation(dimension);
        this.pos = new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DimensionalBlockPos that = (DimensionalBlockPos) o;
        return Objects.equals(dimension, that.dimension) && Objects.equals(pos, that.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode() + dimension.hashCode();
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    public void setDimension(ResourceLocation dimension) {
        this.dimension = dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public String toString() {
        return "DimensionalBlockPos{" +
                "dimension=" + dimension +
                ", pos=" + pos +
                '}';
    }
}
