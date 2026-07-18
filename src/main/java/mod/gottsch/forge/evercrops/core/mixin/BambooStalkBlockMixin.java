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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-up growth for bamboo. Bamboo grows by placing a new bamboo block one above the current top
 * (via the vanilla {@code growBamboo} invoker); the column cap is 16. The bamboo-specific growth loop
 * (STAGE gate, sky-only light, height-via-invoker) is shared in {@link ColumnCatchUp#bambooCatchUp}.
 *
 * @author Mark Gottschling on 5/5/2026
 */
@Mixin(BambooStalkBlock.class)
public abstract class BambooStalkBlockMixin extends Block implements BonemealableBlock {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 4500;

    public BambooStalkBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.bambooEnabled.get()) return;
        if (!state.hasProperty(BambooStalkBlock.STAGE)) return;

        ColumnCatchUp.bambooCatchUp(level, pos, state, random, AVG_GROWTH_TICK_INTERVAL, 16,
                (IBambooStalkBlockMixin) (Object) this);
    }
}
