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

import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes a chunk's saved scheduled ticks — the ones read from disk that have not started counting
 * yet — so {@code ScheduledCatchUp} can take the time the chunk was away off their delays before
 * vanilla restarts them. The field is {@code null} once vanilla has restarted them.
 *
 * @author Mark Gottschling on 10/15/2026
 */
@Mixin(LevelChunkTicks.class)
public interface ILevelChunkTicksMixin<T> {

    @Accessor("pendingTicks")
    List<SavedTick<T>> everCrops_getPendingTicks();

    @Accessor("pendingTicks")
    void everCrops_setPendingTicks(List<SavedTick<T>> pendingTicks);
}
