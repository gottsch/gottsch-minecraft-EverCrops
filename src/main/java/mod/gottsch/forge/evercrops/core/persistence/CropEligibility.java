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

import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.api.EverCropsConfigView;
import mod.gottsch.forge.evercrops.api.GrowthProperties;
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
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Capability-based crop eligibility (v4 §A, shipped in 3.6.0). The capability detection itself —
 * resolving a block's growth {@link IntegerProperty} — now lives in {@link GrowthProperties} (public
 * API); this class layers the host-only concerns on top: the config-toggle category routing (read
 * through {@link EverCropsApi#config()}, not {@code Config} directly) and the structural denylist.
 *
 * <p><b>Eligibility vs. tracking.</b> {@link #isEligible(BlockState)} is pure capability and is
 * what registry cleanup uses (config-independent). {@link #isTrackingEnabled(BlockState)} layers the
 * per-category config toggles on top and is what the place/break handlers use.
 *
 * @author Mark Gottschling on 2026-06-20
 */
public final class CropEligibility {

    /** Property names treated as a growth axis. Now defined by the public API. */
    public static final Set<String> GROWTH_PROPERTY_NAMES = GrowthProperties.GROWTH_PROPERTY_NAMES;

    /** Default denylist: blocks with an {@code age} property that is not a growth axis. */
    public static final Set<ResourceLocation> DEFAULT_DENYLIST = Set.of(
            new ResourceLocation("fire"),
            new ResourceLocation("soul_fire"),
            new ResourceLocation("frosted_ice")
    );

    /** Active denylist; replaced from config at load via {@link #setDenylist(Set)}. */
    private static volatile Set<ResourceLocation> denylist = DEFAULT_DENYLIST;

    private CropEligibility() {}

    /** Toggle-routing category for a block state. {@code OTHER} = eligible but not a known vanilla category. */
    public enum Category { CROPS, STEM, BUSH, COLUMN, SAPLING, BAMBOO, TWISTING, WEEPING, CAVE, CHORUS, EGGS, OTHER }

    // -------------------------------------------------
    // Capability (config-independent)
    // -------------------------------------------------

    /**
     * True if this block state is a catch-up growable by capability, ignoring config toggles.
     * Used by registry cleanup retention.
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
     * recognized. Delegates to the public {@link GrowthProperties}.
     */
    public static IntegerProperty growthPropertyOf(Block block) {
        return GrowthProperties.growthPropertyOf(block);
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
     * Used by the place/break handlers.
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
        // Incubating eggs (turtle eggs today; sniffer eggs share the same HATCH axis) grow on the
        // 'hatch' property rather than age/stage, but are otherwise ordinary random-tick growables.
        if (block instanceof TurtleEggBlock) return Category.EGGS;
        return Category.OTHER;
    }

    private static boolean isCategoryEnabled(Category category) {
        EverCropsConfigView config = EverCropsApi.config();
        return switch (category) {
            case CROPS    -> config.cropsEnabled();
            case STEM     -> config.stemCropsEnabled();
            case BUSH     -> config.bushCropsEnabled();
            case COLUMN   -> config.columnCropsEnabled();
            case SAPLING  -> config.saplingCropsEnabled();
            case BAMBOO   -> config.bambooEnabled();
            case TWISTING -> config.twistingVinesEnabled();
            case WEEPING  -> config.weepingVinesEnabled();
            case CAVE     -> config.caveVinesEnabled();
            case CHORUS   -> config.chorusFlowerEnabled();
            case EGGS     -> config.turtleEggsEnabled();
            case OTHER    -> config.moddedCropsEnabled();
        };
    }

    // -------------------------------------------------
    // Config wiring
    // -------------------------------------------------

    /** Replace the active denylist (called once from config load). Block ids that fail to parse are skipped by the caller. */
    public static void setDenylist(Set<ResourceLocation> ids) {
        denylist = (ids == null || ids.isEmpty()) ? Set.of() : new LinkedHashSet<>(ids);
    }
}
