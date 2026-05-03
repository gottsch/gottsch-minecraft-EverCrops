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
