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

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

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
            builder.pop();
        }
    }
}
