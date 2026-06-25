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
package mod.gottsch.forge.evercrops.core.persistence;

import mod.gottsch.forge.evercrops.api.BeehiveState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Per-dimension beehive state storage backed by Minecraft's SavedData system. One instance per
 * ServerLevel; saved to {@code <world>/data/evercrops_bees.dat} when the level saves.
 *
 * <p>Kept deliberately separate from {@link CropSavedData} (its own {@code .dat}): beehives carry a
 * different shape of state ({@link BeehiveState}: honey level + learned production rate, no light)
 * and a separate file means adopting bees never touches the verified crop persistence.
 *
 * @author Mark Gottschling
 */
public class BeehiveSavedData extends SavedData {

    public static final String DATA_NAME = "evercrops_bees";

    private final Map<Long, BeehiveState> hives = new HashMap<>();

    public BeehiveSavedData() {}

    // -------------------------------------------------
    // Factory / lifecycle
    // -------------------------------------------------

    public static BeehiveSavedData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BeehiveSavedData::load, BeehiveSavedData::new, DATA_NAME);
    }

    // -------------------------------------------------
    // Serialization
    // -------------------------------------------------

    public static BeehiveSavedData load(CompoundTag tag) {
        BeehiveSavedData data = new BeehiveSavedData();
        ListTag list = tag.getList("hives", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long posKey = entry.getLong("pos");
            BeehiveState state = new BeehiveState();
            state.setLastCallGameTime(entry.getLong("lastCallTime"))
                 .setLastGrowthGameTime(entry.getLong("lastGrowthTime"))
                 // Pre-existing entries may lack these; sensible defaults keep them inert until re-observed.
                 .setLastHoneyLevel(entry.contains("lastHoney") ? entry.getInt("lastHoney") : -1)
                 .setLearnedIntervalTicks(entry.getLong("learnedInterval"))
                 .setDaytimeTicksAccumulated(entry.getLong("daytimeAcc"))
                 .setLastActive(entry.getBoolean("lastActive"));
            data.hives.put(posKey, state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, BeehiveState> entry : hives.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", entry.getKey());
            BeehiveState s = entry.getValue();
            e.putLong("lastCallTime", s.getLastCallGameTime());
            e.putLong("lastGrowthTime", s.getLastGrowthGameTime());
            e.putInt("lastHoney", s.getLastHoneyLevel());
            e.putLong("learnedInterval", s.getLearnedIntervalTicks());
            e.putLong("daytimeAcc", s.getDaytimeTicksAccumulated());
            e.putBoolean("lastActive", s.isLastActive());
            list.add(e);
        }
        tag.put("hives", list);
        return tag;
    }

    // -------------------------------------------------
    // Data access
    // -------------------------------------------------

    public Optional<BeehiveState> get(BlockPos pos) {
        return Optional.ofNullable(hives.get(pos.asLong()));
    }

    public void put(BlockPos pos, BeehiveState state) {
        hives.put(pos.asLong(), state);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (hives.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    // -------------------------------------------------
    // Dev / command utilities
    // -------------------------------------------------

    /**
     * Subtracts {@code ticks} from both timestamps of every entry within {@code radius} blocks of
     * {@code origin}, so the next tick on each hive immediately enters the offline-growth path.
     * Returns the number of entries backdated.
     */
    public int backdateInRadius(BlockPos origin, int radius, long ticks) {
        long radiusSq = (long) radius * radius;
        int count = 0;
        for (Map.Entry<Long, BeehiveState> entry : hives.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (pos.distSqr(origin) <= radiusSq) {
                BeehiveState state = entry.getValue();
                state.setLastCallGameTime(state.getLastCallGameTime() - ticks);
                state.setLastGrowthGameTime(state.getLastGrowthGameTime() - ticks);
                count++;
            }
        }
        if (count > 0) setDirty();
        return count;
    }

    /**
     * Removes entries whose block is no longer a beehive, but only for positions in currently loaded
     * chunks. Unloaded entries are intentionally left untouched (the hive may still be there). Mirrors
     * {@link CropSavedData#cleanupStale} for the bee store.
     *
     * @return number of stale entries removed
     */
    public int cleanupStale(ServerLevel level, Predicate<BlockState> isBeehive) {
        List<Long> toRemove = new ArrayList<>();
        for (long packedPos : hives.keySet()) {
            BlockPos pos = BlockPos.of(packedPos);
            if (!level.isLoaded(pos)) continue;
            if (!isBeehive.test(level.getBlockState(pos))) {
                toRemove.add(packedPos);
            }
        }
        toRemove.forEach(hives::remove);
        if (!toRemove.isEmpty()) setDirty();
        return toRemove.size();
    }

    /** Returns all tracked positions as packed longs (see {@link BlockPos#asLong()}). */
    public Set<Long> getKeys() {
        return Collections.unmodifiableSet(hives.keySet());
    }

    public int size() {
        return hives.size();
    }
}
