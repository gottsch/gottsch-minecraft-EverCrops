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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code grow} on PitcherCropBlock needed by {@code PitcherCropStrategy}.
 *
 * <p>Worth invoking rather than reimplementing: vanilla's {@code grow} already enforces the light
 * gate, the max-age clamp and the "is there room above for the upper half?" check, and it writes
 * <i>both</i> halves itself when the new age reaches 3. Driving it is what keeps catch-up from
 * leaving a headless half-plant behind.
 *
 * @author Mark Gottschling
 */
@Mixin(PitcherCropBlock.class)
public interface IPitcherCropBlockMixin {

    @Invoker("grow")
    void invokeGrow(ServerLevel level, BlockState state, BlockPos pos, int ageIncrement);
}
