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
package mod.gottsch.forge.evercrops.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin exposing {@link BeehiveBlockEntity}'s private {@code savedFlowerPos} — the hive's
 * remembered flower location, a cheap and reliable "this hive has foraged" signal used by the
 * cold-start eligibility gate in beehive catch-up. Follows the project's {@code I*Mixin} accessor
 * convention (see {@code IStemBlockMixin}); cast the entity to this interface at the call site.
 *
 * @author Mark Gottschling
 */
@Mixin(BeehiveBlockEntity.class)
public interface IBeehiveBlockEntityMixin {

    @Accessor("savedFlowerPos")
    BlockPos everCrops_getSavedFlowerPos();
}
