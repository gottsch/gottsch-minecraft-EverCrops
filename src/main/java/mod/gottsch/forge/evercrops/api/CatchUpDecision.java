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
package mod.gottsch.forge.evercrops.api;

/**
 * Pure catch-up timing logic — the "when / how many steps" brain, expressed over a plain
 * {@link CropState} POJO and primitives only (no {@code ServerLevel}/Minecraft types).
 *
 * <p>This is the loader-agnostic engine core: {@link EverCropsApi} reads the live world values
 * ({@code now}, {@code light}, {@code isDay}) and delegates the decision here, so the
 * threshold/light-gate/harvest-guard math can be unit-tested with no world, no mocking and no
 * Minecraft bootstrap.
 *
 * @author Mark Gottschling
 */
public final class CatchUpDecision {

    private CatchUpDecision() {}

    /**
     * Decide whether catch-up growth should fire and, if so, how many growth steps to apply.
     * Mutates the timing fields on {@code state} as a side effect (mirroring the live engine).
     *
     * @param state              tracked state (mutated in place)
     * @param now                current game time (ticks)
     * @param light              current raw brightness at the block
     * @param isDay              whether it is currently day in the level
     * @param avgCallInterval    average ticks between random ticks for one block
     * @param avgGrowthInterval  average vanilla ticks between growth steps for this block
     * @param requiresLight      true if the block needs light &gt;= 9 to grow
     * @return number of growth steps to apply (0 if no growth this tick)
     */
    public static int computeSteps(CropState state, long now, int light, boolean isDay,
                                   int avgCallInterval, int avgGrowthInterval, boolean requiresLight) {
        long callDelta = now - state.getLastCallGameTime();
        if (callDelta <= avgCallInterval * 2L) {
            state.setLastCallGameTime(now).setLastCallLightLevel(light);
            return 0;
        }

        long growthDelta = now - state.getLastGrowthGameTime();
        if (growthDelta <= avgGrowthInterval * 2L) {
            return 0;
        }

        boolean grow = !requiresLight
                || light >= 9
                || (!isDay && (state.getLastCallLightLevel() >= 9
                               || state.getLastGrowthLightLevel() >= 9));
        if (!grow) {
            state.setLastCallGameTime(now).setLastCallLightLevel(light);
            return 0;
        }

        int quotient = (int) Math.floor((double) growthDelta / avgGrowthInterval);
        long remainder = growthDelta % avgGrowthInterval;
        state.setLastGrowthGameTime(now - remainder)
                .setLastGrowthLightLevel(light)
                .setLastCallGameTime(now)
                .setLastCallLightLevel(light);
        return quotient;
    }

    /**
     * Detect an in-place harvest (age/stage regression) and, if found, reset the growth clock so
     * pending catch-up is not re-applied to the replant. Records {@code currentAge} every call;
     * a drop below the previously recorded age is the harvest signal.
     *
     * @param state      tracked state (mutated in place: records age, and on a regression stamps clocks)
     * @param now        current game time (ticks)
     * @param light      current raw brightness at the block
     * @param currentAge the block's current age/stage this tick ({@code -1} = no growth property)
     * @return true if a harvest reset was detected (caller should persist and skip catch-up this tick)
     */
    public static boolean detectInPlaceHarvest(CropState state, long now, int light, int currentAge) {
        int previousAge = state.getLastAge();
        state.setLastAge(currentAge);
        // currentAge < 0 means no resolvable growth property (e.g. bamboo sapling) — never a regression.
        if (previousAge >= 0 && currentAge >= 0 && currentAge < previousAge) {
            state.setLastGrowthGameTime(now)
                    .setLastGrowthLightLevel(light)
                    .setLastCallGameTime(now)
                    .setLastCallLightLevel(light);
            return true;
        }
        return false;
    }

    /**
     * Catch-up step count for an <i>unlit</i> growable that runs its own growth pass and so does
     * not separately stamp {@code lastGrowthGameTime} during loaded play (e.g. Dynamic Trees soil
     * blocks). Operates on the two base {@link CatchUpState} timestamps only — no light gate.
     *
     * <p>Differs from {@link #computeSteps} in the loaded gate: when the chunk was never really
     * unloaded (callDelta within 2&times; the call interval) BOTH timestamps are refreshed, so
     * {@code lastGrowthGameTime} cannot drift stale and fire spurious catch-up during normal play
     * (the crop path keeps {@code lastGrowthGameTime} fresh via its own per-growth hook instead).
     *
     * @param state             tracked state (mutated in place)
     * @param now               current game time (ticks)
     * @param avgCallInterval   average ticks between random ticks for one block
     * @param avgGrowthInterval average ticks between growth steps for this block
     * @return number of growth steps to apply (0 if none this tick)
     */
    public static int computeStepsUnlit(CatchUpState state, long now,
                                        int avgCallInterval, int avgGrowthInterval) {
        long callDelta = now - state.getLastCallGameTime();
        if (callDelta <= avgCallInterval * 2L) {
            // Chunk was never really unloaded; keep BOTH timestamps current so growthDelta reflects
            // only the actual offline period on the next reload, not accumulated loaded idle time.
            state.setLastCallGameTime(now).setLastGrowthGameTime(now);
            return 0;
        }

        long growthDelta = now - state.getLastGrowthGameTime();
        if (growthDelta <= avgGrowthInterval * 2L) {
            // Not enough time since last growth; leave timestamps untouched (between-threshold).
            return 0;
        }

        int quotient = (int) Math.floor((double) growthDelta / avgGrowthInterval);
        long remainder = growthDelta % avgGrowthInterval;
        state.setLastGrowthGameTime(now - remainder).setLastCallGameTime(now);
        return quotient;
    }
}
