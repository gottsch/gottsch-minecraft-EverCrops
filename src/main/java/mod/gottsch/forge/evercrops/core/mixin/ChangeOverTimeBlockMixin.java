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

import mod.gottsch.forge.evercrops.core.catchup.CopperCatchUp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up oxidation for copper — the random-tick wiring. Everything else, including the clock stamp,
 * lives in {@link CopperCatchUp}, which is shared with the Forge 1.20.1 line.
 *
 * <p><b>One mixin for every copper block.</b> All seven vanilla weathering-copper classes (block, cut,
 * chiseled, slab, stairs, door, trapdoor, grate, bulb) do nothing in {@code randomTick} but call this
 * interface's default {@code changeOverTime}, so hooking it here covers them all — plus any modded
 * implementor that does not override it. Copper doors only call it from the lower half, so catch-up
 * runs once per door, just as vanilla oxidation does.
 *
 * <p>Injecting into an interface needs a newer Mixin than Forge 1.20.1 ships; that line hooks the
 * copper classes' {@code randomTick} instead.
 *
 * @author Mark Gottschling
 */
@Mixin(ChangeOverTimeBlock.class)
public interface ChangeOverTimeBlockMixin {

    @Inject(method = "changeOverTime", at = @At(value = "HEAD"), cancellable = true)
    private void everCrops_changeOverTime(BlockState state, ServerLevel level, BlockPos pos,
                                          RandomSource random, CallbackInfo ci) {
        if (CopperCatchUp.onRandomTick(state, level, pos, random)) {
            ci.cancel();
        }
    }
}
