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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure catch-up logic. These lock the in-place family's timing/threshold/
 * light-gate/harvest-guard behaviour before the §B strategy refactor, with no Minecraft world.
 */
class CatchUpDecisionTest {

    // Production tunings for a standard crop (wheat): see ProjectKnowledge §8a.
    private static final int AVG_CALL = 1350;
    private static final int AVG_GROWTH = 7000;
    private static final long NOW = 100_000L;

    private static CropState state(long lastCall, long lastGrowth, int lastCallLight, int lastGrowthLight) {
        return new CropState()
                .setLastCallGameTime(lastCall)
                .setLastGrowthGameTime(lastGrowth)
                .setLastCallLightLevel(lastCallLight)
                .setLastGrowthLightLevel(lastGrowthLight);
    }

    // -------------------------------------------------
    // computeSteps
    // -------------------------------------------------

    @Test
    void recentCall_returnsZero_andRefreshesCallClock() {
        // callDelta = 100 <= 2*1350 -> block is ticking normally, no catch-up.
        CropState s = state(NOW - 100, NOW - 999_999, 5, 5);
        int steps = CatchUpDecision.computeSteps(s, NOW, 12, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed to now");
        assertEquals(12, s.getLastCallLightLevel(), "call light refreshed");
        assertEquals(NOW - 999_999, s.getLastGrowthGameTime(), "growth clock untouched");
    }

    @Test
    void growthDeltaBelowThreshold_returnsZero_refreshesCallClockOnly() {
        // callDelta = 5000 (> 2700) but growthDelta = 10000 (<= 2*7000) -> tick-rate noise.
        CropState s = state(NOW - 5000, NOW - 10_000, 7, 7);
        int steps = CatchUpDecision.computeSteps(s, NOW, 12, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
        // Refreshing the call clock here is what stops the next ordinary tick landing back in this
        // branch — see the loaded-play tests at the end of this class.
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed");
        assertEquals(12, s.getLastCallLightLevel(), "call light refreshed");
        assertEquals(NOW - 10_000, s.getLastGrowthGameTime(), "growth clock untouched");
    }

    @Test
    void shortAbsence_isNotCreditedYet_butKeepsGrowthClock() {
        // Gone 5000 ticks (> 2*1350 but <= 2*7000), last grew 30000 ago. A gap that short also
        // happens in loaded play, so it is not treated as an absence — and because the growth clock
        // is kept, the time still counts toward the next genuine absence.
        CropState s = state(NOW - 5000, NOW - 30_000, 15, 15);
        int steps = CatchUpDecision.computeSteps(s, NOW, 15, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed");
        assertEquals(NOW - 30_000, s.getLastGrowthGameTime(), "growth clock kept for a later absence");
    }

    @Test
    void lightGateFails_dayLowLight_returnsZero_refreshesCallOnly() {
        // Offline long enough, but daytime with light < 9 and no stored daylight -> cannot grow.
        CropState s = state(NOW - 20_000, NOW - 30_000, 0, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 4, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed when gate fails");
        assertEquals(4, s.getLastCallLightLevel());
        assertEquals(NOW - 30_000, s.getLastGrowthGameTime(), "growth clock untouched when gate fails");
    }

    @Test
    void lightGatePasses_highLight_computesQuotientAndRemainder() {
        // growthDelta = 30000, avg = 7000 -> 4 steps (28000), remainder 2000.
        CropState s = state(NOW - 20_000, NOW - 30_000, 0, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 15, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(4, steps);
        assertEquals(NOW - 2000, s.getLastGrowthGameTime(), "growth clock set to now - remainder");
        assertEquals(15, s.getLastGrowthLightLevel());
        assertEquals(NOW, s.getLastCallGameTime());
        assertEquals(15, s.getLastCallLightLevel());
    }

    @Test
    void nightFallback_usesStoredDaylight_toGrow() {
        // Night, current light < 9, but the crop saw daylight (stored call light >= 9) -> still grows.
        CropState s = state(NOW - 20_000, NOW - 30_000, 14, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 3, false, AVG_CALL, AVG_GROWTH, true);

        assertEquals(4, steps, "night-time growth allowed via stored daylight");
    }

    @Test
    void nightNoStoredLight_doesNotGrow() {
        CropState s = state(NOW - 20_000, NOW - 30_000, 0, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 3, false, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
    }

    @Test
    void noLightRequired_growsRegardlessOfDarkness() {
        // Nether wart / cocoa: requiresLight = false, so darkness never blocks growth.
        CropState s = state(NOW - 20_000, NOW - 30_000, 0, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 0, true, AVG_CALL, AVG_GROWTH, false);

        assertEquals(4, steps);
    }

    // -------------------------------------------------
    // detectInPlaceHarvest
    // -------------------------------------------------

    @Test
    void firstObservation_doesNotTrigger_butRecordsAge() {
        CropState s = state(NOW - 5000, NOW - 30_000, 12, 12); // lastAge defaults to -1
        boolean harvested = CatchUpDecision.detectInPlaceHarvest(s, NOW, 12, 7);

        assertFalse(harvested, "first observation can never be a regression");
        assertEquals(7, s.getLastAge());
        assertEquals(NOW - 30_000, s.getLastGrowthGameTime(), "clocks untouched on first observation");
    }

    @Test
    void ageRegression_triggers_andStampsClocks() {
        CropState s = state(NOW - 5000, NOW - 30_000, 12, 12).setLastAge(7);
        boolean harvested = CatchUpDecision.detectInPlaceHarvest(s, NOW, 9, 0);

        assertTrue(harvested, "age dropped 7 -> 0 is an in-place harvest");
        assertEquals(0, s.getLastAge());
        assertEquals(NOW, s.getLastGrowthGameTime(), "growth clock spent");
        assertEquals(NOW, s.getLastCallGameTime(), "call clock spent");
        assertEquals(9, s.getLastGrowthLightLevel());
    }

    @Test
    void ageIncrease_doesNotTrigger() {
        CropState s = state(NOW - 5000, NOW - 30_000, 12, 12).setLastAge(2);
        boolean harvested = CatchUpDecision.detectInPlaceHarvest(s, NOW, 12, 3);

        assertFalse(harvested);
        assertEquals(3, s.getLastAge());
        assertEquals(NOW - 30_000, s.getLastGrowthGameTime());
    }

    @Test
    void sameAge_doesNotTrigger() {
        CropState s = state(NOW - 5000, NOW - 30_000, 12, 12).setLastAge(3);
        assertFalse(CatchUpDecision.detectInPlaceHarvest(s, NOW, 12, 3));
    }

    @Test
    void negativeCurrentAge_neverTriggers() {
        // Property-less growable (bamboo sapling): currentAge = -1 must never be a regression.
        CropState s = state(NOW - 5000, NOW - 30_000, 12, 12).setLastAge(5);
        boolean harvested = CatchUpDecision.detectInPlaceHarvest(s, NOW, 12, -1);

        assertFalse(harvested);
        assertEquals(-1, s.getLastAge());
        assertEquals(NOW - 30_000, s.getLastGrowthGameTime(), "clocks untouched");
    }

    // -------------------------------------------------
    // computeStepsUnlit (Dynamic Trees / unlit growables)
    // -------------------------------------------------

    private static CatchUpState unlit(long lastCall, long lastGrowth) {
        return new CatchUpState()
                .setLastCallGameTime(lastCall)
                .setLastGrowthGameTime(lastGrowth);
    }

    @Test
    void unlit_recentCall_returnsZero_andRefreshesBOTHClocks() {
        // The key difference from computeSteps: the loaded gate stamps lastGrowth too, so it
        // can't drift stale and fire spurious catch-up during normal loaded play.
        CatchUpState s = unlit(NOW - 100, NOW - 999_999);
        int steps = CatchUpDecision.computeStepsUnlit(s, NOW, AVG_CALL, AVG_GROWTH);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed");
        assertEquals(NOW, s.getLastGrowthGameTime(), "growth clock ALSO refreshed (unlit behaviour)");
    }

    @Test
    void unlit_growthDeltaBelowThreshold_returnsZero_refreshesCallClockOnly() {
        // callDelta = 5000 (> 2700) but growthDelta = 10000 (<= 2*7000) -> between thresholds.
        CatchUpState s = unlit(NOW - 5000, NOW - 10_000);
        int steps = CatchUpDecision.computeStepsUnlit(s, NOW, AVG_CALL, AVG_GROWTH);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed, so the next call is 'loaded'");
        assertEquals(NOW - 10_000, s.getLastGrowthGameTime(), "growth clock untouched");
    }

    @Test
    void unlit_shortAbsence_isNotCredited() {
        // Gone 5000 ticks, growth clock 30000 old: too short a gap to be an absence.
        CatchUpState s = unlit(NOW - 5000, NOW - 30_000);
        int steps = CatchUpDecision.computeStepsUnlit(s, NOW, AVG_CALL, AVG_GROWTH);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime());
    }

    @Test
    void unlit_offline_computesQuotientAndRemainder_noLightGate() {
        // growthDelta = 30000, avg = 7000 -> 4 steps (28000), remainder 2000. No light gate at all.
        CatchUpState s = unlit(NOW - 20_000, NOW - 30_000);
        int steps = CatchUpDecision.computeStepsUnlit(s, NOW, AVG_CALL, AVG_GROWTH);

        assertEquals(4, steps);
        assertEquals(NOW - 2000, s.getLastGrowthGameTime(), "growth clock set to now - remainder");
        assertEquals(NOW, s.getLastCallGameTime());
    }

    // -------------------------------------------------
    // Loaded play must never earn catch-up
    // -------------------------------------------------
    //
    // A block's random ticks are roughly Poisson with a mean of ~1365 ticks, so a gap longer than
    // 2 * AVG_CALL happens on about one tick in seven while the chunk is loaded the whole time. The
    // "growth too recent" branch then returns without refreshing lastCallGameTime, so every
    // following ordinary tick still looks like a long gap. Once the growth clock ages past
    // 2 * avgGrowthInterval, the engine grants "offline" steps to a block that never unloaded.
    // A simulation put the excess at about a third more growth attempts than vanilla.
    //
    // These drive the engine through one such timeline with no vanilla growth stamp in between,
    // which is realistic: vanilla growth is rare, so long stretches without a stamp are common.
    // Fixed in 4.3.0 by refreshing the call clock in that branch and requiring the gap itself to
    // exceed 2 * the growth interval (CatchUpDecision.isTooShortForAnAbsence). Every one of these
    // granted 2 steps before the fix.

    /**
     * Random-tick times for a block that stays loaded throughout: ordinary 1300-tick gaps, except
     * for a single 2800-tick gap (just over 2 * AVG_CALL) early on. Runs to 20,000 ticks, past
     * 2 * the growth interval of every category tested below.
     */
    private static long[] loadedTimelineWithOneLongGap() {
        java.util.List<Long> ticks = new java.util.ArrayList<>();
        ticks.add(1_300L);
        long t = 1_300L + 2_800L;   // the one long gap
        while (t <= 20_000L) {
            ticks.add(t);
            t += 1_300L;
        }
        return ticks.stream().mapToLong(Long::longValue).toArray();
    }

    @Test
    void loadedBlock_oneLongTickGap_neverEarnsCatchUp_noLightRequired() {
        // Budding amethyst's pace (6750); copper and amethyst run the lit engine with no light gate.
        int interval = 6_750;
        CropState s = state(0, 0, 15, 15);
        int total = 0;
        for (long now : loadedTimelineWithOneLongGap()) {
            total += CatchUpDecision.computeSteps(s, now, 15, true, AVG_CALL, interval, false);
        }
        assertEquals(0, total, "a block that never unloaded must not be granted catch-up steps");
    }

    @Test
    void loadedBlock_oneLongTickGap_neverEarnsCatchUp_lightRequired() {
        // Wheat in full daylight: the light gate passes, so it cannot mask the leak.
        CropState s = state(0, 0, 15, 15);
        int total = 0;
        for (long now : loadedTimelineWithOneLongGap()) {
            total += CatchUpDecision.computeSteps(s, now, 15, true, AVG_CALL, AVG_GROWTH, true);
        }
        assertEquals(0, total, "a block that never unloaded must not be granted catch-up steps");
    }

    @Test
    void unlit_loadedBlock_oneLongTickGap_neverEarnsCatchUp() {
        // The unlit engine refreshes both clocks on a normal tick, but its "between thresholds" branch
        // has the same gap. (Beehives call it every server tick, so in practice a hive never sees a
        // long gap — this pins the engine, not the hives.)
        CatchUpState s = unlit(0, 0);
        int total = 0;
        for (long now : loadedTimelineWithOneLongGap()) {
            total += CatchUpDecision.computeStepsUnlit(s, now, AVG_CALL, 6_750);
        }
        assertEquals(0, total, "a block that never unloaded must not be granted catch-up steps");
    }
}
