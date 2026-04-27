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
package mod.gottsch.forge.evercrops.core.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Shared timing/threshold logic used by the single-AGE-property catch-up mixins
 * (sweet berry bush, nether wart, cocoa, etc.).
 *
 * The original CropBlockMixin / StemBlockMixin implementations inline this
 * logic directly. The phase-1 expansion mixins call into this helper so the
 * three near-identical implementations stay DRY.
 *
 * @author Mark Gottschling on 4/26/2026
 */
public final class CropCatchUp {

    public static final int AVG_CALL_TICK_INTERVAL = 1350;

    private CropCatchUp() {}

    /**
     * Decide whether catch-up growth should fire on this random tick, and if so
     * how many growth steps to apply. Updates the timing fields on
     * {@code cropState} as a side effect; the caller is responsible for
     * performing the actual block updates and persisting via
     * {@link CropRegistry#put}.
     *
     * Behaviour mirrors the original CropBlockMixin:
     *  - if call delta is below threshold, just refresh the call stamps
     *  - if growth delta is below threshold, leave timestamps untouched
     *  - otherwise check light gating (when required) and compute the number of
     *    elapsed growth intervals to catch up
     *
     * @param level                level the crop lives in
     * @param pos                  block position
     * @param cropState            tracked state (mutated in place)
     * @param avgGrowthInterval    average vanilla ticks between growth steps for this block
     * @param requiresLight        true if the block needs light &gt;= 9 to grow
     * @return number of growth steps to apply (0 if no growth this tick)
     */
    public static int beginCatchUp(ServerLevel level, BlockPos pos, CropState cropState,
                                   int avgGrowthInterval, boolean requiresLight) {
        long now = level.getGameTime();
        int light = level.getRawBrightness(pos, 0);

        long callDelta = now - cropState.getLastCallGameTime();
        if (callDelta <= AVG_CALL_TICK_INTERVAL * 2L) {
            cropState.setLastCallGameTime(now).setLastCallLightLevel(light);
            return 0;
        }

        long growthDelta = now - cropState.getLastGrowthGameTime();
        if (growthDelta <= avgGrowthInterval * 2L) {
            // mirror original mixins: leave timestamps untouched between thresholds
            return 0;
        }

        boolean grow = !requiresLight
                || light >= 9
                || (!level.isDay() && (cropState.getLastCallLightLevel() >= 9
                                       || cropState.getLastGrowthLightLevel() >= 9));
        if (!grow) {
            cropState.setLastCallGameTime(now).setLastCallLightLevel(light);
            return 0;
        }

        int quotient = (int) Math.floor((double) growthDelta / avgGrowthInterval);
        long remainder = growthDelta % avgGrowthInterval;
        cropState.setLastGrowthGameTime(now - remainder)
                .setLastGrowthLightLevel(light)
                .setLastCallGameTime(now)
                .setLastCallLightLevel(light);
        return quotient;
    }

    /** Build a fresh CropState stamped with the current game time and light level. */
    public static CropState createState(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        int light = level.getRawBrightness(pos, 0);
        return new CropState()
                .setLastCallGameTime(now)
                .setLastGrowthGameTime(now)
                .setLastCallLightLevel(light)
                .setLastGrowthLightLevel(light);
    }
}
