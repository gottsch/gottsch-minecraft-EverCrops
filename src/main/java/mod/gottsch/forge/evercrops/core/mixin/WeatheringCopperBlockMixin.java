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
import net.minecraft.world.level.block.WeatheringCopperFullBlock;
import net.minecraft.world.level.block.WeatheringCopperSlabBlock;
import net.minecraft.world.level.block.WeatheringCopperStairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up oxidation for copper — the random-tick wiring. Everything else, including the clock stamp,
 * lives in {@link CopperCatchUp}, which is shared with the NeoForge 1.21.1 line.
 *
 * <p><b>Why a multi-target class mixin rather than the interface.</b> The NeoForge line hooks the
 * {@code ChangeOverTimeBlock} interface once. The Mixin that Forge 1.20.1 ships rejects injectors in
 * interfaces ("Injector in interface is unsupported"), so this hooks each copper class's
 * {@code randomTick} instead — one body, three targets. 1.20.1 has only these three weathering-copper
 * classes (full and cut blocks share {@code WeatheringCopperFullBlock}); doors, trapdoors, grates, bulbs
 * and chiseled copper arrived in 1.21. Each {@code randomTick} does nothing but call
 * {@code onRandomTick}, so starting here is the same as starting in the interface.
 *
 * @author Mark Gottschling
 */
@Mixin({WeatheringCopperFullBlock.class, WeatheringCopperSlabBlock.class, WeatheringCopperStairBlock.class})
public abstract class WeatheringCopperBlockMixin {

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    private void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                      RandomSource random, CallbackInfo ci) {
        if (CopperCatchUp.onRandomTick(state, level, pos, random)) {
            ci.cancel();
        }
    }
}
