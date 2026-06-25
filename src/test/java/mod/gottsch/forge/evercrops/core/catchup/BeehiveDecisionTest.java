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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure beehive rate-learning logic ({@link BeehiveDecision}). These lock the
 * daytime-accumulation, delivery-detection, EMA blending and effective-interval math with no world.
 */
class BeehiveDecisionTest {

    private static BeehiveState fresh() {
        return new BeehiveState();
    }

    /** Run {@code n} active daytime ticks at a fixed honey level (no delivery), advancing the accumulator. */
    private static void tickActiveDaytime(BeehiveState s, int honey, int n) {
        for (int i = 0; i < n; i++) {
            BeehiveDecision.observe(s, honey, true, true);
        }
    }

    // -------------------------------------------------
    // observe — baseline & accumulation
    // -------------------------------------------------

    @Test
    void firstObservation_baselinesHoney_learnsNothing() {
        BeehiveState s = fresh();
        boolean delivered = BeehiveDecision.observe(s, 2, true, true);

        assertFalse(delivered);
        assertEquals(2, s.getLastHoneyLevel(), "honey baselined on first observation");
        assertEquals(0, s.getLearnedIntervalTicks(), "no interval learned yet");
        // First call also counts as one active daytime tick toward the next sample.
        assertEquals(1, s.getDaytimeTicksAccumulated());
    }

    @Test
    void accumulatesOnlyWhenActiveAndDay() {
        BeehiveState s = fresh();
        BeehiveDecision.observe(s, 0, true, true);   // active + day -> +1
        BeehiveDecision.observe(s, 0, false, true);  // night -> no
        BeehiveDecision.observe(s, 0, true, false);  // inactive -> no
        assertEquals(1, s.getDaytimeTicksAccumulated(), "only active daytime ticks accumulate");
    }

    // -------------------------------------------------
    // observe — learning from a real delivery
    // -------------------------------------------------

    @Test
    void honeyRise_learnsInterval_fromAccumulatedDaytimeTicks() {
        BeehiveState s = fresh();
        BeehiveDecision.observe(s, 0, true, true);   // baseline at 0 (acc = 1)
        tickActiveDaytime(s, 0, 1499);               // acc now 1500, still level 0
        boolean delivered = BeehiveDecision.observe(s, 1, true, true); // delivery 0 -> 1

        assertTrue(delivered);
        // acc was 1500 (+1 this tick = 1501) BEFORE consuming; sample = 1501 / 1 level.
        assertEquals(1501, s.getLearnedIntervalTicks(), "first sample seeds the EMA directly");
        assertEquals(0, s.getDaytimeTicksAccumulated(), "accumulator reset after a delivery");
        assertEquals(1, s.getLastHoneyLevel());
    }

    @Test
    void doubleJumpDelivery_dividesSampleByLevelsGained() {
        BeehiveState s = fresh();
        BeehiveDecision.observe(s, 0, true, true);
        tickActiveDaytime(s, 0, 1999);               // acc = 2000
        boolean delivered = BeehiveDecision.observe(s, 2, true, true); // rare +2 jump 0 -> 2

        assertTrue(delivered);
        // acc 2001 over 2 levels -> ~1000 ticks/level.
        assertEquals(1000, s.getLearnedIntervalTicks());
    }

    @Test
    void secondDelivery_blendsViaEma() {
        BeehiveState s = fresh();
        // Seed the EMA at 2000.
        BeehiveDecision.observe(s, 0, true, true);
        tickActiveDaytime(s, 0, 1998);               // acc = 1999
        BeehiveDecision.observe(s, 1, true, true);   // +1 this tick -> sample 2000
        assertEquals(2000, s.getLearnedIntervalTicks());

        // Next cycle observes a faster 1000-tick level.
        tickActiveDaytime(s, 1, 999);                // acc = 1000
        BeehiveDecision.observe(s, 2, true, true);   // delivery 1 -> 2, sample 1000
        // EMA: 2000*0.75 + 1000*0.25 = 1750.
        assertEquals(1750, s.getLearnedIntervalTicks());
    }

    @Test
    void harvestDrop_doesNotLearn_andResetsAccumulator() {
        BeehiveState s = fresh();
        BeehiveDecision.observe(s, 5, true, true);   // baseline at full
        tickActiveDaytime(s, 5, 50);                 // acc grows
        boolean delivered = BeehiveDecision.observe(s, 0, true, true); // player sheared 5 -> 0

        assertFalse(delivered, "a drop is a harvest, not a production sample");
        assertEquals(0, s.getLearnedIntervalTicks(), "nothing learned from a harvest");
        assertEquals(0, s.getDaytimeTicksAccumulated(), "partial sample dropped on harvest");
        assertEquals(0, s.getLastHoneyLevel(), "re-baselined to the new level");
    }

    @Test
    void sampleIsClampedToSaneBand() {
        BeehiveState s = fresh();
        // Only 10 accumulated ticks before a delivery would be absurdly fast -> clamp up to the floor.
        BeehiveDecision.observe(s, 0, true, true);
        tickActiveDaytime(s, 0, 8);                  // acc = 9
        BeehiveDecision.observe(s, 1, true, true);   // sample 9 -> clamped to MIN
        assertEquals(BeehiveDecision.MIN_SAMPLE_TICKS, s.getLearnedIntervalTicks());
    }

    // -------------------------------------------------
    // effectiveInterval
    // -------------------------------------------------

    @Test
    void effectiveInterval_usesFallbackUntilLearned_inflatedByDaytimeFraction() {
        BeehiveState s = fresh();   // nothing learned
        // fallback 1500, daytime fraction 0.5 -> 3000.
        assertEquals(3000, BeehiveDecision.effectiveInterval(s, 1500, 0.5));
    }

    @Test
    void effectiveInterval_prefersLearnedOverFallback() {
        BeehiveState s = fresh().setLearnedIntervalTicks(800);
        // learned 800 wins over fallback 5000; /0.5 -> 1600.
        assertEquals(1600, BeehiveDecision.effectiveInterval(s, 5000, 0.5));
    }

    @Test
    void effectiveInterval_clampsDaytimeFraction_andStaysPositive() {
        BeehiveState s = fresh().setLearnedIntervalTicks(1000);
        // fraction 0 is clamped to 0.05 -> 1000/0.05 = 20000 (not divide-by-zero).
        assertEquals(20000, BeehiveDecision.effectiveInterval(s, 1000, 0.0));
        // fraction 1.0 -> no inflation.
        assertEquals(1000, BeehiveDecision.effectiveInterval(s, 1000, 1.0));
    }
}
