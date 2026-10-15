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
 * Two numbers EverCrops keeps on every loaded chunk (added by {@code LevelChunkMixin}): the game time
 * the chunk was last saved, read from vanilla's own {@code LastUpdate} when it loads, and the time
 * away that catch-up credited when it started ticking. Neither is saved; vanilla's {@code LastUpdate}
 * already is.
 *
 * @author Mark Gottschling on 10/15/2026
 */
public interface ChunkSaveTime {

    /** Game time the chunk was last saved, or {@code -1} if unknown or already used. */
    long everCrops_getLastSavedTime();

    void everCrops_setLastSavedTime(long gameTime);

    /** Ticks away credited when this chunk last started ticking, or {@code -1} if none. For inspect. */
    long everCrops_getCreditedAbsence();

    void everCrops_setCreditedAbsence(long ticks);
}
