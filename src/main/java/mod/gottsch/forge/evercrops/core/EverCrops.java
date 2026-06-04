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
package mod.gottsch.forge.evercrops.core;

import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.event.ModEvents;
import mod.gottsch.forge.evercrops.core.setup.CommonSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Mark Gottschling on 3/13/2025
 */
@Mod(EverCrops.MOD_ID)
public class EverCrops {

    public static Logger LOGGER = LogManager.getLogger(EverCrops.MOD_ID);

    public static final String MOD_ID = "evercrops";

    public EverCrops(IEventBus modEventBus, ModContainer modContainer) {
        Config.register(modContainer);
        ModEvents.registerPredicates();
        modEventBus.addListener(CommonSetup::init);
    }
}
