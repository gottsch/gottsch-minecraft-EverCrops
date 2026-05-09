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

import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
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
        if (level.isClientSide()) {
            return;
        }
        Block block = (Block) (Object) this;
        if (!evercrops$isTracked(block, state)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        CropRegistry.put(serverLevel, pos, CropCatchUp.createState(serverLevel, pos));
    }

    @Inject(method = "updateOrDestroy(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"))
    private static void evercrops_destroyBlock(BlockState state, BlockState replaceWithState, LevelAccessor levelAccessor, BlockPos pos, int p_49913_, int p_49914_, CallbackInfo ci) {
        if (levelAccessor.isClientSide()) {
            return;
        }
        if (evercrops$isTracked(state.getBlock(), state)) {
            CropRegistry.remove((ServerLevel) levelAccessor, pos);
        }
    }

    private static boolean evercrops$isTracked(Block block, BlockState state) {
        if (block instanceof CropBlock) {
            // getAgeProperty() is protected; use the static CropBlock.AGE here.
            // Subclasses that override getAgeProperty() (e.g. BeetrootBlock) are
            // adopted by CropBlockMixin's randomTick fallback on their first tick.
            return state.hasProperty(CropBlock.AGE);
        }
        if (block instanceof StemBlock) {
            return state.hasProperty(StemBlock.AGE);
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.hasProperty(SweetBerryBushBlock.AGE);
        }
        if (block instanceof NetherWartBlock) {
            return state.hasProperty(NetherWartBlock.AGE);
        }
        if (block instanceof CocoaBlock) {
            return state.hasProperty(CocoaBlock.AGE);
        }
        if (block instanceof SugarCaneBlock) {
            return true;
        }
        if (block instanceof CactusBlock) {
            return true;
        }
        if (block instanceof KelpBlock) {
            return true;
        }
        if (block instanceof SaplingBlock) {
            return true;
        }
        if (block instanceof BambooSaplingBlock) {
            return true;
        }
        if (block instanceof BambooStalkBlock) {
            return true;
        }
        if (block instanceof TwistingVinesBlock) {
            return true;
        }
        if (block instanceof WeepingVinesBlock) {
            return true;
        }
        if (block instanceof CaveVinesBlock) {
            return true;
        }
        if (block instanceof ChorusFlowerBlock) {
            return state.hasProperty(ChorusFlowerBlock.AGE);
        }
        return false;
    }
}
