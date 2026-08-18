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
package mod.gottsch.forge.evercrops.api;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability detection (v4 §A, public API): resolve a block's growth {@link IntegerProperty} — the
 * {@code age} / {@code stage} / {@code colony_age} / {@code hatch} axis it advances along as it grows — by name,
 * cached per {@link Block} so the lookup stays cheap on the random-tick hot path.
 *
 * <p>Loader-agnostic and config-independent: this is pure "what axis does this block grow on?",
 * with no notion of whether tracking is enabled. The config-gated eligibility lives in the host.
 *
 * @author Mark Gottschling
 */
public final class GrowthProperties {

    /**
     * Property names treated as a growth axis.
     *
     * <p>{@code hatch} is the turtle-egg / sniffer-egg incubation axis — a genuine bounded growth
     * ladder like {@code age}, just one that ends by removing the block rather than ripening it.
     */
    public static final Set<String> GROWTH_PROPERTY_NAMES = Set.of("age", "stage", "colony_age", "hatch");

    /** Preference order when a block exposes more than one allowlisted growth property. */
    private static final List<String> GROWTH_PROPERTY_PREFERENCE = List.of("age", "stage", "colony_age", "hatch");

    /** Per-Block resolved growth property cache (Optional.empty() = resolved, none found). */
    private static final Map<Block, Optional<IntegerProperty>> CACHE = new ConcurrentHashMap<>();

    private GrowthProperties() {}

    /**
     * Resolve the growth {@link IntegerProperty} for a block (cached), or {@code null} if it has none
     * recognized. Property-less growables (e.g. bamboo sapling) return {@code null} here.
     */
    public static IntegerProperty growthPropertyOf(Block block) {
        return CACHE.computeIfAbsent(block, GrowthProperties::resolve).orElse(null);
    }

    private static Optional<IntegerProperty> resolve(Block block) {
        // Collect allowlisted IntegerProperty matches by name, then pick by preference order.
        Map<String, IntegerProperty> byName = new HashMap<>();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            if (property instanceof IntegerProperty intProperty
                    && GROWTH_PROPERTY_NAMES.contains(property.getName())) {
                byName.putIfAbsent(property.getName(), intProperty);
            }
        }
        for (String name : GROWTH_PROPERTY_PREFERENCE) {
            IntegerProperty match = byName.get(name);
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }
}
