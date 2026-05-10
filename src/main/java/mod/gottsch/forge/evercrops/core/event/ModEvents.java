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
import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Game-bus event listeners for EverCrops.
 * Registers a CropState on placement and removes it on break for every block
 * class that has a randomTick mixin in this mod.
 *
 * @author Mark Gottschling on 2026-05-10
 */
@Mod.EventBusSubscriber(modid = EverCrops.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getPlacedBlock();
        if (!isTracked(state)) return;
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        CropRegistry.put(serverLevel, pos, CropCatchUp.createState(serverLevel, pos));
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (isTracked(event.getState())) {
            CropRegistry.remove((ServerLevel) event.getLevel(), event.getPos());
        }
    }

    /**
     * Whether a block should be tracked by EverCrops.
     * Mirrors the full set of block classes that have a randomTick mixin.
     */
    public static boolean isTracked(BlockState state) {
        Block block = state.getBlock();

        if (Config.SERVER.cropsEnabled.get()) {
            // CropBlock guard mirrors CropBlockMixin: skip blocks with no "age" property
            if (block instanceof CropBlock
                    && state.getProperties().stream().anyMatch(p -> p.getName().equals("age"))) return true;
        }
        if (Config.SERVER.stemCropsEnabled.get()) {
            if (block instanceof StemBlock && state.hasProperty(StemBlock.AGE)) return true;
        }
        if (Config.SERVER.bushCropsEnabled.get()) {
            if (block instanceof SweetBerryBushBlock && state.hasProperty(SweetBerryBushBlock.AGE)) return true;
            if (block instanceof NetherWartBlock && state.hasProperty(NetherWartBlock.AGE)) return true;
            if (block instanceof CocoaBlock && state.hasProperty(CocoaBlock.AGE)) return true;
        }
        if (Config.SERVER.columnCropsEnabled.get()) {
            if (block instanceof SugarCaneBlock && state.hasProperty(SugarCaneBlock.AGE)) return true;
            if (block instanceof CactusBlock && state.hasProperty(CactusBlock.AGE)) return true;
            if (block instanceof KelpBlock && state.hasProperty(GrowingPlantHeadBlock.AGE)) return true;
        }
        if (Config.SERVER.saplingCropsEnabled.get()) {
            if (block instanceof SaplingBlock && state.hasProperty(SaplingBlock.STAGE)) return true;
        }
        if (Config.SERVER.bambooEnabled.get()) {
            if (block instanceof BambooStalkBlock && state.hasProperty(BambooStalkBlock.STAGE)) return true;
            if (block instanceof BambooSaplingBlock) return true;
        }
        if (Config.SERVER.twistingVinesEnabled.get()) {
            if (block instanceof TwistingVinesBlock && state.hasProperty(GrowingPlantHeadBlock.AGE)) return true;
        }
        if (Config.SERVER.weepingVinesEnabled.get()) {
            if (block instanceof WeepingVinesBlock && state.hasProperty(GrowingPlantHeadBlock.AGE)) return true;
        }
        if (Config.SERVER.caveVinesEnabled.get()) {
            if (block instanceof CaveVinesBlock && state.hasProperty(GrowingPlantHeadBlock.AGE)) return true;
        }
        if (Config.SERVER.chorusFlowerEnabled.get()) {
            if (block instanceof ChorusFlowerBlock && state.hasProperty(ChorusFlowerBlock.AGE)) return true;
        }
        return false;
    }
}
