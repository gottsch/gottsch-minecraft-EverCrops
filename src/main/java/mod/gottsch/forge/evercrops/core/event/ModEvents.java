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
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
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
     * Register a CropState entry whenever a tracked crop block is placed by an entity.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if (!isTracked(state)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        CropRegistry.put(serverLevel, pos, CropCatchUp.createState(serverLevel, pos));
    }

    /**
     * Remove the CropState entry when a player breaks a tracked crop block.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        if (isTracked(state)) {
            CropRegistry.remove((ServerLevel) event.getLevel(), event.getPos());
        }
    }

    /**
     * Whether a placed/broken block should be tracked by EverCrops. Mirrors the
     * set of block classes that have a randomTick mixin in this mod.
     */
    private static boolean isTracked(BlockState state) {
        Block block = state.getBlock();
        if (Config.SERVER.cropsEnabled.get()) {
            if (block instanceof CropBlock) return true;
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
            if (block instanceof BambooSaplingBlock) return true;
            if (block instanceof BambooStalkBlock && state.hasProperty(BambooStalkBlock.STAGE)) return true;
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
