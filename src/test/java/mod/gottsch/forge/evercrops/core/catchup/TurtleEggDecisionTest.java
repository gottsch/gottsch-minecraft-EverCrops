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

import mod.gottsch.forge.evercrops.core.catchup.TurtleEggDecision.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure turtle-egg step math ({@link TurtleEggDecision}). These lock the hatch
 * ladder, the terminal-hatch trigger, and the clamping that keeps the disabled-hatch case idempotent.
 */
class TurtleEggDecisionTest {

    @Test
    void noStepsLeavesClusterUntouched() {
        Outcome outcome = TurtleEggDecision.resolve(1, 0, true);
        assertEquals(1, outcome.targetHatch());
        assertFalse(outcome.hatches());
    }

    @Test
    void negativeStepsLeaveClusterUntouched() {
        Outcome outcome = TurtleEggDecision.resolve(2, -3, true);
        assertEquals(2, outcome.targetHatch());
        assertFalse(outcome.hatches());
    }

    @Test
    void oneStepFromFreshEggCracksOnce() {
        Outcome outcome = TurtleEggDecision.resolve(0, 1, true);
        assertEquals(1, outcome.targetHatch());
        assertFalse(outcome.hatches());
    }

    @Test
    void twoStepsFromFreshEggReachFullyCrackedWithoutHatching() {
        Outcome outcome = TurtleEggDecision.resolve(0, 2, true);
        assertEquals(TurtleEggDecision.MAX_HATCH_LEVEL, outcome.targetHatch());
        assertFalse(outcome.hatches());
    }

    @Test
    void threeStepsFromFreshEggHatch() {
        Outcome outcome = TurtleEggDecision.resolve(0, 3, true);
        assertTrue(outcome.hatches());
    }

    /** A very long absence must not compound into more than one hatch. */
    @Test
    void manyStepsFromFreshEggStillHatchExactlyOnce() {
        Outcome outcome = TurtleEggDecision.resolve(0, 500, true);
        assertTrue(outcome.hatches());
        assertEquals(TurtleEggDecision.MAX_HATCH_LEVEL, outcome.targetHatch());
    }

    @Test
    void oneStepFromFullyCrackedHatches() {
        Outcome outcome = TurtleEggDecision.resolve(TurtleEggDecision.MAX_HATCH_LEVEL, 1, true);
        assertTrue(outcome.hatches());
    }

    @Test
    void hatchDisabledClampsAtFullyCracked() {
        Outcome outcome = TurtleEggDecision.resolve(0, 5, false);
        assertEquals(TurtleEggDecision.MAX_HATCH_LEVEL, outcome.targetHatch());
        assertFalse(outcome.hatches());
    }

    /**
     * With hatching disabled a cluster already at the top of the ladder must resolve to exactly
     * where it is, so the caller no-ops instead of re-cracking (and re-playing the sound) forever.
     */
    @Test
    void hatchDisabledIsIdempotentAtFullyCracked() {
        Outcome outcome = TurtleEggDecision.resolve(TurtleEggDecision.MAX_HATCH_LEVEL, 9, false);
        assertEquals(TurtleEggDecision.MAX_HATCH_LEVEL, outcome.targetHatch());
        assertFalse(outcome.hatches());
    }
}
