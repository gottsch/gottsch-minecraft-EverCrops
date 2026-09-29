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

import mod.gottsch.forge.evercrops.api.CatchUpStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Catch-up strategy for copper (and any other {@link ChangeOverTimeBlock}): replay the oxidation
 * opportunities missed while the chunk was unloaded, rolling vanilla's neighbourhood chance live.
 *
 * <p>Oxidation is not capped at one stage per pass. Vanilla already refuses to advance a block past
 * any less-oxidized neighbour within 4 blocks, so a block in a copper build is stopped by its own
 * neighbours after one stage — while a genuinely lone block, which vanilla really would walk all the way
 * to oxidized over a long absence, is free to do so here too. After each stage the neighbourhood is
 * re-scanned, and the pass stops exactly where vanilla would.
 *
 * <p>The scan only needs redoing after <i>this</i> block changes: nothing else writes during the
 * pass, so at most one scan per stage (four in all, normally one).
 *
 * <p>Deliberately <i>not</i> consulted: {@code CommonHooks.canCropGrow}. Copper is not a plant, and
 * vanilla fires no crop-grow event for it.
 *
 * @author Mark Gottschling
 */
public final class CopperStrategy implements CatchUpStrategy {

    public static final CopperStrategy INSTANCE = new CopperStrategy();

    private CopperStrategy() {}

    /**
     * Generic entry point. Opportunities blocked by a less-weathered neighbour are dropped here, since
     * there is nowhere to keep them — EverCrops' own copper hook calls {@link #advance} instead, which
     * hands them back to be banked.
     */
    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        return advance(level, pos, state, steps, random).grew();
    }

    /**
     * Replay {@code steps} owed opportunities, and report any that could not be used because a
     * less-weathered neighbour is in the way.
     *
     * <p>Those blocked opportunities are real time that passed — in vanilla the neighbour would have
     * caught up during the same absence and freed this block to use them. So rather than lose them,
     * the caller banks them and offers them again on later ticks, once the neighbour has had its own
     * catch-up. Nothing can overshoot: the neighbour rule is re-checked on every stage, so a block
     * never gets ahead of the copper around it no matter how many opportunities it holds.
     */
    public Outcome advance(ServerLevel level, BlockPos pos, BlockState state, long steps, RandomSource random) {
        long remaining = steps;
        BlockState current = state;
        boolean grew = false;

        while (remaining > 0 && current.getBlock() instanceof ChangeOverTimeBlock<?> block) {
            Optional<BlockState> next = block.getNext(current);
            if (next.isEmpty()) {
                break; // fully oxidized — the end of the ladder
            }
            Neighbourhood around = scan(level, pos, block);
            if (CopperDecision.isBlockedByNeighbour(around.lessOxidized())) {
                // The neighbour cannot change during this pass, so hand the rest back to be banked.
                return new Outcome(grew, remaining);
            }
            float chance = CopperDecision.advanceChance(around.moreOxidized(), around.sameAge(),
                    block.getChanceModifier());
            long needed = CopperDecision.opportunitiesUntilAdvance(chance, 1.0 - random.nextFloat());
            if (needed > remaining) {
                break; // the rest of the owed opportunities all rolled and missed
            }
            remaining -= needed;
            current = next.get();
            level.setBlockAndUpdate(pos, current);
            grew = true;
        }
        // Fully oxidized, or the remaining opportunities all missed: nothing left worth keeping.
        return new Outcome(grew, 0);
    }

    /** Result of {@link #advance}: whether the block changed, and opportunities to bank for later. */
    public record Outcome(boolean grew, long blockedSteps) {}

    /**
     * Count the copper within vanilla's neighbourhood, exactly as {@code applyChangeOverTime} does:
     * Manhattan distance 4 around {@code pos}, excluding {@code pos} itself, and only blocks whose
     * stage enum is the same type as this block's (so copper never counts some other weathering
     * family). Shared with {@code /evercrops inspect}.
     */
    public static Neighbourhood scan(ServerLevel level, BlockPos pos, ChangeOverTimeBlock<?> self) {
        Enum<?> age = self.getAge();
        int stage = age.ordinal();
        int less = 0;
        int same = 0;
        int more = 0;
        int radius = CopperDecision.SCAN_DISTANCE;
        for (BlockPos p : BlockPos.withinManhattan(pos, radius, radius, radius)) {
            if (p.distManhattan(pos) > radius) {
                break;
            }
            if (p.equals(pos)) {
                continue;
            }
            if (level.getBlockState(p).getBlock() instanceof ChangeOverTimeBlock<?> other
                    && other.getAge().getClass() == age.getClass()) {
                int otherStage = other.getAge().ordinal();
                if (otherStage < stage) {
                    less++;
                } else if (otherStage > stage) {
                    more++;
                } else {
                    same++;
                }
            }
        }
        return new Neighbourhood(less, same, more);
    }

    /** The weathering stage of a copper block as its ordinal (0 = unaffected), or -1 if it is not one. */
    public static int stageOf(BlockState state) {
        return state.getBlock() instanceof ChangeOverTimeBlock<?> block ? block.getAge().ordinal() : -1;
    }

    /** Copper neighbours within the scan, split by stage relative to the scanned block. */
    public record Neighbourhood(int lessOxidized, int sameAge, int moreOxidized) {}
}
