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

import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Internal convenience facade kept for the base mod's own mixins; all logic now lives in the public
 * {@link EverCropsApi}. New code (and add-ons) should call {@code EverCropsApi} directly.
 *
 * @author Mark Gottschling on 4/26/2026
 */
public final class CropCatchUp {

    public static final int AVG_CALL_TICK_INTERVAL = EverCropsApi.AVG_CALL_TICK_INTERVAL;

    private CropCatchUp() {}

    public static int beginCatchUp(ServerLevel level, BlockPos pos, CropState cropState,
                                   int avgGrowthInterval, boolean requiresLight) {
        return EverCropsApi.beginCatchUp(level, pos, cropState, avgGrowthInterval, requiresLight);
    }

    public static boolean handleInPlaceHarvest(ServerLevel level, BlockPos pos, CropState cropState, BlockState state) {
        return EverCropsApi.handleInPlaceHarvest(level, pos, cropState, state);
    }

    @Deprecated
    public static boolean handleInPlaceHarvest(ServerLevel level, BlockPos pos, CropState cropState, int currentAge) {
        return EverCropsApi.handleInPlaceHarvest(level, pos, cropState, currentAge);
    }

    public static CropState createState(ServerLevel level, BlockPos pos) {
        return EverCropsApi.createState(level, pos);
    }
}
