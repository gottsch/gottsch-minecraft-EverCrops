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
package mod.gottsch.forge.evercrops.core.mixin.data;

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.persistence.data.CropDataRegistry;
import mod.gottsch.forge.evercrops.core.persistence.data.CropGrowthData;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Random;

/**
 * @author by Mark Gottschling on 3/17/2025
 */
@Mixin(CropsBlock.class)
public abstract class CropBlockDataMixin extends BushBlock implements IGrowable {

    public CropBlockDataMixin(AbstractBlock.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerWorld level, BlockPos pos, Random randomSource, CallbackInfo ci) {
        EverCrops.LOGGER.debug("randomTick called... at {}", pos.toShortString());

        Optional<CropGrowthData> data = CropDataRegistry.get(pos);
        if (data.isPresent()) {
            /*
             * retain any previous data and reset the last game times.
             * NOTE why do we have to copy old data to the new object
             * instead of updating the object from the registry. because much
             * like the new DataComponents (1.21), the object has to be immutable.
             * !Actually probably don't need to create a new object as the class isn't
             * immutable, just need to ensure to call the put() method and commit()!
             */
            CropGrowthData newData = new CropGrowthData();

            // calc new call data
            newData.setLastCallGameTime(level.getGameTime());
            newData.setCallCount(data.get().getCallCount() + 1);
            long delta = level.getGameTime() - data.get().getLastCallGameTime();
            newData.setTotalCallDelta(data.get().getTotalCallDelta() + delta);

            // retain growth data
            newData.setLastGrowthGameTime(data.get().getLastGrowthGameTime());
            newData.setGrowthCount(data.get().getGrowthCount());
            newData.setTotalGrowthDelta(data.get().getTotalGrowthDelta());
            CropDataRegistry.put(pos, newData);

        } else {
            // NOTE this shouldn't happen
            CropDataRegistry.put(pos, new CropGrowthData(level.getGameTime(), level.getGameTime()));
        }
        CropDataRegistry.commit();
        if (EverCrops.LOGGER.isDebugEnabled()) {
            Optional<CropGrowthData> dataCheck = CropDataRegistry.get(pos);
            if (dataCheck.isPresent()) {
                EverCrops.LOGGER.debug("randomTick.dataCheck -> {}", dataCheck.get());
            } else {
                EverCrops.LOGGER.debug("couldn't find at -> {}", pos);
            }
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;setBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    public void everCrops_randomTick_setBlock(BlockState state, ServerWorld world, BlockPos pos, Random randomSource, CallbackInfo ci) {
        EverCrops.LOGGER.debug("randomTick.setBlock called... at {}", pos.toShortString());

        Optional<CropGrowthData> data = CropDataRegistry.get(pos);
        if (data.isPresent()) {
            /*
             * retain any previous data and reset the last game times
             */
            CropGrowthData newData = new CropGrowthData();

            // calc new growth
            newData.setLastGrowthGameTime(world.getGameTime());
            newData.setGrowthCount(data.get().getGrowthCount() + 1);
            long delta = world.getGameTime() - data.get().getLastGrowthGameTime();
            newData.setTotalGrowthDelta(data.get().getTotalGrowthDelta() + delta);

            // retain call data
            newData.setLastCallGameTime(data.get().getLastCallGameTime());
            newData.setCallCount(data.get().getCallCount());
            newData.setTotalCallDelta(data.get().getTotalCallDelta());

            CropDataRegistry.put(pos, newData);

        } else {
            // NOTE this shouldn't happen
            CropDataRegistry.put(pos, new CropGrowthData(world.getGameTime(), world.getGameTime()));
        }
        CropDataRegistry.commit();

        if (EverCrops.LOGGER.isDebugEnabled()) {
            Optional<CropGrowthData> dataCheck = CropDataRegistry.get(pos);
            dataCheck.ifPresent(cropGrowthData -> EverCrops.LOGGER.debug("randomTick.setBlock.dataCheck -> {}", cropGrowthData));
        }
    }
}
