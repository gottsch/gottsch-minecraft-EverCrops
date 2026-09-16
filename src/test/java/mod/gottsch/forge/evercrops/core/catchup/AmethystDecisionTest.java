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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure amethyst step math ({@link AmethystDecision}). These lock the bud ladder,
 * the blocked-face rules, and — most importantly — that a step landing on a blocked face is spent
 * rather than retried, which is what keeps a walled-in geode growing at vanilla's real pace.
 */
class AmethystDecisionTest {

    /** A picker that always names the same face. */
    private static IntSupplier always(int face) {
        return () -> face;
    }

    /** A picker that walks the six faces in order, over and over — vanilla's choice, made fair. */
    private static IntSupplier cycling() {
        AtomicInteger next = new AtomicInteger();
        return () -> next.getAndIncrement() % AmethystDecision.FACES;
    }

    private static int[] allOpen() {
        return new int[] {0, 0, 0, 0, 0, 0};
    }

    @Test
    void noStepsLeavesEveryFaceUntouched() {
        int[] before = {0, 1, 2, -1, 4, 0};
        assertArrayEquals(before, AmethystDecision.resolve(before, 0, always(0)));
        assertArrayEquals(before, AmethystDecision.resolve(before, -5, always(0)));
    }

    @Test
    void doesNotMutateTheInputArray() {
        int[] before = allOpen();
        AmethystDecision.resolve(before, 12, cycling());
        assertArrayEquals(allOpen(), before);
    }

    @Test
    void oneFaceWalksTheWholeLadderThenStops() {
        int[] stages = {0, -1, -1, -1, -1, -1};
        assertArrayEquals(new int[] {1, -1, -1, -1, -1, -1}, AmethystDecision.resolve(stages, 1, always(0)));
        assertArrayEquals(new int[] {2, -1, -1, -1, -1, -1}, AmethystDecision.resolve(stages, 2, always(0)));
        assertArrayEquals(new int[] {3, -1, -1, -1, -1, -1}, AmethystDecision.resolve(stages, 3, always(0)));
        assertArrayEquals(new int[] {4, -1, -1, -1, -1, -1}, AmethystDecision.resolve(stages, 4, always(0)));
        // A fifth step cannot push past a full cluster.
        assertArrayEquals(new int[] {4, -1, -1, -1, -1, -1}, AmethystDecision.resolve(stages, 5, always(0)));
    }

    @Test
    void everyOpenFaceMaturesGivenEnoughSteps() {
        int[] result = AmethystDecision.resolve(allOpen(), 24, cycling());
        assertArrayEquals(new int[] {4, 4, 4, 4, 4, 4}, result);
        assertTrue(AmethystDecision.isTerminal(result));
    }

    @Test
    void blockedFacesNeverGrow() {
        int[] stages = {-1, -1, -1, -1, -1, -1};
        assertArrayEquals(stages, AmethystDecision.resolve(stages, 1000, cycling()));
    }

    @Test
    void aStepLandingOnABlockedFaceIsConsumed() {
        // Face 0 is walled in; face 1 is open. Two steps, both aimed at the blocked face, must leave
        // the open face untouched — the wasted opportunity is not handed to a face that could use it.
        int[] stages = {-1, 0, -1, -1, -1, -1};
        assertArrayEquals(stages, AmethystDecision.resolve(stages, 2, always(0)));
    }

    @Test
    void blockedFacesDoNotSlowDownTheOpenOnes() {
        // Easy to get backwards. Vanilla picks a direction at random every opportunity, and that pick
        // does not care which faces are walled in — so a given face is chosen one time in six either
        // way. Walling a block off does NOT slow the surviving faces; it only means fewer faces bear
        // buds at all. Both blocks below therefore reach the same rung on face 0 at every step count.
        int[] oneOpen = {0, -1, -1, -1, -1, -1};
        assertEquals(4, AmethystDecision.resolve(oneOpen, 24, cycling())[0]);
        assertEquals(4, AmethystDecision.resolve(allOpen(), 24, cycling())[0]);
        assertEquals(3, AmethystDecision.resolve(oneOpen, 18, cycling())[0]);
        assertEquals(3, AmethystDecision.resolve(allOpen(), 18, cycling())[0]);
        // What the walled block loses is total output: one face's worth of buds instead of six.
        assertArrayEquals(new int[] {4, -1, -1, -1, -1, -1}, AmethystDecision.resolve(oneOpen, 24, cycling()));
        assertArrayEquals(new int[] {4, 4, 4, 4, 4, 4}, AmethystDecision.resolve(allOpen(), 24, cycling()));
    }

    @Test
    void partiallyGrownFacesResumeWhereTheyLeftOff() {
        int[] stages = {3, 1, -1, 4, 0, 2};
        assertArrayEquals(new int[] {4, 2, -1, 4, 1, 3}, AmethystDecision.resolve(stages, 6, cycling()));
    }

    @Test
    void aTerminalBlockIsIdempotentUnderAnyNumberOfSteps() {
        int[] stages = {4, 4, -1, 4, -1, 4};
        assertTrue(AmethystDecision.isTerminal(stages));
        assertArrayEquals(stages, AmethystDecision.resolve(stages, 1_000_000, cycling()));
    }

    @Test
    void isTerminalRecognisesAnyRemainingRoomToGrow() {
        assertFalse(AmethystDecision.isTerminal(new int[] {4, 4, 4, 4, 4, 0}));
        assertFalse(AmethystDecision.isTerminal(new int[] {-1, -1, -1, -1, -1, 3}));
        assertTrue(AmethystDecision.isTerminal(new int[] {-1, -1, -1, -1, -1, -1}));
    }

    @Test
    void stepsAreCappedButTheCapNeverCostsRealGrowth() {
        // Far more owed steps than the cap, yet every open face still matures: the early terminal
        // exit does the real work, and STEP_LIMIT only bounds a pathological absence.
        int[] result = AmethystDecision.resolve(allOpen(), Integer.MAX_VALUE, cycling());
        assertArrayEquals(new int[] {4, 4, 4, 4, 4, 4}, result);
    }
}
