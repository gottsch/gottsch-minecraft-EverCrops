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

import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.catchup.CopperStrategy;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up oxidation for copper. The oxidation action lives in {@code CopperStrategy}; this is the
 * random-tick wiring.
 *
 * <p><b>One mixin for every copper block.</b> All seven vanilla weathering-copper classes (block, cut,
 * chiseled, slab, stairs, door, trapdoor, grate, bulb) do nothing in {@code randomTick} but call this
 * interface's default {@code changeOverTime}, so hooking it here covers them all — plus any modded
 * implementor that does not override it. Copper doors only call it from the lower half, so catch-up
 * runs once per door, just as vanilla oxidation does.
 *
 * <p>No light gate: vanilla oxidation has none.
 *
 * @author Mark Gottschling
 */
@Mixin(ChangeOverTimeBlock.class)
public interface ChangeOverTimeBlockMixin {

    @Inject(method = "changeOverTime", at = @At(value = "HEAD"), cancellable = true)
    private void everCrops_changeOverTime(BlockState state, ServerLevel level, BlockPos pos,
                                          RandomSource random, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();

        // Not CatchUpEngine.run: copper also spends opportunities banked on earlier ticks, when a
        // less-weathered neighbour was in the way. The engine resets the clock on every ordinary
        // loaded tick, so the bank has to live in its own field rather than in the clock.
        //
        // The bank is only good for the stage it was earned at, recorded in lastAge (copper has no age
        // property, so the field is otherwise unused). If the block is anywhere else now — a player
        // scraped it back with an axe, say — the bank is dropped, so scraped copper does not snap
        // straight back to the stage it was just taken from.
        if (cropState.getBankedSteps() > 0 && cropState.getLastAge() != CopperStrategy.stageOf(state)) {
            cropState.setBankedSteps(0);
        }
        int owed = EverCropsApi.beginCatchUp(level, pos, cropState,
                EverCropsApi.config().copperOxidationIntervalTicks(), false);
        long available = (long) Math.max(0, owed) + cropState.getBankedSteps();
        boolean grew = false;
        if (available > 0) {
            CopperStrategy.Outcome outcome = CopperStrategy.INSTANCE.advance(level, pos, state, available, random);
            grew = outcome.grew();
            cropState.setBankedSteps((int) Math.min(Integer.MAX_VALUE, outcome.blockedSteps()));
        }
        cropState.setLastAge(CopperStrategy.stageOf(level.getBlockState(pos)));

        // Copper oxidizes in place, so the entry always stays where it is. Once fully oxidized the
        // block stops random-ticking, fails eligibility, and auto-cleanup removes the entry.
        CropRegistry.put(level, pos, cropState);

        if (grew) {
            ci.cancel();
        }
    }

    /**
     * Clock stamp for vanilla's own oxidation.
     *
     * <p>The target here is deliberately <i>not</i> the {@code setBlockAndUpdate} that actually
     * oxidizes the block — that lives in a synthetic lambda, and it also fires far too rarely: vanilla
     * passes its one-in-17.6 gate and then usually changes nothing, because the neighbourhood roll
     * fails or a less-oxidized neighbour blocks it. Stamping only on success would let
     * {@code lastGrowthGameTime} drift and fire catch-up during ordinary loaded play.
     *
     * <p>{@code getNextState} is invoked exactly once in {@code changeOverTime}, inside that gate and
     * before any branch, which makes it an exact marker for "an oxidation opportunity happened" — and
     * an opportunity, not a success, is what {@code copperOxidationIntervalTicks} measures.
     */
    @Inject(method = "changeOverTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/ChangeOverTimeBlock;getNextState(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
    private void everCrops_changeOverTime_opportunity(BlockState state, ServerLevel level, BlockPos pos,
                                                      RandomSource random, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        }
    }
}
