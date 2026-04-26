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
package mod.gottsch.forge.evercrops.core.event;

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Game-bus event listeners for EverCrops.
 *
 * @author Mark Gottschling on 4/26/2026
 */
@EventBusSubscriber(modid = EverCrops.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvents {

    /**
     * Register a CropState entry whenever a crop or stem block is placed by an entity.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if ((state.getBlock() instanceof CropBlock && state.hasProperty(CropBlock.AGE))
                || (state.getBlock() instanceof StemBlock && state.hasProperty(StemBlock.AGE))) {
            ServerLevel serverLevel = (ServerLevel) event.getLevel();
            BlockPos pos = event.getPos();
            CropState cropState = new CropState();
            cropState.setLastCallGameTime(serverLevel.getGameTime())
                    .setLastGrowthGameTime(serverLevel.getGameTime())
                    .setLastCallLightLevel(serverLevel.getRawBrightness(pos, 0))
                    .setLastGrowthLightLevel(serverLevel.getRawBrightness(pos, 0));
            CropRegistry.put(serverLevel, pos, cropState);
        }
    }

    /**
     * Remove the CropState entry when a player breaks a crop or stem block.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) {
            CropRegistry.remove((ServerLevel) event.getLevel(), event.getPos());
        }
    }
}
