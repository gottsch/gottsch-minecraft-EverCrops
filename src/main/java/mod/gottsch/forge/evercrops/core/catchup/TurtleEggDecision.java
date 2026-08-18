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
 * Pure "how far do these owed steps carry an egg cluster?" math for turtle eggs — no Minecraft
 * types, so it unit-tests with no world and no bootstrap (the same split as {@link BeehiveDecision}).
 *
 * <p>Vanilla's ladder is short: {@code hatch} runs 0 &rarr; 1 &rarr; 2, and the step <i>after</i> 2
 * is the hatch itself, which removes the block and releases the baby turtles. So at most three owed
 * steps ever matter, no matter how long the chunk sat unloaded — a long absence cannot compound into
 * anything bigger than one hatch.
 *
 * @author Mark Gottschling
 */
public final class TurtleEggDecision {

    /** Vanilla's {@code TurtleEggBlock.MAX_HATCH_LEVEL} — mirrored so this class stays Minecraft-free. */
    public static final int MAX_HATCH_LEVEL = 2;

    private TurtleEggDecision() {}

    /**
     * Where {@code steps} owed advances leave a cluster currently at {@code hatch}.
     *
     * @param targetHatch the hatch level to write back (meaningless when {@code hatches} is true —
     *                    the block is removed instead)
     * @param hatches     true if the cluster tips past the ladder and should release its turtles
     */
    public record Outcome(int targetHatch, boolean hatches) {}

    /**
     * Resolve owed steps against a cluster's current hatch level.
     *
     * @param hatch      current {@code hatch} value (0..2)
     * @param steps      owed catch-up steps (&lt;= 0 means nothing to do)
     * @param allowHatch whether the final hatch may fire ({@code turtleEggSpawnTurtles}); when false
     *                   the cluster clamps at fully-cracked and waits for vanilla
     */
    public static Outcome resolve(int hatch, int steps, boolean allowHatch) {
        if (steps <= 0) {
            return new Outcome(hatch, false);
        }
        int raw = hatch + steps;
        if (raw > MAX_HATCH_LEVEL) {
            // Clamping (rather than reporting the raw overshoot) is what makes the disabled-hatch
            // case idempotent: a cluster already sitting at 2 resolves to 2 and the caller no-ops,
            // so it does not re-crack — and re-play the crack sound — on every random tick.
            return new Outcome(MAX_HATCH_LEVEL, allowHatch);
        }
        return new Outcome(raw, false);
    }
}
