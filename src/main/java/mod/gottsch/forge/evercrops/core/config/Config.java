/*
 * This file is part of EverCrops.
 * Copyright (c) 2025 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.evercrops.core.config;

import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author by Mark Gottschling on 3/14/2025
 */
public final class Config {

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder()
                .configure(ServerConfig::new);
        SERVER_SPEC = serverSpecPair.getRight();
        SERVER = serverSpecPair.getLeft();
    }

    private Config() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    // -------------------------------------------------
    // Dynamic config application (denylist → CropEligibility)
    // -------------------------------------------------

    /** Config-load listener (mod event bus). Applies the denylist when our SERVER spec loads. */
    public static void onConfigLoading(final ModConfigEvent.Loading event) {
        applyIfServer(event);
    }

    /** Config-reload listener (mod event bus). Re-applies the denylist on /reload or file edit. */
    public static void onConfigReloading(final ModConfigEvent.Reloading event) {
        applyIfServer(event);
    }

    private static void applyIfServer(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            applyDenylist();
        }
    }

    /** Parse the configured block ids and push them to {@link CropEligibility}. Invalid ids are skipped. */
    private static void applyDenylist() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String entry : SERVER.denylist.get()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null) {
                ids.add(id);
            }
        }
        CropEligibility.setDenylist(ids);
    }

    public static class ServerConfig {
        public final ForgeConfigSpec.BooleanValue cropsEnabled;
        public final ForgeConfigSpec.BooleanValue stemCropsEnabled;
        public final ForgeConfigSpec.BooleanValue bushCropsEnabled;
        public final ForgeConfigSpec.BooleanValue columnCropsEnabled;
        public final ForgeConfigSpec.BooleanValue saplingCropsEnabled;
        public final ForgeConfigSpec.BooleanValue bambooEnabled;
        public final ForgeConfigSpec.BooleanValue twistingVinesEnabled;
        public final ForgeConfigSpec.BooleanValue weepingVinesEnabled;
        public final ForgeConfigSpec.BooleanValue caveVinesEnabled;
        public final ForgeConfigSpec.BooleanValue chorusFlowerEnabled;
        public final ForgeConfigSpec.BooleanValue beehivesEnabled;
        public final ForgeConfigSpec.IntValue     beehiveHoneyIntervalTicks;
        public final ForgeConfigSpec.BooleanValue turtleEggsEnabled;
        public final ForgeConfigSpec.IntValue     turtleEggHatchIntervalTicks;
        public final ForgeConfigSpec.BooleanValue turtleEggSpawnTurtles;
        public final ForgeConfigSpec.BooleanValue trackWildTurtleEggs;
        public final ForgeConfigSpec.BooleanValue moddedCropsEnabled;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> denylist;
        public final ForgeConfigSpec.BooleanValue trackWildVines;
        public final ForgeConfigSpec.BooleanValue autoCleanupEnabled;
        public final ForgeConfigSpec.IntValue     autoCleanupIntervalTicks;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.push("crops");
            cropsEnabled = builder.comment("Enable catch-up growth for standard crops (wheat, carrots, potatoes, beetroot, etc.)")
                    .define("cropsEnabled", true);
            stemCropsEnabled = builder.comment("Enable catch-up growth for stem crops (melon, pumpkin)")
                    .define("stemCropsEnabled", true);
            bushCropsEnabled = builder.comment("Enable catch-up growth for sweet berry bushes, nether wart, and cocoa pods")
                    .define("bushCropsEnabled", true);
            columnCropsEnabled = builder.comment("Enable catch-up growth for column crops (sugar cane, cactus, kelp)")
                    .define("columnCropsEnabled", true);
            saplingCropsEnabled = builder.comment("Enable catch-up growth for saplings")
                    .define("saplingCropsEnabled", true);
            bambooEnabled = builder.comment("Enable catch-up growth for bamboo")
                    .define("bambooEnabled", true);
            twistingVinesEnabled = builder.comment("Enable catch-up growth for twisting vines")
                    .define("twistingVinesEnabled", true);
            weepingVinesEnabled = builder.comment("Enable catch-up growth for weeping vines")
                    .define("weepingVinesEnabled", true);
            caveVinesEnabled = builder.comment("Enable catch-up growth for cave vines")
                    .define("caveVinesEnabled", true);
            chorusFlowerEnabled = builder.comment("Enable catch-up growth for chorus flowers")
                    .define("chorusFlowerEnabled", true);
            beehivesEnabled = builder
                    .comment("Enable catch-up honey production for beehives and bee nests. When enabled, a hive that",
                             "had bees and a known flower while you were away keeps filling its honey_level (up to 5)",
                             "based on elapsed time. Honey only accrues for the daytime share of the time away (bees do",
                             "not work at night/rain), and EverCrops learns each hive's real fill rate while it is loaded.")
                    .define("beehivesEnabled", true);
            beehiveHoneyIntervalTicks = builder
                    .comment("Cold-start fill rate for beehive catch-up: assumed daytime ticks to gain one honey level",
                             "before a hive has been observed producing honey while loaded (1200 ticks = 1 minute).",
                             "Once a hive produces honey while you are nearby, its own measured rate is used instead and",
                             "this value no longer applies to it. Lower = faster offline honey.")
                    .defineInRange("beehiveHoneyIntervalTicks", 1_500, 200, 100_000);
            turtleEggsEnabled = builder
                    .comment("Enable catch-up hatching for turtle eggs. When enabled, an egg cluster sitting on sand keeps",
                             "cracking (and eventually hatching into baby turtles) based on how long the chunk was unloaded.",
                             "Eggs not on sand make no progress, matching vanilla.")
                    .define("turtleEggsEnabled", true);

            turtleEggHatchIntervalTicks = builder
                    .comment("Assumed ticks per turtle-egg hatch stage. There are 3 stages from a fresh egg to baby turtles,",
                             "so the default 32000 works out to roughly 4 in-game days total - about the vanilla pace.",
                             "Vanilla eggs only advance during a short window near dawn, so this is a flat-rate approximation",
                             "of that daily cadence. Lower = faster offline hatching.")
                    .defineInRange("turtleEggHatchIntervalTicks", 32_000, 1_200, 500_000);

            turtleEggSpawnTurtles = builder
                    .comment("Whether catch-up may complete the final hatch and spawn baby turtles. When false, catch-up still",
                             "cracks eggs offline but stops at fully-cracked, leaving the actual hatch to vanilla once a player",
                             "is nearby. Useful on servers that want tight control over entity counts.")
                    .define("turtleEggSpawnTurtles", true);

            trackWildTurtleEggs = builder
                    .comment("Track naturally-generated (wild) turtle egg clusters for catch-up hatching. When false (default),",
                             "only placed clusters are tracked, which keeps the saved registry small near beaches.",
                             "Set true if you want wild beach nests to hatch while you are away too.")
                    .define("trackWildTurtleEggs", false);

            moddedCropsEnabled = builder
                    .comment("Enable catch-up growth for modded growables that pass capability detection (a random-ticking block",
                             "with an 'age'/'stage'/'colony_age' growth property) but do not match any known vanilla category. Default true.")
                    .define("moddedCropsEnabled", true);
            denylist = builder
                    .comment("Block ids to exclude from capability detection — structural false-positives whose growth property",
                             "is not actually a growth axis (e.g. fire spread, ice melt). Use full ids like \"minecraft:fire\".")
                    .defineList("denylist",
                            List.of("minecraft:fire", "minecraft:soul_fire", "minecraft:frosted_ice"),
                            o -> o instanceof String);
            trackWildVines = builder
                    .comment("Track naturally-generated (wild) kelp, twisting/weeping/cave vines, and chorus flowers for catch-up growth.",
                             "When false (default), only placed plants of these types are tracked (player, villager, automation), keeping the",
                             "saved registry small near oceans and the Nether. When true, wild ones are tracked too; kelp and vines",
                             "relocate their tracking entry as they grow so they never leave stale entries behind.",
                             "(Bamboo is unaffected by this setting — it always registers on first tick regardless.)")
                    .define("trackWildVines", false);
            builder.pop();

            builder.push("cleanup");
            autoCleanupEnabled = builder
                    .comment("Periodically scan the crop registry and remove entries whose block is no longer a tracked crop",
                             "(e.g. crops destroyed by pistons, explosions, fluids, or other mods that bypass the player BreakEvent).",
                             "Only loaded chunks are checked; unloaded entries are left untouched.",
                             "Prevents the evercrops.dat registry from slowly accumulating stale entries over time.")
                    .define("autoCleanupEnabled", true);
            autoCleanupIntervalTicks = builder
                    .comment("How often (in game ticks) the automatic cleanup scan runs per dimension.",
                             "1200 = 1 minute, 36000 = 30 minutes, 72000 = 60 minutes.",
                             "The scan only touches loaded chunks, so it is cheap at typical values.")
                    .defineInRange("autoCleanupIntervalTicks", 36_000, 1_200, 288_000);
            builder.pop();
        }
    }
}
