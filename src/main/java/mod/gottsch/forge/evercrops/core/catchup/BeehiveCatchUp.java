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

import mod.gottsch.forge.evercrops.api.BeehiveState;
import mod.gottsch.forge.evercrops.api.CatchUpDecision;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.mixin.IBeehiveBlockEntityMixin;
import mod.gottsch.forge.evercrops.core.persistence.BeehiveRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The per-tick beehive catch-up routine, shared by {@code BeehiveBlockEntityMixin} (which calls it
 * every loaded server tick) and the {@code /evercrops tick} command (which calls it on demand). It
 * ties together the three pieces: lazy state tracking, rate-learning ({@link BeehiveDecision#observe}),
 * and the offline replay via the shared unlit engine ({@link CatchUpDecision#computeStepsUnlit}) +
 * {@link BeehiveStrategy}.
 *
 * <p>Beehives have no random tick, so there is no per-growth INVOKE hook as the crop mixins have —
 * the unlit engine refreshes both clocks every loaded tick instead, which is why honey produced
 * normally while loaded is never double-counted by catch-up.
 *
 * @author Mark Gottschling
 */
public final class BeehiveCatchUp {

    private BeehiveCatchUp() {}

    /**
     * Average ticks between calls for a beehive's tracking. A block entity ticks every game tick while
     * its chunk is fully ticking, so this is small — it exists only as the unlit engine's "was the
     * chunk really unloaded?" guard (gaps below ~2&times; this are treated as normal play, not offline).
     */
    public static final int AVG_BE_CALL_INTERVAL = 20;

    /**
     * Fraction of real elapsed time treated as productive: vanilla bees deliver honey only by day in
     * fair weather, so only this share of an offline window is credited. ~0.5 ≈ the day portion of the
     * day/night cycle (a deliberate, slightly conservative approximation — rain makes the true figure
     * a little lower).
     */
    public static final double DAYTIME_FRACTION = 0.5;

    /**
     * Run one catch-up pass for the hive at {@code pos}. Lazily begins tracking a new hive, folds any
     * real honey delivery into the learned rate, and — when the hive is eligible and a load-gap is
     * detected — replays the owed honey levels. Persists the (mutated) state.
     */
    public static void onServerTick(ServerLevel level, BlockPos pos, BlockState state, BeehiveBlockEntity be) {
        if (!EverCropsApi.config().beehivesEnabled()) return;
        if (!state.hasProperty(BeehiveBlock.HONEY_LEVEL)) return;

        long now = level.getGameTime();
        int honey = state.getValue(BeehiveBlock.HONEY_LEVEL);

        BeehiveState beeState = BeehiveRegistry.get(level, pos).orElse(null);
        if (beeState == null) {
            // First time we have seen this hive — baseline it and wait for the next tick. A brand-new
            // hive has no learned rate yet, so eligibility is simply bees in residence + a live flower.
            boolean activeNew = hasValidFlower(level, be) && be.getOccupantCount() > 0;
            BeehiveRegistry.put(level, pos, new BeehiveState()
                    .setLastCallGameTime(now)
                    .setLastGrowthGameTime(now)
                    .setLastHoneyLevel(honey)
                    .setLastActive(activeNew));
            return;
        }

        boolean active = isActive(level, be, beeState);

        // A drop in honey is a harvest (player sheared/bottled) or a hive replaced at this position —
        // reset the growth clock so the fresh refill is not instantly caught-up. The bee analogue of
        // the crops' in-place-harvest guard. Detected before observe() re-baselines the level.
        int prevHoney = beeState.getLastHoneyLevel();
        boolean harvested = prevHoney >= 0 && honey < prevHoney;

        // 1. Learn this hive's real production rate from deliveries observed during loaded play.
        BeehiveDecision.observe(beeState, honey, level.isDay(), active);
        if (harvested) {
            beeState.setLastCallGameTime(now).setLastGrowthGameTime(now);
        }

        // 2. Replay honey owed while the chunk was unloaded (or backdated by /evercrops simulate).
        //    computeStepsUnlit always runs so it manages the clock uniformly — refreshing it on every
        //    loaded tick, consuming the gap exactly once on reload — so an ineligible gap is never
        //    banked. Whether the gap actually earns honey is gated on whether the hive was producing
        //    as of its LAST loaded tick ({@code lastActive}): bees/flowers can only change while
        //    loaded, so that pre-unload state describes the whole offline gap — and a hive whose bees
        //    are merely out foraging on the reload tick must not lose the credit it earned.
        int interval = BeehiveDecision.effectiveInterval(
                beeState, EverCropsApi.config().beehiveHoneyIntervalTicks(), DAYTIME_FRACTION);
        int steps = CatchUpDecision.computeStepsUnlit(beeState, now, AVG_BE_CALL_INTERVAL, interval);
        if (steps > 0 && beeState.isLastActive()
                && BeehiveStrategy.INSTANCE.grow(level, pos, state, steps, level.getRandom())) {
            // Our own catch-up growth must not be mistaken for a vanilla delivery next tick.
            beeState.setLastHoneyLevel(level.getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL));
        }

        // Remember whether the hive is producing now, to decide the next gap's credit.
        beeState.setLastActive(active);

        BeehiveRegistry.put(level, pos, beeState);
    }

    /**
     * Whether the hive is currently producing honey, so catch-up may credit elapsed time. It must have
     * a living flower it can reach <b>and</b> either bees in residence right now <i>or</i> a proven
     * production history (a learned rate). The flower requirement is always live, so a hive whose
     * flowers are destroyed stops being eligible; the bees-or-history clause keeps an established hive
     * eligible while its bees are merely out foraging (a momentary {@code occupantCount} of 0), without
     * crediting a hive that has never actually produced.
     *
     * <p>The flower is read from the hive's remembered {@code savedFlowerPos}, which vanilla refreshes
     * as bees return with a new flower but never clears on its own — so {@link #hasValidFlower}
     * confirms the block there is still a flower rather than trusting a stale position.
     */
    public static boolean isActive(ServerLevel level, BeehiveBlockEntity be, BeehiveState beeState) {
        return hasValidFlower(level, be)
                && (be.getOccupantCount() > 0 || beeState.getLearnedIntervalTicks() > 0);
    }

    /**
     * True if the hive remembers a flower position whose block is still a flower. When that position's
     * chunk is not currently loaded we cannot check it cheaply, so we trust the remembered position
     * rather than force-load a chunk (the hive's own chunk is loaded and forage range is short, so
     * this fallback is rare).
     */
    public static boolean hasValidFlower(ServerLevel level, BeehiveBlockEntity be) {
        BlockPos flower = ((IBeehiveBlockEntityMixin) be).everCrops_getSavedFlowerPos();
        if (flower == null) {
            return false;
        }
        if (!level.isLoaded(flower)) {
            return true;
        }
        return level.getBlockState(flower).is(BlockTags.FLOWERS);
    }
}
