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

import mod.gottsch.forge.evercrops.core.catchup.ScheduledTickDecision.Result;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the scheduled-tick catch-up math ({@link ScheduledTickDecision}). The single-call
 * cases lock the arithmetic; the timeline tests check the thing that matters — that an egg which was
 * unloaded for a while ends up hatching when it would have if it had stayed loaded.
 *
 * @author Mark Gottschling on 10/15/2026
 */
class ScheduledTickDecisionTest {

    // ------------------------------------------------------------------
    // rewrite
    // ------------------------------------------------------------------

    @Test
    void noTimeLostLeavesTheDelayAlone() {
        assertEquals(new Result(8000, 0), ScheduledTickDecision.rewrite(8000, 0));
    }

    @Test
    void shortAbsenceShortensTheDelay() {
        assertEquals(new Result(5000, 0), ScheduledTickDecision.rewrite(8000, 3000));
    }

    @Test
    void absenceEqualToTheDelayFiresNowWithNothingLeftOver() {
        assertEquals(new Result(0, 0), ScheduledTickDecision.rewrite(8000, 8000));
    }

    @Test
    void longAbsenceFiresNowAndCarriesTheRest() {
        assertEquals(new Result(0, 12000), ScheduledTickDecision.rewrite(8000, 20000));
    }

    @Test
    void tickAlreadyOverdueAtSaveCountsThatToo() {
        // Saved 50 ticks overdue, then away 1000 ticks: 1050 behind.
        assertEquals(new Result(0, 1050), ScheduledTickDecision.rewrite(-50, 1000));
    }

    // ------------------------------------------------------------------
    // spend / effectiveBudget
    // ------------------------------------------------------------------

    @Test
    void noBudgetLeavesTheDelayAlone() {
        assertEquals(new Result(8000, 0), ScheduledTickDecision.spend(8000, 0));
    }

    @Test
    void budgetCoveringTheStageFiresNextTickAndCarriesTheRest() {
        assertEquals(new Result(1, 4000), ScheduledTickDecision.spend(8000, 12000));
    }

    @Test
    void budgetExactlyCoveringTheStageFiresNextTick() {
        assertEquals(new Result(1, 0), ScheduledTickDecision.spend(8000, 8000));
    }

    @Test
    void partialBudgetShortensTheStageAndIsUsedUp() {
        assertEquals(new Result(5000, 0), ScheduledTickDecision.spend(8000, 3000));
    }

    @Test
    void budgetGrowsWhileItWaits() {
        assertEquals(1040, ScheduledTickDecision.effectiveBudget(1000, 500, 540));
    }

    @Test
    void noBudgetStaysZeroWhileItWaits() {
        assertEquals(0, ScheduledTickDecision.effectiveBudget(0, 500, 9000));
    }

    // ------------------------------------------------------------------
    // timelines
    // ------------------------------------------------------------------

    /** A sniffer egg on ordinary ground: three stages of 8000 + up to 300 ticks. */
    private static final int[] EGG = {8120, 8007, 8299};

    @Test
    void eggAwayForTheWholeHatchCatchesUpWithinAFewTicksOfReturn() {
        long[] fire = runWithAbsence(EGG, 100, 100_000);
        assertEquals(100_000, fire[0]);
        assertEquals(100_001, fire[1]);
        assertEquals(100_002, fire[2]);
    }

    @Test
    void eggAwayForPartOfTheHatchKeepsItsOriginalSchedule() {
        long[] faithful = faithfulTimes(EGG);
        // Away from just after placing until halfway through the second stage.
        long[] fire = runWithAbsence(EGG, 10, faithful[0] + 4000);
        assertEquals(faithful[0] + 4000, fire[0]); // first crack was due while away
        assertEquals(faithful[1], fire[1]);        // second crack lands exactly on time
        assertEquals(faithful[2], fire[2]);        // and so does the hatch
    }

    @Test
    void eggAwayButBackBeforeItsNextStageIsExactlyOnTime() {
        long[] faithful = faithfulTimes(EGG);
        long[] fire = runWithAbsence(EGG, 1000, faithful[0] - 500);
        for (int k = 0; k < EGG.length; k++) {
            assertEquals(faithful[k], fire[k]);
        }
    }

    @Test
    void anyAbsenceMatchesStayingLoaded() {
        Random random = new Random(42);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] stages = new int[1 + random.nextInt(3)];
            for (int k = 0; k < stages.length; k++) {
                stages[k] = 1 + random.nextInt(9000);
            }
            long[] faithful = faithfulTimes(stages);
            long end = faithful[stages.length - 1];
            long save = random.nextLong(end + 1);
            long reload = save + random.nextLong(3 * end + 1);

            long[] fire = runWithAbsence(stages, save, reload);
            for (int k = 0; k < stages.length; k++) {
                if (faithful[k] < save) {
                    // Fired while still loaded — untouched.
                    assertEquals(faithful[k], fire[k]);
                } else {
                    // Never earlier than real time, never before the chunk is back, and at most one
                    // tick per stage of drift while overdue stages are worked off.
                    long expected = Math.max(faithful[k], reload);
                    assertTrue(fire[k] >= expected, "stage " + k + " fired early");
                    assertTrue(fire[k] - expected <= stages.length,
                            "stage " + k + " drifted " + (fire[k] - expected) + " ticks");
                    if (faithful[k] > reload + stages.length) {
                        assertEquals(faithful[k], fire[k]);
                    }
                }
            }
        }
    }

    @Test
    void waitingForMobsToLoadDoesNotCostTheEggItsBudget() {
        long[] faithful = faithfulTimes(EGG);
        long reload = faithful[0] + 5000;
        int saved = (int) (faithful[0] - 10);

        Result r = ScheduledTickDecision.rewrite(saved, reload - 10);
        // The tick is due at reload, but only fires 200 ticks later.
        long now = reload + 200;
        long eff = ScheduledTickDecision.effectiveBudget(r.budget(), reload, now);
        Result next = ScheduledTickDecision.spend(EGG[1], eff);
        assertEquals(faithful[1], now + next.delay());
    }

    /** When each stage fires for an egg that never leaves loaded chunks, placed at time 0. */
    private static long[] faithfulTimes(int[] stages) {
        long[] times = new long[stages.length];
        long t = 0;
        for (int k = 0; k < stages.length; k++) {
            t += stages[k];
            times[k] = t;
        }
        return times;
    }

    /**
     * When each stage fires if the chunk is saved and unloaded at {@code save}, and comes back at
     * {@code reload}: the same steps the game takes, with the pending tick's delay rewritten on
     * reload and every later stage's delay spent against the budget.
     */
    private static long[] runWithAbsence(int[] stages, long save, long reload) {
        long[] faithful = faithfulTimes(stages);
        long[] fire = new long[stages.length];
        int k = 0;
        while (k < stages.length && faithful[k] < save) {
            fire[k] = faithful[k];
            k++;
        }
        if (k == stages.length) {
            return fire;
        }

        Result r = ScheduledTickDecision.rewrite((int) (faithful[k] - save), reload - save);
        long now = reload + r.delay();
        long budget = r.budget();
        long stampedAt = reload;
        fire[k++] = now;

        while (k < stages.length) {
            long eff = ScheduledTickDecision.effectiveBudget(budget, stampedAt, now);
            Result spent = ScheduledTickDecision.spend(stages[k], eff);
            budget = spent.budget();
            stampedAt = now;
            now += spent.delay();
            fire[k++] = now;
        }
        return fire;
    }
}
