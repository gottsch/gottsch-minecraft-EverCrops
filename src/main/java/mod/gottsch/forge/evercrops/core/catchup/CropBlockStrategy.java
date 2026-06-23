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

import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.ForgeHooks;

/**
 * In-place strategy for {@link CropBlock} and its subclasses (wheat, carrots, potatoes, beetroot,
 * torchflower, and any modded {@code CropBlock} — e.g. Mystical Agriculture crops).
 *
 * <p>Grows via the public, virtual {@link CropBlock#getStateForAge(int)} / {@link CropBlock#getMaxAge()}
 * so subclasses that customise their growth states (different max age, alternate age property, extra
 * state) are handled correctly. Age is read through the detected growth property rather than the
 * {@code protected getAge(BlockState)} so this can live outside the {@code CropBlock} hierarchy.
 *
 * @author Mark Gottschling
 */
public final class CropBlockStrategy implements CatchUpStrategy {

    public static final CropBlockStrategy INSTANCE = new CropBlockStrategy();

    private CropBlockStrategy() {}

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        CropBlock crop = (CropBlock) state.getBlock();
        IntegerProperty ageProperty = CropEligibility.growthPropertyOf(crop);
        if (ageProperty == null) {
            return false;
        }
        int maxAge = crop.getMaxAge();

        BlockState current = state;
        boolean grew = false;
        for (int i = 0; i < steps; i++) {
            int age = current.getValue(ageProperty);
            if (age >= maxAge) {
                break;
            }
            // Another mod (land claim/protection, etc.) vetoed growth — stop rather than spinning.
            if (!ForgeHooks.onCropsGrowPre(level, pos, current, true)) {
                break;
            }
            current = crop.getStateForAge(age + 1);
            level.setBlock(pos, current, 3);
            ForgeHooks.onCropsGrowPost(level, pos, current);
            grew = true;
        }
        return grew;
    }
}
