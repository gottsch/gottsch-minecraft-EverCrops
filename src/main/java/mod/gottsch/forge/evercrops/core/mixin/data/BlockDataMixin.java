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
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropsBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * this version of the class is for pre-mod data gathering.
 * @author by Mark Gottschling on 3/14/2025
 */
@Mixin(Block.class)
public abstract class BlockDataMixin implements IItemProvider, net.minecraftforge.common.extensions.IForgeBlock {
    public BlockDataMixin(AbstractBlock.Properties properties) {

    }

    @Inject(method = "setPlacedBy", at = @At(value = "TAIL"))
    public void everCrops_setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack, CallbackInfo ci) {
        if (!world.isClientSide()) {
            if ((Block)(Object)this instanceof CropsBlock) {
                EverCrops.LOGGER.debug("update MapDb on setPlacedBy at {}", pos.toShortString());

                Optional<CropGrowthData> data = CropDataRegistry.get(pos);
                if (data.isPresent()) {
                    // retain any previous data and reset the last game times
                  CropGrowthData newData = new CropGrowthData(world.getGameTime(), world.getGameTime());
                  newData.setCallCount(data.get().getCallCount());
                  newData.setTotalCallDelta(data.get().getTotalCallDelta());
                  newData.setGrowthCount(data.get().getGrowthCount());
                  newData.setTotalGrowthDelta(data.get().getTotalGrowthDelta());
                  CropDataRegistry.put(pos, newData);
                } else {
                    CropDataRegistry.put(pos, new CropGrowthData(world.getGameTime(), world.getGameTime()));
                }
                CropDataRegistry.commit();
                Optional<CropGrowthData> dataCheck = CropDataRegistry.get(pos);
                dataCheck.ifPresent(cropGrowthData -> EverCrops.LOGGER.debug("dataCheck -> {}", cropGrowthData));
            }
        }
    }
}
