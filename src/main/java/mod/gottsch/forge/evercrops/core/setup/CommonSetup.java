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
package mod.gottsch.forge.evercrops.core.setup;

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.config.Config;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * @author by Mark Gottschling on 3/14/2025
 */
@Mod.EventBusSubscriber(modid = EverCrops.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonSetup {

    /**
     *
     * @param event
     */
    public static void init(final FMLCommonSetupEvent event) {
        // create a treasure2 specific log file
        Config.instance.addRollingFileAppender(EverCrops.MOD_ID);
        EverCrops.LOGGER.debug("file appender created");
    }
}
