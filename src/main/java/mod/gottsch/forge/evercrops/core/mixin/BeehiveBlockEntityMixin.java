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

import mod.gottsch.forge.evercrops.core.catchup.BeehiveCatchUp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up growth for beehives and bee nests. Unlike every other EverCrops target, honey is not
 * advanced by a block {@code randomTick} — it is driven by the block entity's {@code serverTick} as
 * nectar bees are released. So this hooks {@code serverTick} (the first block-entity mixin in the mod)
 * and lets the shared unlit engine fire only after a real chunk-unload gap; normal loaded ticks are
 * a cheap no-op. Non-cancellable: vanilla's own bee logic still runs.
 *
 * @author Mark Gottschling
 */
@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void everCrops_serverTick(Level level, BlockPos pos, BlockState state,
                                             BeehiveBlockEntity be, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            BeehiveCatchUp.onServerTick(serverLevel, pos, state, be);
        }
    }
}
