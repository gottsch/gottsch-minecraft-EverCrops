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

import mod.gottsch.forge.evercrops.core.persistence.CropState;
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
    void growthDeltaBelowThreshold_returnsZero_withoutTouchingClocks() {
        // callDelta = 5000 (> 2700) but growthDelta = 10000 (<= 2*7000) -> tick-rate noise, leave as-is.
        CropState s = state(NOW - 5000, NOW - 10_000, 7, 7);
        int steps = CatchUpDecision.computeSteps(s, NOW, 12, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
        assertEquals(NOW - 5000, s.getLastCallGameTime(), "call clock untouched in this branch");
        assertEquals(NOW - 10_000, s.getLastGrowthGameTime(), "growth clock untouched");
    }

    @Test
    void lightGateFails_dayLowLight_returnsZero_refreshesCallOnly() {
        // Offline long enough, but daytime with light < 9 and no stored daylight -> cannot grow.
        CropState s = state(NOW - 5000, NOW - 30_000, 0, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 4, true, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
        assertEquals(NOW, s.getLastCallGameTime(), "call clock refreshed when gate fails");
        assertEquals(4, s.getLastCallLightLevel());
        assertEquals(NOW - 30_000, s.getLastGrowthGameTime(), "growth clock untouched when gate fails");
    }

    @Test
    void lightGatePasses_highLight_computesQuotientAndRemainder() {
        // growthDelta = 30000, avg = 7000 -> 4 steps (28000), remainder 2000.
        CropState s = state(NOW - 5000, NOW - 30_000, 0, 0);
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
        CropState s = state(NOW - 5000, NOW - 30_000, 14, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 3, false, AVG_CALL, AVG_GROWTH, true);

        assertEquals(4, steps, "night-time growth allowed via stored daylight");
    }

    @Test
    void nightNoStoredLight_doesNotGrow() {
        CropState s = state(NOW - 5000, NOW - 30_000, 0, 0);
        int steps = CatchUpDecision.computeSteps(s, NOW, 3, false, AVG_CALL, AVG_GROWTH, true);

        assertEquals(0, steps);
    }

    @Test
    void noLightRequired_growsRegardlessOfDarkness() {
        // Nether wart / cocoa: requiresLight = false, so darkness never blocks growth.
        CropState s = state(NOW - 5000, NOW - 30_000, 0, 0);
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
}
