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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author by Mark Gottschling on 3/14/2025
 */
@Mixin(Block.class)
public abstract class BlockMixin extends BlockBehaviour implements ItemLike, net.minecraftforge.common.extensions.IForgeBlock {
    public BlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "setPlacedBy", at = @At(value = "TAIL"))
    public void evercrops_setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack, CallbackInfo ci) {
        if (!CropRegistry.isStarted()) {
            return;
        }

        if (!level.isClientSide()) {
            Block block = (Block)(Object)this;
            if (block instanceof CropBlock
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

    @Inject(method = "updateOrDestroy(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"))
    private static void evercrops_destroyBlock(BlockState state, BlockState replaceWithState, LevelAccessor levelAccessor, BlockPos pos, int p_49913_, int p_49914_, CallbackInfo ci) {
        if (CropRegistry.isStarted()) {
            if (!levelAccessor.isClientSide()) {
                if (state.getBlock() instanceof CropBlock
                  || state.getBlock() instanceof StemBlock) {
//                EverCrops.LOGGER.debug("remove crop block from {}", pos.toShortString());
                    // get the dimension
                    ResourceLocation dimension = ((Level) levelAccessor).dimension().location();
                    CropRegistry.remove(new DimensionalBlockPos(dimension, pos));
                }
            }
        }
    }
}
