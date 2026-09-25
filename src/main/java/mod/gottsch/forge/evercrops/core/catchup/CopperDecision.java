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

/**
 * Pure copper-oxidation math — no Minecraft types, so it unit-tests with no world and no bootstrap
 * (the same split as {@link AmethystDecision}).
 *
 * <p>Vanilla oxidizes copper in two stages. About one random tick in 17.6 is an <i>opportunity</i>;
 * the opportunity then scans every copper block within Manhattan distance 4 and succeeds with
 * probability {@code f² · modifier}, where {@code f = (k + 1) / (k + j + 1)}, {@code k} counts
 * neighbours that are <i>more</i> oxidized and {@code j} neighbours at the <i>same</i> stage. Any
 * neighbour that is <i>less</i> oxidized blocks the opportunity outright. An owed catch-up step here is
 * one opportunity; the neighbourhood roll is replayed live by {@link CopperStrategy}.
 *
 * @author Mark Gottschling
 */
public final class CopperDecision {

    /** Vanilla's {@code ChangeOverTimeBlock.SCAN_DISTANCE}: the Manhattan radius of the neighbourhood. */
    public static final int SCAN_DISTANCE = 4;

    /** Signals that an opportunity can never succeed (a zero chance), however many are owed. */
    public static final long NEVER = Long.MAX_VALUE;

    private CopperDecision() {}

    /**
     * Probability that one opportunity oxidizes a block — vanilla's {@code f² · chanceModifier}.
     *
     * @param moreOxidized   copper neighbours within the scan that are further along ({@code k})
     * @param sameAge        copper neighbours within the scan at the same stage ({@code j})
     * @param chanceModifier the block's own {@code getChanceModifier()}: 0.75 when unaffected, else 1.0
     */
    public static float advanceChance(int moreOxidized, int sameAge, float chanceModifier) {
        float f = (float) (moreOxidized + 1) / (float) (moreOxidized + sameAge + 1);
        return f * f * chanceModifier;
    }

    /**
     * True when a less-oxidized neighbour exists: vanilla returns before the roll, so no number of
     * opportunities can advance this block until that neighbour catches up.
     */
    public static boolean isBlockedByNeighbour(int lessOxidized) {
        return lessOxidized > 0;
    }

    /**
     * How many opportunities it takes to land the next success, including the successful one.
     *
     * <p>Replaying opportunities one {@code nextFloat()} at a time is exactly vanilla, but a copper
     * block buried in same-stage copper succeeds about once in 16,600 tries, and a long absence can owe
     * thousands of tries per block. The count of tries up to the first success of a repeated
     * {@code p}-chance roll is geometrically distributed, so a single uniform draw samples it directly,
     * with the same distribution and in constant time: {@code ceil(ln(u) / ln(1 - p))}.
     *
     * @param chance  per-opportunity success probability, from {@link #advanceChance}
     * @param uniform a uniform draw in {@code (0, 1]} — pass {@code 1 - random.nextFloat()}, never 0
     * @return opportunities consumed (at least 1), or {@link #NEVER} when {@code chance <= 0}
     */
    public static long opportunitiesUntilAdvance(float chance, double uniform) {
        if (chance <= 0f) {
            return NEVER;
        }
        if (chance >= 1f || uniform >= 1.0) {
            return 1;
        }
        double tries = Math.ceil(Math.log(uniform) / Math.log1p(-chance));
        if (tries >= NEVER) {
            return NEVER;
        }
        return Math.max(1L, (long) tries);
    }

    /** Ticks per expected successful oxidation at this chance, for {@code /evercrops inspect}. */
    public static long expectedIntervalTicks(float chance, int opportunityInterval) {
        if (chance <= 0f) {
            return NEVER;
        }
        return Math.round(opportunityInterval / (double) chance);
    }
}
