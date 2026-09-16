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

import java.util.function.IntSupplier;

/**
 * Pure "how far do these owed steps carry a budding amethyst's six faces?" math — no Minecraft
 * types, so it unit-tests with no world and no bootstrap (the same split as {@link TurtleEggDecision}).
 *
 * <p>Vanilla advances amethyst one <i>opportunity</i> at a time: roughly one random tick in five it
 * picks one of the six directions at random and advances whatever is on that face by one rung of
 * {@code empty -> small -> medium -> large -> cluster}. So an owed step here is one opportunity, and
 * replaying it means picking a direction and applying the ladder there.
 *
 * <p><b>A step that lands on a blocked face is consumed, not skipped.</b> That is the whole reason
 * this is faithful: vanilla wastes those ticks too. A budding block walled in on five sides really
 * does grow about six times slower than an exposed one, and skipping blocked picks would silently
 * erase that difference.
 *
 * @author Mark Gottschling
 */
public final class AmethystDecision {

    /** A face nothing can ever grow on: a solid neighbour, flowing water, or a bud facing elsewhere. */
    public static final int BLOCKED = -1;
    /** A face that is air or a full water source — vanilla's {@code canClusterGrowAtState}. */
    public static final int EMPTY = 0;
    /** A fully-grown amethyst cluster: the end of the ladder. */
    public static final int CLUSTER = 4;

    /** Number of faces on a block; the direction picker must stay within {@code 0 .. FACES - 1}. */
    public static final int FACES = 6;

    /**
     * Hard bound on replayed opportunities. Six faces times four rungs is 24 useful advances, so
     * anything past that is provably wasted; this is only a guard against a pathological absence,
     * and {@link #isTerminal} normally ends the loop far sooner.
     */
    public static final int STEP_LIMIT = 200;

    private AmethystDecision() {}

    /**
     * Where {@code steps} owed opportunities leave a block's six faces.
     *
     * @param faceStages current stage per face, indexed by direction ordinal: {@link #BLOCKED},
     *                   {@link #EMPTY}, or 1..{@link #CLUSTER}. Not mutated.
     * @param steps      owed catch-up steps ({@code <= 0} means nothing to do)
     * @param directionPicker supplies a face index in {@code 0 .. FACES - 1} per step — vanilla's
     *                   random direction choice, passed in so this stays testable and deterministic
     * @return a new array of resulting stages (a copy of {@code faceStages} when nothing changes)
     */
    public static int[] resolve(int[] faceStages, int steps, IntSupplier directionPicker) {
        int[] result = faceStages.clone();
        if (steps <= 0) {
            return result;
        }
        int limit = Math.min(steps, STEP_LIMIT);
        for (int i = 0; i < limit; i++) {
            // Nothing further can change, however long the player was away — stop reading the world.
            if (isTerminal(result)) {
                break;
            }
            int face = directionPicker.getAsInt();
            int stage = result[face];
            if (stage >= EMPTY && stage < CLUSTER) {
                result[face] = stage + 1;
            }
            // else: blocked, or already a full cluster. The step is spent either way.
        }
        return result;
    }

    /** True when every face is blocked or fully grown, so no further step could change anything. */
    public static boolean isTerminal(int[] faceStages) {
        for (int stage : faceStages) {
            if (stage >= EMPTY && stage < CLUSTER) {
                return false;
            }
        }
        return true;
    }
}
