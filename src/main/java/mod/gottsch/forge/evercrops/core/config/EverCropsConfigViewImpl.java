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
package mod.gottsch.forge.evercrops.core.config;

import mod.gottsch.forge.evercrops.api.EverCropsConfigView;

/**
 * {@link EverCropsConfigView} backed by the live {@link Config} spec. Reads the spec on each call,
 * so values stay current across {@code /reload} and file edits. Bound into {@code EverCropsApi}
 * once during mod construction; lets the API/engine and add-ons read host settings without
 * importing the loader-specific {@code Config}/{@code ModConfigSpec} classes.
 *
 * @author Mark Gottschling
 */
public final class EverCropsConfigViewImpl implements EverCropsConfigView {

    @Override public boolean cropsEnabled()        { return Config.SERVER.cropsEnabled.get(); }
    @Override public boolean stemCropsEnabled()    { return Config.SERVER.stemCropsEnabled.get(); }
    @Override public boolean bushCropsEnabled()    { return Config.SERVER.bushCropsEnabled.get(); }
    @Override public boolean columnCropsEnabled()  { return Config.SERVER.columnCropsEnabled.get(); }
    @Override public boolean saplingCropsEnabled() { return Config.SERVER.saplingCropsEnabled.get(); }
    @Override public boolean bambooEnabled()       { return Config.SERVER.bambooEnabled.get(); }
    @Override public boolean twistingVinesEnabled(){ return Config.SERVER.twistingVinesEnabled.get(); }
    @Override public boolean weepingVinesEnabled() { return Config.SERVER.weepingVinesEnabled.get(); }
    @Override public boolean caveVinesEnabled()    { return Config.SERVER.caveVinesEnabled.get(); }
    @Override public boolean chorusFlowerEnabled() { return Config.SERVER.chorusFlowerEnabled.get(); }
    @Override public boolean beehivesEnabled()     { return Config.SERVER.beehivesEnabled.get(); }
    @Override public int beehiveHoneyIntervalTicks(){ return Config.SERVER.beehiveHoneyIntervalTicks.get(); }
    @Override public boolean moddedCropsEnabled()  { return Config.SERVER.moddedCropsEnabled.get(); }
    @Override public boolean trackWildVines()      { return Config.SERVER.trackWildVines.get(); }
    @Override public boolean autoCleanupEnabled()  { return Config.SERVER.autoCleanupEnabled.get(); }
    @Override public int autoCleanupIntervalTicks(){ return Config.SERVER.autoCleanupIntervalTicks.get(); }
}
