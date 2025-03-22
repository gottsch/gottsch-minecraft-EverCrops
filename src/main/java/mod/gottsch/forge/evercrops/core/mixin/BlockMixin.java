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
package mod.gottsch.forge.evercrops.core.mixin;

import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import mod.gottsch.forge.evercrops.core.persistence.DimensionalBlockPos;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author by Mark Gottschling on 3/14/2025
 */
@Mixin(Block.class)
public abstract class BlockMixin extends AbstractBlock implements IItemProvider, net.minecraftforge.common.extensions.IForgeBlock {
    public BlockMixin(AbstractBlock.Properties properties) {
        super(properties);
    }

    @Inject(method = "setPlacedBy", at = @At(value = "TAIL"))
    public void evercrops_setPlacedBy(World level, BlockPos pos, BlockState p_180633_3_, LivingEntity entity, ItemStack stack, CallbackInfo ci) {
        if (!CropRegistry.isStarted()) {
            return;
        }

        if (!level.isClientSide()) {
            Block block = (Block)(Object)this;
            if (block instanceof CropsBlock
              || block instanceof StemBlock) {
//                EverCrops.LOGGER.debug("update mapDb on setPlacedBy at {}", pos.toShortString());

                // get the dimension
                ResourceLocation dimension = level.dimension().location();
                DimensionalBlockPos dimPos = new DimensionalBlockPos(dimension, pos);

                // create a new CropState to record current state
                CropState cropState = new CropState();
                cropState.setLastCallGameTime(level.getGameTime())
                        .setLastGrowthGameTime(level.getGameTime())
                        .setLastCallLightLevel(level.getRawBrightness(pos, 0))
                        .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));

                CropRegistry.put(dimPos, cropState);

//                if (EverCrops.LOGGER.isDebugEnabled()) {
//                    Optional<CropState> stateCheck = CropRegistry.get(dimPos);
//                    stateCheck.ifPresent(c -> EverCrops.LOGGER.de
//
//                    .2bug("stateCheck -> {}", c));
//                }
            }
        }
    }

    @Inject(method = "updateOrDestroy(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IWorld;Lnet/minecraft/util/math/BlockPos;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/IWorld;destroyBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z"))
    private static void evercrops_destroyBlock(BlockState state, BlockState replaceWithState, IWorld world, BlockPos pos, int p_241468_4_, int p_241468_5_, CallbackInfo ci) {
        if (CropRegistry.isStarted()) {
            if (!world.isClientSide()) {
                if (state.getBlock() instanceof CropsBlock
                  || state.getBlock() instanceof StemBlock) {
//                EverCrops.LOGGER.debug("remove crop block from {}", pos.toShortString());
                    // get the dimension
                    ResourceLocation dimension = ((World) world).dimension().location();
                    CropRegistry.remove(new DimensionalBlockPos(dimension, pos));
                }
            }
        }
    }
}
