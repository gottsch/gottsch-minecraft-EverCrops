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

import mod.gottsch.forge.evercrops.core.catchup.ScheduledCatchUp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Multi-stage catch-up for sniffer eggs. A sniffer egg cracks twice and then hatches, and each
 * stage's {@code onPlace} schedules the next. After a long absence only the pending stage is
 * restarted early by {@link ScheduledCatchUp#onChunkTickingStart}; this shortens each following
 * stage by whatever time is still owed, so an egg away for a day cracks, cracks and hatches a tick
 * apart. Vanilla still does the cracking, the moss check, the sounds and the baby sniffer.
 *
 * @author Mark Gottschling on 10/15/2026
 */
@Mixin(SnifferEggBlock.class)
public abstract class SnifferEggBlockMixin {

    /**
     * The level the egg is being placed in, handed from {@link #everCrops_onPlace} to
     * {@link #everCrops_stageDelay} — the argument modifier only sees the call's own arguments.
     * Server thread only; cleared as soon as it is read.
     */
    @Unique
    private static Level everCrops_placingLevel;

    /**
     * A brand-new egg (not a crack of an existing one) starts with nothing owed, even if an earlier
     * egg at this spot was broken before it could spend its catch-up.
     */
    @Inject(method = "onPlace", at = @At(value = "HEAD"))
    private void everCrops_onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                                   boolean movedByPiston, CallbackInfo ci) {
        everCrops_placingLevel = level;
        if (!oldState.is(state.getBlock())) {
            ScheduledCatchUp.clearBudget(level, pos);
        }
    }

    @ModifyArg(method = "onPlace", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;I)V"))
    private int everCrops_stageDelay(BlockPos pos, Block block, int delay) {
        Level level = everCrops_placingLevel;
        everCrops_placingLevel = null;
        return level == null ? delay : ScheduledCatchUp.adjustStageDelay(level, pos, delay);
    }

    /** The egg is hatching, so anything still owed has nowhere to go. */
    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void everCrops_tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                CallbackInfo ci) {
        if (state.getValue(SnifferEggBlock.HATCH) >= SnifferEggBlock.MAX_HATCH_LEVEL) {
            ScheduledCatchUp.clearBudget(level, pos);
        }
    }
}
