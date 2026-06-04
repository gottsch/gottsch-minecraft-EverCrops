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

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Extensible set of "is this block state still a live, tracked crop?" predicates,
 * consulted by registry auto-cleanup.
 *
 * <p>The base mod and each add-on (e.g. EverCrops: Farmer's Delight) share a single
 * per-dimension {@link CropSavedData} registry keyed only by position — entries carry
 * no type tag. Cleanup therefore cannot tell a base-mod entry from an add-on entry, so
 * every mod that writes to the registry must contribute a predicate here. Cleanup keeps
 * an entry if <em>any</em> registered predicate matches; only entries that no module
 * recognises as a crop are removed.
 *
 * <p>Predicates are registered from each mod's {@code ModEvents} static initializer,
 * which the loader runs when it processes the {@code @EventBusSubscriber} class during
 * mod loading — long before the first level tick that could trigger cleanup.
 *
 * @author Mark Gottschling on 2026-06-02
 */
public final class CropBlockPredicates {

    private static final List<Predicate<BlockState>> PREDICATES = new CopyOnWriteArrayList<>();

    private CropBlockPredicates() {}

    /** Registers a predicate identifying block states that are still tracked crops. */
    public static void register(Predicate<BlockState> predicate) {
        PREDICATES.add(predicate);
    }

    /** True if any registered predicate considers this block state a live crop. */
    public static boolean isCropBlock(BlockState state) {
        for (Predicate<BlockState> predicate : PREDICATES) {
            if (predicate.test(state)) {
                return true;
            }
        }
        return false;
    }
}
