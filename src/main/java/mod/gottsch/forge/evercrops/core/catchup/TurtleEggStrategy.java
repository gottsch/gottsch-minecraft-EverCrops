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
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Catch-up strategy for turtle eggs: advance the cluster's {@code hatch} level and, at the end of
 * the ladder, run vanilla's hatch — remove the block and release one baby turtle per egg.
 *
 * <p>This is the first EverCrops strategy that <b>spawns entities</b> rather than only editing block
 * state. Two things keep that safe. It runs from {@code randomTick}, which only fires on a loaded,
 * ticking chunk, so {@code addFreshEntity} never targets an unloaded one. And random ticks are
 * naturally staggered across blocks (roughly one per block per 1350 ticks), so a nest of many
 * clusters hatches spread over the following minutes rather than all on the reload tick.
 *
 * <p>The {@code onSand} requirement is re-checked live here, mirroring the beehive strategy's flower
 * check: the ground under a cluster cannot change while its chunk is unloaded, so what we read on
 * the reload tick correctly describes the whole offline gap.
 *
 * <p>Everything below the decision is a faithful replay of {@code TurtleEggBlock.randomTick} —
 * the same sounds, game events, particles, and turtle placement — so a caught-up hatch is
 * indistinguishable from one the player stood and watched.
 *
 * @author Mark Gottschling
 */
public final class TurtleEggStrategy implements CatchUpStrategy {

    public static final TurtleEggStrategy INSTANCE = new TurtleEggStrategy();

    /** Vanilla's baby-turtle age stamp (one full growth period from adult). */
    private static final int BABY_TURTLE_AGE = -24_000;

    /** Vanilla's "block break" client level event, used for the hatching particles. */
    private static final int LEVEL_EVENT_BLOCK_BREAK = 2001;

    private TurtleEggStrategy() {}

    @Override
    public boolean grow(ServerLevel level, BlockPos pos, BlockState state, int steps, RandomSource random) {
        if (!state.hasProperty(TurtleEggBlock.HATCH) || !state.hasProperty(TurtleEggBlock.EGGS)) {
            return false;
        }
        if (!TurtleEggBlock.onSand(level, pos)) {
            return false;
        }

        int hatch = state.getValue(TurtleEggBlock.HATCH);
        TurtleEggDecision.Outcome outcome = TurtleEggDecision.resolve(
                hatch, steps, EverCropsApi.config().turtleEggSpawnTurtles());

        if (outcome.hatches()) {
            hatch(level, pos, state, random);
            return true;
        }
        if (outcome.targetHatch() <= hatch) {
            return false;
        }
        crack(level, pos, state, outcome.targetHatch(), random);
        return true;
    }

    /** Advance the cluster one or more hatch levels in place — vanilla's {@code HATCH < 2} branch. */
    private static void crack(ServerLevel level, BlockPos pos, BlockState state, int target, RandomSource random) {
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS,
                0.7F, 0.9F + random.nextFloat() * 0.2F);
        level.setBlock(pos, state.setValue(TurtleEggBlock.HATCH, target), 2);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
    }

    /** Remove the cluster and release one baby turtle per egg — vanilla's {@code HATCH == 2} branch. */
    private static void hatch(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS,
                0.7F, 0.9F + random.nextFloat() * 0.2F);
        level.removeBlock(pos, false);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));

        int eggs = state.getValue(TurtleEggBlock.EGGS);
        for (int i = 0; i < eggs; i++) {
            level.levelEvent(LEVEL_EVENT_BLOCK_BREAK, pos, Block.getId(state));
            Turtle turtle = EntityType.TURTLE.create(level);
            if (turtle != null) {
                turtle.setAge(BABY_TURTLE_AGE);
                turtle.setHomePos(pos);
                turtle.moveTo(pos.getX() + 0.3 + i * 0.2, pos.getY(), pos.getZ() + 0.3, 0.0F, 0.0F);
                level.addFreshEntity(turtle);
            }
        }
    }
}
