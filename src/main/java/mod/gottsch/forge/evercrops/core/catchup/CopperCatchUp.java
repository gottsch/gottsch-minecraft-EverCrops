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
package mod.gottsch.forge.evercrops.core.catchup;

import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The whole copper random-tick hook, shared by both loaders. Each loader's mixin only decides <i>where</i>
 * to call it from, because the two Mixin versions allow different targets: NeoForge 1.21.1 injects into
 * the {@code ChangeOverTimeBlock} interface itself, while Forge 1.20.1's Mixin rejects injectors in
 * interfaces and hooks the three copper classes' {@code randomTick} instead.
 *
<p><b>The growth clock is stamped on every random tick, not on vanilla's oxidation attempts.</b>
 * That starts the offline credit from the moment the chunk actually unloaded, rather than from the
 * last attempt, which may have been tens of thousands of ticks earlier. (It was first added to dodge
 * an engine bug where an ordinary long gap between random ticks was misread as an unload, handing
 * loaded blocks about a third more attempts than vanilla. The engine itself is fixed as of 4.3.0 —
 * see {@code CatchUpDecision.isTooShortForAnAbsence} — so the stamp is now about accuracy only.)
 *
 * @author Mark Gottschling
 */
public final class CopperCatchUp {

    private CopperCatchUp() {}

    /**
     * Run from the start of a copper block's random tick.
     *
     * @return true if catch-up oxidized the block, in which case vanilla's own tick should be cancelled
     */
    public static boolean onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!CropEligibility.isTrackingEnabled(state)) return false;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return false;
        }
        CropState cropState = existing.get();

        // Opportunities a less-weathered neighbour blocked on earlier ticks are banked in their own
        // field, since the clock is re-stamped every tick. The bank is only good for the stage it was
        // earned at, recorded in lastAge (copper has no age property, so the field is otherwise
        // unused). If the block is anywhere else now — a player scraped it back with an axe, say — the
        // bank is dropped, so scraped copper does not snap straight back to the stage it was just
        // taken from.
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
        if (owed <= 0) {
            // An ordinary loaded tick: the clock stamp described above. (When catch-up did run, the
            // engine has already set the clock, keeping the part-interval remainder.)
            cropState.setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
        }
        cropState.setLastAge(CopperStrategy.stageOf(level.getBlockState(pos)));

        // Copper oxidizes in place, so the entry always stays where it is. Once fully oxidized the
        // block stops random-ticking, fails eligibility, and auto-cleanup removes the entry.
        CropRegistry.put(level, pos, cropState);
        return grew;
    }
}
