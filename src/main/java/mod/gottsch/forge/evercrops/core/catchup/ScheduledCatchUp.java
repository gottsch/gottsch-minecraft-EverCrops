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

import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.mixin.ILevelChunkTicksMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FrogspawnBlock;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Catch-up for blocks that grow on a <i>scheduled</i> tick rather than a random one — sniffer eggs
 * and frogspawn. Vanilla saves a scheduled tick as a delay relative to the chunk's save, then counts
 * it from the reload, so the countdown pauses for as long as the chunk is away. Nothing random-ticks,
 * so none of the other catch-up rails ever notices.
 *
 * <p><b>Nothing is tracked.</b> Vanilla writes {@code LastUpdate} — the game time of the save — into
 * every chunk, and keeps the chunk's saved ticks in a list until the chunk starts ticking. So when a
 * chunk starts ticking, {@code now - LastUpdate} is exactly how long the countdown paused, and that
 * list already holds every egg waiting on one. {@link #onChunkTickingStart} shortens those delays by
 * the time away. Eggs placed before EverCrops was installed are covered too.
 *
 * <p><b>Nothing is grown here either.</b> Vanilla's own scheduled tick still cracks the egg, plays the
 * sounds and spawns the baby, in a chunk that is ticking with its mobs loaded. A sniffer egg has three
 * stages, each scheduled by the last one's {@code onPlace}; when the time away covers more than the
 * stage that was pending, the rest is kept as a budget and {@link #adjustStageDelay} spends it on the
 * following stages, so they fire a tick apart until the egg has caught up.
 *
 * <p>The math is in {@link ScheduledTickDecision}.
 *
 * @author Mark Gottschling on 10/15/2026
 */
public final class ScheduledCatchUp {

    /**
     * Budgets older than this are dropped as stale. A budget is normally spent within a few ticks;
     * this only bounds memory if an egg is removed some way that skips the usual cleanup.
     */
    private static final long BUDGET_EXPIRY_TICKS = 24_000;

    /** A budget owed to one block: {@code ticks} behind as of {@code stampedAt}. */
    private record Budget(long ticks, long stampedAt) {}

    /** Server thread only. Budgets live for a few ticks, so they are never saved. */
    private static final Map<ResourceKey<Level>, Map<Long, Budget>> BUDGETS = new HashMap<>();

    private ScheduledCatchUp() {}

    /** The blocks this rail catches up. */
    public enum Kind {
        /** Three stages, each scheduled by the previous one's {@code onPlace}. */
        SNIFFER_EGG(true),
        /** One tick, then it hatches. */
        FROGSPAWN(false);

        private final boolean multiStage;

        Kind(boolean multiStage) {
            this.multiStage = multiStage;
        }

        public boolean isMultiStage() {
            return multiStage;
        }

        public boolean isEnabled() {
            return switch (this) {
                case SNIFFER_EGG -> EverCropsApi.config().snifferEggsEnabled();
                case FROGSPAWN -> EverCropsApi.config().frogspawnEnabled();
            };
        }
    }

    /** The kind of scheduled-tick block this is, or {@code null} if this rail does not handle it. */
    @Nullable
    public static Kind kindOf(Block block) {
        if (block instanceof SnifferEggBlock) return Kind.SNIFFER_EGG;
        if (block instanceof FrogspawnBlock) return Kind.FROGSPAWN;
        return null;
    }

    // ------------------------------------------------------------------
    // Chunk load / tick start
    // ------------------------------------------------------------------

    /**
     * A saved chunk was read back. Remember when it was saved; the credit itself waits until the
     * chunk starts ticking, because its saved ticks stay paused until then too.
     */
    public static void onChunkLoaded(LevelChunk chunk, CompoundTag data) {
        if (chunk instanceof ChunkSaveTime holder && data.contains("LastUpdate", Tag.TAG_LONG)) {
            holder.everCrops_setLastSavedTime(data.getLong("LastUpdate"));
        }
    }

    /**
     * The chunk is about to start ticking, and vanilla is about to restart its saved ticks counting
     * from now. Take the time it was away off the ticks of the blocks this rail handles first.
     */
    public static void onChunkTickingStart(ServerLevel level, LevelChunk chunk) {
        if (!(chunk instanceof ChunkSaveTime holder)) return;
        long savedAt = holder.everCrops_getLastSavedTime();
        if (savedAt < 0) return;
        // One use only: a chunk that drops out of ticking and comes back without unloading has
        // already had its ticks restarted, and they kept counting while it was loaded.
        holder.everCrops_setLastSavedTime(-1);

        long now = level.getGameTime();
        long lost = now - savedAt;
        if (lost <= 0) return;
        holder.everCrops_setCreditedAbsence(lost);

        if (!(chunk.getBlockTicks() instanceof LevelChunkTicks<Block> ticks)) return;
        @SuppressWarnings("unchecked")
        ILevelChunkTicksMixin<Block> access = (ILevelChunkTicksMixin<Block>) ticks;
        List<SavedTick<Block>> pending = access.everCrops_getPendingTicks();
        if (pending == null || pending.isEmpty()) return;

        List<SavedTick<Block>> rewritten = null;
        for (int i = 0; i < pending.size(); i++) {
            SavedTick<Block> tick = pending.get(i);
            Kind kind = kindOf(tick.type());
            if (kind == null || !kind.isEnabled()) continue;

            ScheduledTickDecision.Result result = ScheduledTickDecision.rewrite(tick.delay(), lost);
            if (rewritten == null) {
                // The list read from disk is immutable.
                rewritten = new ArrayList<>(pending);
            }
            rewritten.set(i, new SavedTick<>(tick.type(), tick.pos(), result.delay(), tick.priority()));
            // Set, not add: anything left over from an earlier visit was already counted in this
            // absence's LastUpdate, so adding would credit it twice.
            setBudget(level, tick.pos(), kind.isMultiStage() ? result.budget() : 0, now);
        }
        if (rewritten != null) {
            access.everCrops_setPendingTicks(rewritten);
            EverCrops.LOGGER.debug("Scheduled-tick catch-up: credited {} ticks away to chunk {} in {}.",
                    lost, chunk.getPos(), level.dimension().location());
        }
    }

    /** Drop any budgets in a chunk that is unloading; the next reload credits its own absence. */
    public static void onChunkUnload(Level level, ChunkPos chunkPos) {
        Map<Long, Budget> budgets = BUDGETS.get(level.dimension());
        if (budgets == null || budgets.isEmpty()) return;
        budgets.keySet().removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            return (pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z;
        });
    }

    public static void clearAll() {
        BUDGETS.clear();
    }

    // ------------------------------------------------------------------
    // Multi-stage spending (sniffer egg onPlace)
    // ------------------------------------------------------------------

    /**
     * The delay to actually schedule for a sniffer egg's next stage: vanilla's {@code delay},
     * shortened by any budget the egg is owed.
     */
    public static int adjustStageDelay(Level level, BlockPos pos, int delay) {
        if (level.isClientSide()) return delay;
        Map<Long, Budget> budgets = BUDGETS.get(level.dimension());
        if (budgets == null) return delay;
        Budget budget = budgets.get(pos.asLong());
        if (budget == null) return delay;

        long now = level.getGameTime();
        if (now - budget.stampedAt() > BUDGET_EXPIRY_TICKS) {
            budgets.remove(pos.asLong());
            return delay;
        }
        long owed = ScheduledTickDecision.effectiveBudget(budget.ticks(), budget.stampedAt(), now);
        ScheduledTickDecision.Result result = ScheduledTickDecision.spend(delay, owed);
        setBudget(level, pos, result.budget(), now);
        return result.delay();
    }

    /** Forget any budget at this position — a new egg placed there, or the egg just hatched. */
    public static void clearBudget(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Map<Long, Budget> budgets = BUDGETS.get(level.dimension());
        if (budgets != null) {
            budgets.remove(pos.asLong());
        }
    }

    private static void setBudget(Level level, BlockPos pos, long ticks, long now) {
        if (ticks <= 0) {
            clearBudget(level, pos);
            return;
        }
        BUDGETS.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .put(pos.asLong(), new Budget(ticks, now));
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * {@code /evercrops simulate}: treat every handled block within {@code radius} as if its chunk
     * had been away for {@code ticks} — the same rewrite a real reload does, applied to the live
     * scheduled tick instead of the saved one.
     *
     * @return number of blocks credited
     */
    public static int simulate(ServerLevel level, BlockPos origin, int radius, long ticks) {
        long radiusSq = (long) radius * radius;
        long now = level.getGameTime();
        List<ScheduledTick<Block>> found = new ArrayList<>();

        int minCx = (origin.getX() - radius) >> 4, maxCx = (origin.getX() + radius) >> 4;
        int minCz = (origin.getZ() - radius) >> 4, maxCz = (origin.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || !(chunk.getBlockTicks() instanceof LevelChunkTicks<Block> chunkTicks)) continue;
                chunkTicks.getAll()
                        .filter(t -> t.pos().distSqr(origin) <= radiusSq)
                        .filter(t -> {
                            Kind kind = kindOf(t.type());
                            return kind != null && kind.isEnabled();
                        })
                        .forEach(found::add);
            }
        }

        for (ScheduledTick<Block> tick : found) {
            Kind kind = kindOf(tick.type());
            int remaining = (int) Math.max(Integer.MIN_VALUE, tick.triggerTick() - now);
            ScheduledTickDecision.Result result = ScheduledTickDecision.rewrite(remaining, ticks);
            level.getBlockTicks().clearArea(new BoundingBox(tick.pos()));
            level.scheduleTick(tick.pos(), tick.type(), result.delay());
            setBudget(level, tick.pos(), kind != null && kind.isMultiStage() ? result.budget() : 0, now);
        }
        return found.size();
    }

    /** Ticks until this block's pending scheduled tick fires, if it has one. For inspect. */
    public static OptionalLong ticksUntilNext(ServerLevel level, BlockPos pos, Block block) {
        if (!(level.getChunkAt(pos).getBlockTicks() instanceof LevelChunkTicks<Block> chunkTicks)) {
            return OptionalLong.empty();
        }
        long now = level.getGameTime();
        return chunkTicks.getAll()
                .filter(t -> t.pos().equals(pos) && t.type() == block)
                .mapToLong(t -> Math.max(0, t.triggerTick() - now))
                .findFirst();
    }

    /** Ticks this block is still owed after its pending tick, or {@code 0}. For inspect. */
    public static long owedBudget(ServerLevel level, BlockPos pos) {
        Map<Long, Budget> budgets = BUDGETS.get(level.dimension());
        Budget budget = budgets == null ? null : budgets.get(pos.asLong());
        return budget == null ? 0
                : ScheduledTickDecision.effectiveBudget(budget.ticks(), budget.stampedAt(), level.getGameTime());
    }

    /** Ticks away credited when this block's chunk last started ticking, or {@code -1}. For inspect. */
    public static long creditedAbsence(ServerLevel level, BlockPos pos) {
        return level.getChunkAt(pos) instanceof ChunkSaveTime holder ? holder.everCrops_getCreditedAbsence() : -1;
    }
}
