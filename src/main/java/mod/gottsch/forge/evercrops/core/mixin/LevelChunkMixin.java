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
package mod.gottsch.forge.evercrops.core.mixin;

import mod.gottsch.forge.evercrops.core.catchup.ChunkSaveTime;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every loaded chunk the two in-memory numbers described by {@link ChunkSaveTime}.
 *
 * @author Mark Gottschling on 10/15/2026
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ChunkSaveTime {

    @Unique
    private long everCrops_lastSavedTime = -1L;

    @Unique
    private long everCrops_creditedAbsence = -1L;

    @Override
    public long everCrops_getLastSavedTime() {
        return everCrops_lastSavedTime;
    }

    @Override
    public void everCrops_setLastSavedTime(long gameTime) {
        this.everCrops_lastSavedTime = gameTime;
    }

    @Override
    public long everCrops_getCreditedAbsence() {
        return everCrops_creditedAbsence;
    }

    @Override
    public void everCrops_setCreditedAbsence(long ticks) {
        this.everCrops_creditedAbsence = ticks;
    }
}
