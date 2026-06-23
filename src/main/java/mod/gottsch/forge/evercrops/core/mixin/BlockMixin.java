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
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks placement and physics-destruction of catch-up crops at the {@link Block} level, covering
 * cases the {@code ModEvents} player events miss: {@code setPlacedBy} (any entity placement) and
 * {@code updateOrDestroy} (a crop popping off when its support is removed, which fires no BreakEvent).
 *
 * <p>Eligibility is the shared capability predicate {@link CropEligibility#isEligible(BlockState)}
 * (config-independent) — the single source of truth, replacing the former hardcoded block list.
 *
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
        if (!CropEligibility.isEligible(state)) {
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
        if (CropEligibility.isEligible(state)) {
            CropRegistry.remove((ServerLevel) levelAccessor, pos);
        }
    }
}
