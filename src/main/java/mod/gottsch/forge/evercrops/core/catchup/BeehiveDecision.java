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

import mod.gottsch.forge.evercrops.api.BeehiveState;

/**
 * Pure honey rate-learning logic for beehive catch-up — the bee analogue of
 * {@link mod.gottsch.forge.evercrops.api.CatchUpDecision}, expressed over a plain {@link BeehiveState}
 * POJO and primitives only (no Minecraft types), so it is unit-testable with no world.
 *
 * <p>Vanilla advances {@code honey_level} only when a nectar bee is released, and only by day in fair
 * weather — there is no fixed tick interval like a crop's. So rather than model that, EverCrops
 * <i>measures</i> it: every loaded tick {@link #observe} accumulates daytime ticks, and each time the
 * honey level actually rises it folds the observed daytime-ticks-per-level into an exponential moving
 * average. {@link #effectiveInterval} then feeds that learned rate (or a cold-start fallback) into the
 * shared {@code computeStepsUnlit} engine for the offline replay.
 *
 * @author Mark Gottschling
 */
public final class BeehiveDecision {

    private BeehiveDecision() {}

    /** EMA weight applied to each fresh interval sample (0..1); higher reacts faster, lower is steadier. */
    public static final double EMA_ALPHA = 0.25;

    /** Clamp each per-level sample to a sane band (ticks) so one odd delivery can't poison the average. */
    public static final long MIN_SAMPLE_TICKS = 200L;
    public static final long MAX_SAMPLE_TICKS = 100_000L;

    /**
     * Per-tick observation for rate-learning. While the hive is actively producing (bees present +
     * flowers known) and it is day, accumulate one tick toward the in-progress sample. When the honey
     * level has risen since last tick — a real vanilla delivery during loaded play — fold the observed
     * daytime-ticks-per-level into the learned-interval EMA and reset the accumulator. A drop in honey
     * (player harvest/reset) is not a production sample; it only re-baselines. Mutates {@code state};
     * call once per loaded tick, BEFORE catch-up, with the honey level read at the top of the tick.
     *
     * @param state        the hive's tracked state (mutated in place)
     * @param currentHoney honey level this tick (0..5)
     * @param isDay        whether it is currently day in the level
     * @param active       whether the hive is currently producing (bees present + a known flower)
     * @return true if a delivery was observed this tick (honey rose)
     */
    public static boolean observe(BeehiveState state, int currentHoney, boolean isDay, boolean active) {
        if (active && isDay) {
            state.setDaytimeTicksAccumulated(state.getDaytimeTicksAccumulated() + 1);
        }

        int last = state.getLastHoneyLevel();
        if (last < 0) {
            // First observation — nothing to compare against yet, just baseline.
            state.setLastHoneyLevel(currentHoney);
            return false;
        }

        if (currentHoney > last) {
            int delta = currentHoney - last;
            long acc = state.getDaytimeTicksAccumulated();
            if (acc > 0) {
                long sample = clampSample(acc / delta);
                long prev = state.getLearnedIntervalTicks();
                long updated = prev <= 0
                        ? sample
                        : Math.round(prev * (1.0 - EMA_ALPHA) + sample * EMA_ALPHA);
                state.setLearnedIntervalTicks(updated);
            }
            state.setDaytimeTicksAccumulated(0);
            state.setLastHoneyLevel(currentHoney);
            return true;
        }

        if (currentHoney < last) {
            // Harvested / reset to a lower level — not a production interval; drop the partial sample.
            state.setDaytimeTicksAccumulated(0);
            state.setLastHoneyLevel(currentHoney);
        }
        return false;
    }

    /** Clamp a raw per-level sample into {@code [MIN_SAMPLE_TICKS, MAX_SAMPLE_TICKS]}. */
    static long clampSample(long sample) {
        return Math.max(MIN_SAMPLE_TICKS, Math.min(MAX_SAMPLE_TICKS, sample));
    }

    /**
     * Effective unlit growth interval for catch-up: the hive's learned per-level interval (or the
     * supplied cold-start fallback when nothing has been learned yet), inflated by the inverse of the
     * daytime fraction. The shared {@code computeStepsUnlit} sees raw elapsed game time, so inflating
     * the interval is what makes it credit only the daytime share of the offline window (honey does
     * not accrue at night), matching vanilla's day-gated delivery.
     *
     * @param state            the hive's tracked state
     * @param fallbackInterval cold-start interval (ticks/level) used until something is learned
     * @param daytimeFraction  fraction of real time that is productive daytime (0..1, e.g. ~0.5)
     * @return the interval (ticks) to pass as {@code avgGrowthInterval} to {@code computeStepsUnlit}
     */
    public static int effectiveInterval(BeehiveState state, int fallbackInterval, double daytimeFraction) {
        long base = state.getLearnedIntervalTicks() > 0 ? state.getLearnedIntervalTicks() : fallbackInterval;
        double frac = Math.max(0.05, Math.min(1.0, daytimeFraction));
        long eff = Math.round(base / frac);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, eff));
    }
}
