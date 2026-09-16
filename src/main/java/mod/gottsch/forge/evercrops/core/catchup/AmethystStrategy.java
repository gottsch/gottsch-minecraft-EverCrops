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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Catch-up strategy for budding amethyst: replay the growth opportunities missed while the chunk was
 * unloaded, each one picking a random face and advancing it a rung along
 * {@code empty -> small -> medium -> large -> cluster}.
 *
 * <p>This is the first EverCrops strategy that grows on a <b>neighbouring</b> position rather than
 * its own — the tracked entry stays on the budding block, which is never consumed by its own growth.
 *
 * <p>The six faces are read fresh here, on the reload tick, so a geode the player walled in while
 * away is seen as it is now; and because this runs from {@code randomTick}, the chunk is loaded and
 * the neighbour positions are guaranteed readable. The step math itself lives in
 * {@link AmethystDecision} — including the rule that a step landing on a blocked face is spent.
 *
 * <p>Deliberately <i>not</i> consulted: {@code CommonHooks.canCropGrow}. Amethyst is a mineral, not
 * a plant; vanilla fires no crop-grow event for it, and inventing one here would hand crop-control
 * mods a veto over geodes that they do not have in an unmodified game.
 *
 * @author Mark Gottschling
 */
public final class AmethystStrategy implements CatchUpStrategy {

    public static final AmethystStrategy INSTANCE = new AmethystStrategy();

    /** Vanilla's own {@code DIRECTIONS} — face index is the {@link Direction} ordinal. */
    private static final Direction[] DIRECTIONS = Direction.values();

    private AmethystStrategy() {}

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        int[] before = classifyFaces(level, pos);
        if (AmethystDecision.isTerminal(before)) {
            return false;
        }

        int[] after = AmethystDecision.resolve(before, steps, () -> random.nextInt(AmethystDecision.FACES));

        boolean grew = false;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (after[i] == before[i]) {
                continue;
            }
            Direction direction = DIRECTIONS[i];
            BlockPos target = pos.relative(direction);
            Block block = blockForStage(after[i]);
            if (block == null) {
                continue;
            }
            // Waterlogging is re-read from the live target, exactly as vanilla does when it places a
            // bud: a face sitting in a water source grows a waterlogged one.
            BlockState grown = block.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, direction)
                    .setValue(AmethystClusterBlock.WATERLOGGED,
                            level.getFluidState(target).getType() == Fluids.WATER);
            level.setBlockAndUpdate(target, grown);
            grew = true;
        }
        return grew;
    }

    /**
     * Read the six neighbours of a budding amethyst into {@link AmethystDecision}'s stage alphabet,
     * indexed by {@link Direction} ordinal. Shared with {@code /evercrops inspect}.
     *
     * <p>A bud that faces some <i>other</i> direction counts as blocked: vanilla's ladder checks
     * {@code FACING == direction} at every rung, so it cannot advance one either — and it must never
     * be overwritten.
     */
    public static int[] classifyFaces(ServerLevel level, BlockPos pos) {
        int[] stages = new int[AmethystDecision.FACES];
        for (int i = 0; i < DIRECTIONS.length; i++) {
            stages[i] = classify(level, pos, DIRECTIONS[i]);
        }
        return stages;
    }

    private static int classify(ServerLevel level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        if (BuddingAmethystBlock.canClusterGrowAtState(neighbour)) {
            return AmethystDecision.EMPTY;
        }
        if (!neighbour.hasProperty(AmethystClusterBlock.FACING)
                || neighbour.getValue(AmethystClusterBlock.FACING) != direction) {
            return AmethystDecision.BLOCKED;
        }
        if (neighbour.is(Blocks.SMALL_AMETHYST_BUD)) return 1;
        if (neighbour.is(Blocks.MEDIUM_AMETHYST_BUD)) return 2;
        if (neighbour.is(Blocks.LARGE_AMETHYST_BUD)) return 3;
        // A full cluster is the end of the ladder — vanilla has no branch that advances it further.
        if (neighbour.is(Blocks.AMETHYST_CLUSTER)) return AmethystDecision.CLUSTER;
        return AmethystDecision.BLOCKED;
    }

    /** The block a given ladder rung places, or {@code null} for the rungs that place nothing. */
    public static Block blockForStage(int stage) {
        return switch (stage) {
            case 1 -> Blocks.SMALL_AMETHYST_BUD;
            case 2 -> Blocks.MEDIUM_AMETHYST_BUD;
            case 3 -> Blocks.LARGE_AMETHYST_BUD;
            case AmethystDecision.CLUSTER -> Blocks.AMETHYST_CLUSTER;
            default -> null;
        };
    }

    /** Human-readable rung name for {@code /evercrops inspect}. */
    public static String stageName(int stage) {
        return switch (stage) {
            case AmethystDecision.BLOCKED -> "blocked";
            case AmethystDecision.EMPTY -> "empty";
            case 1 -> "small bud";
            case 2 -> "medium bud";
            case 3 -> "large bud";
            case AmethystDecision.CLUSTER -> "cluster (done)";
            default -> "?";
        };
    }
}
