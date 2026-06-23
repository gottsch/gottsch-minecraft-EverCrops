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
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.NetherVines;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up growth for twisting vines. Targets GrowingPlantHeadBlock (TwistingVinesBlock inherits
 * randomTick); guards with instanceof to avoid affecting kelp and weeping vines.
 *
 * <p>Grows upward; AGE 0-25; no light requirement (Nether plant). Head-walking/relocation logic is
 * shared in {@link ColumnCatchUp}.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(GrowingPlantHeadBlock.class)
public abstract class TwistingVinesBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 13500;

    public TwistingVinesBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick_twisting(BlockState state, ServerLevel level, BlockPos pos,
                                              RandomSource random, CallbackInfo ci) {
        if (!(((Object) this) instanceof TwistingVinesBlock)) return;
        if (!Config.SERVER.twistingVinesEnabled.get()) return;
        if (!state.hasProperty(GrowingPlantHeadBlock.AGE)) return;

        ColumnCatchUp.headCatchUp(level, pos, state, AVG_GROWTH_TICK_INTERVAL, Direction.UP,
                (lvl, next) -> NetherVines.isValidGrowthState(lvl.getBlockState(next)), ci);
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    public void everCrops_randomTick_twisting_relocate(BlockState state, ServerLevel level, BlockPos pos,
                                                       RandomSource random, CallbackInfo ci) {
        if (!(((Object) this) instanceof TwistingVinesBlock)) return;
        if (!Config.SERVER.twistingVinesEnabled.get()) return;
        ColumnCatchUp.headRelocate(level, pos, (Block) (Object) this, Direction.UP);
    }
}
