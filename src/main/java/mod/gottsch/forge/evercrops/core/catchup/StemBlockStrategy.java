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

import mod.gottsch.forge.evercrops.core.mixin.IStemBlockMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.Optional;

/**
 * In-place strategy for {@link StemBlock} (melon / pumpkin stems): advance {@code AGE} 0&ndash;7, then
 * at maturity spread <b>one</b> fruit and stop. A stem yields at most a single fruit (it converts to an
 * attached stem), so catch-up must never spawn several — hence the explicit single-fruit-then-break.
 *
 * <p>On 1.21 the stem's fruit / attached-stem are held as {@link net.minecraft.resources.ResourceKey}s
 * (see {@link IStemBlockMixin}) and resolved through the block registry, rather than as direct block
 * references.
 *
 * @author Mark Gottschling
 */
public final class StemBlockStrategy implements CatchUpStrategy {

    public static final StemBlockStrategy INSTANCE = new StemBlockStrategy();

    private StemBlockStrategy() {}

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        BlockState current = state;
        boolean grew = false;
        for (int i = 0; i < steps; i++) {
            // Another mod (land claim/protection, etc.) vetoed growth.
            if (!CommonHooks.canCropGrow(level, pos, current, true)) {
                break;
            }
            int age = current.getValue(StemBlock.AGE);
            if (age < 7) {
                current = current.setValue(StemBlock.AGE, age + 1);
                level.setBlock(pos, current, 3);
                CommonHooks.fireCropGrowPost(level, pos, current);
                grew = true;
            } else {
                // Mature stem: attempt to spread fruit once, then stop. A stem yields at most one
                // fruit (it converts to an attached stem), so catch-up must not spawn several.
                IStemBlockMixin stemBlock = (IStemBlockMixin) (Object) state.getBlock();
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockpos = pos.relative(direction);
                BlockState blockstate = level.getBlockState(blockpos.below());
                if (level.getBlockState(blockpos).isAir() && (blockstate.is(Blocks.FARMLAND) || blockstate.is(BlockTags.DIRT))) {
                    Registry<Block> registry = level.registryAccess().registryOrThrow(Registries.BLOCK);
                    Optional<Block> fruit = registry.getOptional(stemBlock.getFruit());
                    Optional<Block> attachedStem = registry.getOptional(stemBlock.getAttachedStem());
                    if (fruit.isPresent() && attachedStem.isPresent()) {
                        level.setBlockAndUpdate(blockpos, fruit.get().defaultBlockState());
                        level.setBlockAndUpdate(pos, attachedStem.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction));
                        CommonHooks.fireCropGrowPost(level, pos, current);
                        grew = true;
                    }
                }
                break;
            }
        }
        return grew;
    }
}
