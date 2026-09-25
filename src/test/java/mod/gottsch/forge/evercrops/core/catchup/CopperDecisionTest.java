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

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure copper oxidation math ({@link CopperDecision}). These lock vanilla's
 * {@code f² · modifier} chance across the whole neighbourhood range, the less-oxidized-neighbour block,
 * and that the constant-time opportunity sampler matches replaying vanilla's rolls one at a time.
 */
class CopperDecisionTest {

    /** Opportunity interval: vanilla's ~1350-tick random tick divided by its 0.05688889 gate. */
    private static final int INTERVAL = 23_730;

    /** Every position within Manhattan distance 4, excluding the block itself. */
    private static final int FULL_NEIGHBOURHOOD = 128;

    private static final float UNAFFECTED = 0.75f;
    private static final float LATER_STAGE = 1.0f;

    @Test
    void isolatedBlockChanceIsJustTheModifier() {
        assertEquals(0.75f, CopperDecision.advanceChance(0, 0, UNAFFECTED), 1e-6f);
        assertEquals(1.0f, CopperDecision.advanceChance(0, 0, LATER_STAGE), 1e-6f);
    }

    @Test
    void fullyEnclosedBlockIsRoughlySixteenThousandTimesSlower() {
        float enclosed = CopperDecision.advanceChance(0, FULL_NEIGHBOURHOOD, UNAFFECTED);
        assertEquals(0.75f / (129f * 129f), enclosed, 1e-9f);

        float isolated = CopperDecision.advanceChance(0, 0, UNAFFECTED);
        assertEquals(129.0 * 129.0, isolated / enclosed, 1.0);
    }

    @Test
    void moreOxidizedNeighboursSpeedOxidationUp() {
        float withoutHelp = CopperDecision.advanceChance(0, 20, LATER_STAGE);
        float withHelp = CopperDecision.advanceChance(10, 20, LATER_STAGE);
        assertTrue(withHelp > withoutHelp, "more-oxidized neighbours push f back toward 1");
    }

    @Test
    void chanceFallsMonotonicallyAsSameStageNeighboursAreAdded() {
        float previous = Float.MAX_VALUE;
        for (int same = 0; same <= FULL_NEIGHBOURHOOD; same++) {
            float chance = CopperDecision.advanceChance(3, same, LATER_STAGE);
            assertTrue(chance < previous || same == 0, "chance must fall at sameAge=" + same);
            previous = chance;
        }
    }

    @Test
    void chanceAlwaysStaysAProbability() {
        for (int more = 0; more <= FULL_NEIGHBOURHOOD; more++) {
            for (int same = 0; more + same <= FULL_NEIGHBOURHOOD; same++) {
                for (float modifier : new float[] {UNAFFECTED, LATER_STAGE}) {
                    float chance = CopperDecision.advanceChance(more, same, modifier);
                    assertTrue(chance > 0f && chance <= 1f, "k=" + more + " j=" + same);
                }
            }
        }
    }

    @Test
    void anyLessOxidizedNeighbourBlocksWhateverTheRest() {
        assertTrue(CopperDecision.isBlockedByNeighbour(1));
        assertTrue(CopperDecision.isBlockedByNeighbour(FULL_NEIGHBOURHOOD));
        assertFalse(CopperDecision.isBlockedByNeighbour(0));
    }

    @Test
    void expectedIntervalMatchesThePlanTable() {
        // Isolated unaffected ~31,640; isolated later stage = the opportunity interval itself.
        assertEquals(31_640, CopperDecision.expectedIntervalTicks(0.75f, INTERVAL), 1);
        assertEquals(INTERVAL, CopperDecision.expectedIntervalTicks(1.0f, INTERVAL));

        float enclosed = CopperDecision.advanceChance(0, FULL_NEIGHBOURHOOD, LATER_STAGE);
        long ticks = CopperDecision.expectedIntervalTicks(enclosed, INTERVAL);
        assertTrue(ticks > 390_000_000L && ticks < 400_000_000L, "enclosed ~3.9e8, was " + ticks);
    }

    @Test
    void certainOrLuckiestDrawTakesOneOpportunity() {
        assertEquals(1, CopperDecision.opportunitiesUntilAdvance(1.0f, 0.5));
        assertEquals(1, CopperDecision.opportunitiesUntilAdvance(0.3f, 1.0));
    }

    @Test
    void zeroChanceNeverAdvances() {
        assertEquals(CopperDecision.NEVER, CopperDecision.opportunitiesUntilAdvance(0f, 0.5));
        assertEquals(CopperDecision.NEVER, CopperDecision.expectedIntervalTicks(0f, INTERVAL));
    }

    @Test
    void unluckierDrawsNeverTakeFewerOpportunities() {
        long previous = 0;
        for (double u = 1.0; u > 0.001; u -= 0.01) {
            long tries = CopperDecision.opportunitiesUntilAdvance(0.05f, u);
            assertTrue(tries >= previous, "must be monotone in the draw, u=" + u);
            previous = tries;
        }
    }

    /**
     * The sampler must be a drop-in replacement for vanilla's roll-by-roll replay: over many trials
     * both the mean and the spread of "opportunities until the first success" must agree.
     */
    @Test
    void samplerMatchesReplayingEveryRoll() {
        float chance = 0.08f;
        int trials = 40_000;
        Random random = new Random(1234L);

        double sampledSum = 0;
        double replayedSum = 0;
        int sampledWithinTen = 0;
        int replayedWithinTen = 0;
        for (int i = 0; i < trials; i++) {
            long sampled = CopperDecision.opportunitiesUntilAdvance(chance, 1.0 - random.nextFloat());
            long replayed = 1;
            while (!(random.nextFloat() < chance)) {
                replayed++;
            }
            sampledSum += sampled;
            replayedSum += replayed;
            if (sampled <= 10) sampledWithinTen++;
            if (replayed <= 10) replayedWithinTen++;
        }

        double expectedMean = 1.0 / chance;
        assertEquals(expectedMean, sampledSum / trials, 0.25);
        assertEquals(expectedMean, replayedSum / trials, 0.25);
        // P(N <= 10) = 1 - 0.92^10 ~ 0.566 for both.
        assertEquals(replayedWithinTen / (double) trials, sampledWithinTen / (double) trials, 0.015);
    }
}
