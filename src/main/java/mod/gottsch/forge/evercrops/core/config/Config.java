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

import mod.gottsch.neo.gottschcore.config.AbstractConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author by Mark Gottschling on 3/14/2025
 */
public class Config extends AbstractConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    public static final ModConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;

    public static Config instance = new Config();

    static {
        final Pair<CommonConfig, ModConfigSpec> commonSpecPair = new ModConfigSpec.Builder()
                .configure(CommonConfig::new);
        COMMON_SPEC = commonSpecPair.getRight();
        COMMON = commonSpecPair.getLeft();

        final Pair<ServerConfig, ModConfigSpec> serverSpecPair = new ModConfigSpec.Builder()
                .configure(ServerConfig::new);
        SERVER_SPEC = serverSpecPair.getRight();
        SERVER = serverSpecPair.getLeft();
    }

    private Config() {}

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static class CommonConfig {
        public static Logging logging;
        public CommonConfig(ModConfigSpec.Builder builder) {
            logging = new Logging(builder);
        }
    }

    public static class ServerConfig {
        public final ModConfigSpec.BooleanValue cropsEnabled;
        public final ModConfigSpec.BooleanValue stemCropsEnabled;
        public final ModConfigSpec.BooleanValue bushCropsEnabled;
        public final ModConfigSpec.BooleanValue columnCropsEnabled;
        public final ModConfigSpec.BooleanValue saplingCropsEnabled;
        public final ModConfigSpec.BooleanValue bambooEnabled;
        public final ModConfigSpec.BooleanValue twistingVinesEnabled;
        public final ModConfigSpec.BooleanValue weepingVinesEnabled;
        public final ModConfigSpec.BooleanValue caveVinesEnabled;
        public final ModConfigSpec.BooleanValue chorusFlowerEnabled;
        public final ModConfigSpec.BooleanValue trackWildVines;
        public final ModConfigSpec.BooleanValue autoCleanupEnabled;
        public final ModConfigSpec.IntValue     autoCleanupIntervalTicks;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment("Controls which crop categories receive catch-up growth.")
                   .push("crops");

            cropsEnabled = builder
                    .comment("Enable catch-up growth for standard crops: wheat, carrots, potatoes, beetroot, pitcher plant, torchflower, and modded subclasses.")
                    .define("cropsEnabled", true);

            stemCropsEnabled = builder
                    .comment("Enable catch-up growth for stem crops: melon and pumpkin stems (including fruit spread), and modded subclasses.")
                    .define("stemCropsEnabled", true);

            bushCropsEnabled = builder
                    .comment("Enable catch-up growth for bush/special crops: sweet berry bushes, nether wart, cocoa pods, and modded subclasses.")
                    .define("bushCropsEnabled", true);

            columnCropsEnabled = builder
                    .comment("Enable catch-up growth for column crops: sugar cane, cactus, kelp, and modded subclasses.")
                    .define("columnCropsEnabled", true);

            saplingCropsEnabled = builder
                    .comment("Enable catch-up growth for saplings (oak, birch, spruce, jungle, acacia, dark oak, cherry, mangrove) " +
                             "and modded subclasses. When enabled, saplings will advance their STAGE and attempt to grow a tree based on elapsed time.")
                    .define("saplingCropsEnabled", true);

            bambooEnabled = builder
                    .comment("Enable catch-up growth for bamboo. When enabled, bamboo stalks will grow additional blocks " +
                             "above based on elapsed time. Requires sky light (not torch light) at the block above. " +
                             "Separate from columnCropsEnabled because bamboo uses STAGE gating and has a max height of 16.")
                    .define("bambooEnabled", true);

            twistingVinesEnabled = builder
                    .comment("Enable catch-up growth for twisting vines (Crimson Forest, Nether). " +
                             "Grows upward, no light requirement. Modded subclasses of TwistingVinesBlock are also covered.")
                    .define("twistingVinesEnabled", true);

            weepingVinesEnabled = builder
                    .comment("Enable catch-up growth for weeping vines (Nether). " +
                             "Grows downward, no light requirement. Modded subclasses of WeepingVinesBlock are also covered.")
                    .define("weepingVinesEnabled", true);

            caveVinesEnabled = builder
                    .comment("Enable catch-up growth for cave vines (glow berry vines). " +
                             "Grows downward, no light requirement. Modded subclasses of CaveVinesBlock are also covered.")
                    .define("caveVinesEnabled", true);

            chorusFlowerEnabled = builder
                    .comment("Enable catch-up growth for chorus flowers (End dimension). " +
                             "Due to branching growth mechanics, at most one growth step is applied per catch-up event " +
                             "to prevent excessive structure expansion.")
                    .define("chorusFlowerEnabled", true);

            trackWildVines = builder
                    .comment("Track naturally-generated (wild) kelp, twisting/weeping/cave vines, and chorus flowers for catch-up growth. " +
                             "When false (default), only placed plants of these types are tracked (by a player, villager, or automation), " +
                             "which keeps the saved registry small near oceans and the Nether. When true, wild ones are tracked too — " +
                             "kelp and vines relocate their tracking entry as they grow so they never leave stale entries behind. " +
                             "(Bamboo is unaffected by this setting — it always registers on first tick regardless.)")
                    .define("trackWildVines", false);

            builder.pop();
            builder.comment("Registry cleanup settings.").push("cleanup");

            autoCleanupEnabled = builder
                    .comment("Periodically scan the crop registry and remove entries whose block is no longer a tracked crop " +
                             "(e.g. crops destroyed by pistons, explosions, fluids, or other mods that bypass the player BreakEvent). " +
                             "Only loaded chunks are checked; unloaded entries are left untouched. " +
                             "Prevents the evercrops.dat registry from slowly accumulating stale entries over time.")
                    .define("autoCleanupEnabled", true);

            autoCleanupIntervalTicks = builder
                    .comment("How often (in game ticks) the automatic cleanup scan runs per dimension. " +
                             "1200 = 1 minute, 36000 = 30 minutes, 72000 = 60 minutes. " +
                             "The scan only touches loaded chunks, so it is cheap at typical values.")
                    .defineInRange("autoCleanupIntervalTicks", 36_000, 1_200, 288_000);

            builder.pop();
        }
    }

    @Override
    public String getLogsFolder() {
        return CommonConfig.logging.folder.get();
    }

    @Override
    public String getLogSize() {
        return CommonConfig.logging.size.get();
    }

    @Override
    public String getLoggingLevel() {
        return CommonConfig.logging.level.get();
    }
}
