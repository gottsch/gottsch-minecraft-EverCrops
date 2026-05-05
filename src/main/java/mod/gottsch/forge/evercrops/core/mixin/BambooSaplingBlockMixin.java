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
package mod.gottsch.forge.evercrops.core.mixin;

import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Registers a CropState for bamboo saplings so that when the sapling grows into
 * a BambooStalkBlock the position already has tracking data. Without this, bamboo
 * placed from inventory creates a BambooSaplingBlock (a different class), which is
 * never caught by the BambooStalkBlockMixin until the stalk's first natural random tick.
 *
 * When the sapling grows, vanilla's updateShape converts the sapling at pos to a
 * BambooStalkBlock. The CropState registered here carries over to that bottom stalk,
 * where BambooStalkBlockMixin picks it up on its next random tick.
 *
 * No cancel: vanilla randomTick must still run so the sapling can actually grow.
 * Call timestamps are kept fresh while the chunk stays loaded so the resulting
 * stalk does not trigger premature catch-up during normal (chunk-loaded) gameplay.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(BambooSaplingBlock.class)
public abstract class BambooSaplingBlockMixin extends Block implements BonemealableBlock {

    public BambooSaplingBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.bambooEnabled.get()) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // First tick on this sapling: register a fresh state so the position is
            // already tracked when the sapling converts to a BambooStalkBlock.
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        } else {
            // Sapling is still alive in a loaded chunk — refresh the call timestamp so
            // the state does not look stale and trigger spurious catch-up when the stalk
            // eventually gets its first tick after a normal (online) sapling growth.
            CropState cropState = existing.get();
            cropState.setLastCallGameTime(level.getGameTime())
                     .setLastCallLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState);
        }
        // No ci.cancel() — vanilla must still attempt to grow the sapling.
    }
}
