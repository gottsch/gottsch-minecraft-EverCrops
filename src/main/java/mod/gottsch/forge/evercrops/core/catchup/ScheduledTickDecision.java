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
 * Pure timing math for scheduled-tick catch-up (sniffer eggs, frogspawn). No Minecraft types, so it
 * is unit-tested directly.
 *
 * <p>Vanilla saves a scheduled tick as a delay relative to the chunk's save time and, on reload,
 * counts that delay from the reload instead — so the countdown pauses for exactly as long as the
 * chunk was away. Catch-up takes that time back in two places:
 * <ol>
 *   <li>{@link #rewrite} — at reload, shorten the saved delay by the time away. Whatever the delay
 *       could not absorb becomes a <i>budget</i>: time the block is still behind once that tick
 *       fires.</li>
 *   <li>{@link #spend} — each later stage a multi-stage block schedules is shortened by the budget,
 *       so stages that were already due fire on the next tick instead of starting a fresh
 *       countdown.</li>
 * </ol>
 * A budget is how far behind real time the block is, so it keeps growing while it waits to be spent
 * (see {@link #effectiveBudget}).
 *
 * @author Mark Gottschling on 10/15/2026
 */
public final class ScheduledTickDecision {

    private ScheduledTickDecision() {}

    /**
     * A rewritten delay, plus the time it could not absorb.
     *
     * @param delay  the new delay, never negative
     * @param budget ticks the block is still behind when that delay runs out ({@code 0} if none)
     */
    public record Result(int delay, long budget) {}

    /**
     * Take the time a chunk was away off one of its saved ticks.
     *
     * @param savedDelay the delay vanilla saved (may already be negative if it was overdue at save)
     * @param lost       ticks between the chunk's save and now
     */
    public static Result rewrite(int savedDelay, long lost) {
        if (lost <= 0) {
            return new Result(savedDelay, 0);
        }
        long remaining = (long) savedDelay - lost;
        if (remaining >= 0) {
            return new Result((int) remaining, 0);
        }
        return new Result(0, -remaining);
    }

    /**
     * Shorten the delay a block just scheduled for its next stage by the budget it is owed.
     *
     * <p>If the budget covers the whole delay, that stage was already due: it fires next tick
     * (delay {@code 1}), and the rest of the budget carries on. Otherwise the stage fires that much
     * sooner and the budget is used up.
     *
     * @param delay  the delay vanilla chose for the next stage
     * @param budget the budget owed right now (see {@link #effectiveBudget})
     */
    public static Result spend(int delay, long budget) {
        if (budget <= 0) {
            return new Result(delay, 0);
        }
        if (budget >= delay) {
            return new Result(1, budget - delay);
        }
        return new Result((int) (delay - budget), 0);
    }

    /**
     * The budget owed now. It was {@code budget} at {@code stampedAt}, and the block falls a tick
     * further behind for every tick it waits — for example while the chunk's mobs are still loading,
     * which holds the block's scheduled tick back.
     */
    public static long effectiveBudget(long budget, long stampedAt, long now) {
        if (budget <= 0) {
            return 0;
        }
        return budget + Math.max(0, now - stampedAt);
    }
}
