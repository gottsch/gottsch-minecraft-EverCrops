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

import mod.gottsch.forge.evercrops.core.config.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability-based crop eligibility (v4 §A, shipped in 3.6.0).
 *
 * <p>Single source of truth for "is this block a catch-up growable?", replacing the
 * triplicated {@code instanceof} lists previously spread across {@code ModEvents.isTracked},
 * {@code ModEvents.isCropBlock}, and the per-mixin {@code state.hasProperty(...)} guards.
 *
 * <p>A block is <b>eligible</b> (capability, config-independent) when, at runtime, all hold:
 * <ol>
 *   <li>{@code state.isRandomlyTicking()} is true; and</li>
 *   <li>it exposes a recognized growth {@link IntegerProperty} (name in {@link #GROWTH_PROPERTY_NAMES}),
 *       <i>or</i> it is a property-less transitional growable on the supplemental allowlist
 *       (currently only {@link BambooSaplingBlock}); and</li>
 *   <li>it is not on the denylist of structural false-positives (e.g. fire, frosted ice).</li>
 * </ol>
 *
 * <p><b>Eligibility vs. tracking.</b> {@link #isEligible(BlockState)} is pure capability and is
 * what registry cleanup uses (config-independent, mirrors the old {@code isCropBlock}).
 * {@link #isTrackingEnabled(BlockState)} layers the per-category config toggles on top and is
 * what the place/break handlers use (mirrors the old {@code isTracked}).
 *
 * @author Mark Gottschling on 2026-06-20
 */
public final class CropEligibility {

    /** Property names treated as a growth axis. Kept constant for 3.6.0; becomes API-extensible in v4. */
    public static final Set<String> GROWTH_PROPERTY_NAMES = Set.of("age", "stage", "colony_age");

    /** Preference order when a block exposes more than one allowlisted growth property. */
    private static final List<String> GROWTH_PROPERTY_PREFERENCE = List.of("age", "stage", "colony_age");

    /** Default denylist: blocks with an {@code age} property that is not a growth axis. */
    public static final Set<ResourceLocation> DEFAULT_DENYLIST = Set.of(
            new ResourceLocation("fire"),
            new ResourceLocation("soul_fire"),
            new ResourceLocation("frosted_ice")
    );

    /** Per-Block resolved growth property cache (Optional.empty() = resolved, none found). */
    private static final Map<Block, Optional<IntegerProperty>> GROWTH_PROPERTY_CACHE = new ConcurrentHashMap<>();

    /** Active denylist; replaced from config at load via {@link #setDenylist(Set)}. */
    private static volatile Set<ResourceLocation> denylist = DEFAULT_DENYLIST;

    private CropEligibility() {}

    /** Toggle-routing category for a block state. {@code OTHER} = eligible but not a known vanilla category. */
    public enum Category { CROPS, STEM, BUSH, COLUMN, SAPLING, BAMBOO, TWISTING, WEEPING, CAVE, CHORUS, OTHER }

    // -------------------------------------------------
    // Capability (config-independent)
    // -------------------------------------------------

    /**
     * True if this block state is a catch-up growable by capability, ignoring config toggles.
     * Mirrors the old {@code ModEvents.isCropBlock}; used by registry cleanup retention.
     */
    public static boolean isEligible(BlockState state) {
        Block block = state.getBlock();
        if (isDenylisted(block)) {
            return false;
        }
        if (!state.isRandomlyTicking()) {
            return false;
        }
        return growthPropertyOf(block) != null || isSupplementalEligible(block);
    }

    /**
     * Resolve the growth {@link IntegerProperty} for a block (cached), or {@code null} if it has none
     * recognized. Property-less growables (bamboo sapling) return {@code null} here and are handled by
     * {@link #isSupplementalEligible(Block)}.
     */
    public static IntegerProperty growthPropertyOf(Block block) {
        return GROWTH_PROPERTY_CACHE.computeIfAbsent(block, CropEligibility::resolveGrowthProperty).orElse(null);
    }

    private static Optional<IntegerProperty> resolveGrowthProperty(Block block) {
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

    /**
     * Property-less transitional growables that have no growth axis to detect but are still tracked.
     * Currently only {@link BambooSaplingBlock} (converts to a bamboo stalk on growth).
     */
    public static boolean isSupplementalEligible(Block block) {
        return block instanceof BambooSaplingBlock;
    }

    private static boolean isDenylisted(Block block) {
        Set<ResourceLocation> current = denylist;
        if (current.isEmpty()) {
            return false;
        }
        return current.contains(BuiltInRegistries.BLOCK.getKey(block));
    }

    // -------------------------------------------------
    // Tracking (capability + config toggle)
    // -------------------------------------------------

    /**
     * True if this block state should be tracked given the user's config toggles.
     * Mirrors the old {@code ModEvents.isTracked}; used by the place/break handlers.
     */
    public static boolean isTrackingEnabled(BlockState state) {
        return isEligible(state) && isCategoryEnabled(categoryOf(state));
    }

    /**
     * Map a block state to its config-toggle routing category. Uses {@code instanceof} purely to pick
     * which toggle applies — this is preference routing, not the eligibility gate.
     */
    public static Category categoryOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock) return Category.CROPS;
        if (block instanceof StemBlock) return Category.STEM;
        if (block instanceof SweetBerryBushBlock
                || block instanceof NetherWartBlock
                || block instanceof CocoaBlock) return Category.BUSH;
        if (block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof KelpBlock) return Category.COLUMN;
        if (block instanceof SaplingBlock) return Category.SAPLING;
        if (block instanceof BambooSaplingBlock || block instanceof BambooStalkBlock) return Category.BAMBOO;
        if (block instanceof TwistingVinesBlock) return Category.TWISTING;
        if (block instanceof WeepingVinesBlock) return Category.WEEPING;
        if (block instanceof CaveVinesBlock) return Category.CAVE;
        if (block instanceof ChorusFlowerBlock) return Category.CHORUS;
        return Category.OTHER;
    }

    private static boolean isCategoryEnabled(Category category) {
        return switch (category) {
            case CROPS    -> Config.SERVER.cropsEnabled.get();
            case STEM     -> Config.SERVER.stemCropsEnabled.get();
            case BUSH     -> Config.SERVER.bushCropsEnabled.get();
            case COLUMN   -> Config.SERVER.columnCropsEnabled.get();
            case SAPLING  -> Config.SERVER.saplingCropsEnabled.get();
            case BAMBOO   -> Config.SERVER.bambooEnabled.get();
            case TWISTING -> Config.SERVER.twistingVinesEnabled.get();
            case WEEPING  -> Config.SERVER.weepingVinesEnabled.get();
            case CAVE     -> Config.SERVER.caveVinesEnabled.get();
            case CHORUS   -> Config.SERVER.chorusFlowerEnabled.get();
            case OTHER    -> Config.SERVER.moddedCropsEnabled.get();
        };
    }

    // -------------------------------------------------
    // Config wiring
    // -------------------------------------------------

    /** Replace the active denylist (called once from config load). Invalid block ids are skipped by the caller. */
    public static void setDenylist(Set<ResourceLocation> ids) {
        denylist = (ids == null || ids.isEmpty()) ? Set.of() : new LinkedHashSet<>(ids);
    }
}
