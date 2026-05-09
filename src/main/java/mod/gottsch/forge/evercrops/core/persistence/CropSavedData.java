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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-dimension crop state storage backed by Minecraft's SavedData system.
 * One instance is created per ServerLevel; data is saved automatically
 * to <world>/data/evercrops.dat when the level saves.
 *
 * @author Mark Gottschling on 4/25/2026
 */
public class CropSavedData extends SavedData {

    public static final String DATA_NAME = "evercrops";

    private final Map<Long, CropState> crops = new HashMap<>();

    public CropSavedData() {}

    // -------------------------------------------------
    // Factory / lifecycle
    // -------------------------------------------------

    public static CropSavedData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CropSavedData::load, CropSavedData::new, DATA_NAME);
    }

    // -------------------------------------------------
    // Serialization
    // -------------------------------------------------

    public static CropSavedData load(CompoundTag tag) {
        CropSavedData data = new CropSavedData();
        ListTag list = tag.getList("crops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long posKey = entry.getLong("pos");
            CropState state = new CropState();
            state.setLastCallGameTime(entry.getLong("lastCallTime"))
                 .setLastGrowthGameTime(entry.getLong("lastGrowthTime"))
                 .setLastCallLightLevel(entry.getInt("lastCallLight"))
                 .setLastGrowthLightLevel(entry.getInt("lastGrowthLight"));
            data.crops.put(posKey, state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, CropState> entry : crops.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", entry.getKey());
            CropState s = entry.getValue();
            e.putLong("lastCallTime", s.getLastCallGameTime());
            e.putLong("lastGrowthTime", s.getLastGrowthGameTime());
            e.putInt("lastCallLight", s.getLastCallLightLevel());
            e.putInt("lastGrowthLight", s.getLastGrowthLightLevel());
            list.add(e);
        }
        tag.put("crops", list);
        return tag;
    }

    // -------------------------------------------------
    // Data access
    // -------------------------------------------------

    public Optional<CropState> get(BlockPos pos) {
        return Optional.ofNullable(crops.get(pos.asLong()));
    }

    public void put(BlockPos pos, CropState state) {
        crops.put(pos.asLong(), state);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (crops.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    // -------------------------------------------------
    // Dev / command utilities
    // -------------------------------------------------

    /**
     * Subtracts {@code ticks} from every entry within {@code radius} blocks of
     * {@code origin}, making them appear stale so the next randomTick triggers
     * offline-growth catch-up. Returns the number of entries backdated.
     */
    public int backdateInRadius(BlockPos origin, int radius, long ticks) {
        long radiusSq = (long) radius * radius;
        int count = 0;
        for (Map.Entry<Long, CropState> entry : crops.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (pos.distSqr(origin) <= radiusSq) {
                CropState state = entry.getValue();
                state.setLastCallGameTime(state.getLastCallGameTime() - ticks);
                state.setLastGrowthGameTime(state.getLastGrowthGameTime() - ticks);
                count++;
            }
        }
        if (count > 0) setDirty();
        return count;
    }

    /** Returns all tracked positions as packed longs (see {@link BlockPos#asLong()}). */
    public Set<Long> getKeys() {
        return Collections.unmodifiableSet(crops.keySet());
    }

    public int size() {
        return crops.size();
    }
}
