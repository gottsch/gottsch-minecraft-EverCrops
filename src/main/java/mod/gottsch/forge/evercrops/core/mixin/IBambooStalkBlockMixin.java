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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes protected methods on BambooBlock needed by BambooStalkBlockMixin.
 * In 1.20.1 bamboo is a single class (BambooBlock); the stalk/sapling split
 * happened in 1.21.x.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(BambooStalkBlock.class)
public interface IBambooStalkBlockMixin {

    @Invoker("growBamboo")
    void invokeGrowBamboo(BlockState state, Level level, BlockPos pos, RandomSource random, int height);

    @Invoker("getHeightBelowUpToMax")
    int invokeGetHeightBelowUpToMax(BlockGetter level, BlockPos pos);
}
