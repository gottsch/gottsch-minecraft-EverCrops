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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.ForgeHooks;

import java.util.Collections;

/**
 * The default, property-agnostic in-place strategy: advance the block's detected growth
 * {@link IntegerProperty} (resolved via {@link CropEligibility#growthPropertyOf}) one value per step,
 * up to the property's maximum, writing each step with a fixed {@code setBlock} flag.
 *
 * <p>Covers sweet berry bush, nether wart and cocoa today, and any modded crop whose block exposes a
 * recognized growth property — the §B "zero hardcoded property" path. Crops that need their own
 * growth state logic (vanilla {@code CropBlock} subclasses, stems) use a dedicated strategy instead.
 *
 * @author Mark Gottschling
 */
public final class IntegerPropertyAdvanceStrategy implements CatchUpStrategy {

    /** Shared instance for the vanilla bush family (sweet berry / nether wart / cocoa), which write with flag 2. */
    public static final IntegerPropertyAdvanceStrategy FLAG_2 = new IntegerPropertyAdvanceStrategy(2);

    private final int setBlockFlag;

    public IntegerPropertyAdvanceStrategy(int setBlockFlag) {
        this.setBlockFlag = setBlockFlag;
    }

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        IntegerProperty property = CropEligibility.growthPropertyOf(state.getBlock());
        if (property == null) {
            return false; // no recognized growth axis — nothing to advance
        }
        int max = Collections.max(property.getPossibleValues());

        BlockState current = state;
        boolean grew = false;
        for (int i = 0; i < steps; i++) {
            int age = current.getValue(property);
            if (age >= max) {
                break;
            }
            // Another mod (land claim/protection, etc.) vetoed growth — stop rather than spinning.
            if (!ForgeHooks.onCropsGrowPre(level, pos, current, true)) {
                break;
            }
            current = current.setValue(property, age + 1);
            level.setBlock(pos, current, setBlockFlag);
            ForgeHooks.onCropsGrowPost(level, pos, current);
            grew = true;
        }
        return grew;
    }
}
