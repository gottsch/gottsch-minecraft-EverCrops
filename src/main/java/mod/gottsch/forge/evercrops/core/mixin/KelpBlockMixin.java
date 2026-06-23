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

import mod.gottsch.forge.evercrops.core.catchup.ColumnCatchUp;
import mod.gottsch.forge.evercrops.core.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up growth for kelp. Targets GrowingPlantHeadBlock (kelp's randomTick is inherited from there,
 * not overridden in KelpBlock). Guards with instanceof to avoid affecting other subclasses (vines).
 *
 * <p>Grows upward into water; AGE 0-25; no light requirement. The head-walking, relocation and
 * cancel logic is shared with the nether/cave vines in {@link ColumnCatchUp}.
 *
 * @author Mark Gottschling on 4/27/2026
 */
@Mixin(GrowingPlantHeadBlock.class)
public abstract class KelpBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 9800;

    public KelpBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!(((Object) this) instanceof KelpBlock)) return;
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        if (!state.hasProperty(GrowingPlantHeadBlock.AGE)) return;

        ColumnCatchUp.headCatchUp(level, pos, state, AVG_GROWTH_TICK_INTERVAL, Direction.UP,
                (lvl, next) -> lvl.getBlockState(next).is(Blocks.WATER), ci); // kelp grows only into water
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    public void everCrops_randomTick_relocate(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!(((Object) this) instanceof KelpBlock)) return;
        if (!Config.SERVER.columnCropsEnabled.get()) return;
        ColumnCatchUp.headRelocate(level, pos, (Block) (Object) this, Direction.UP);
    }
}
