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

/**
 * Read-only view of base EverCrops' live config, bound once at startup via
 * {@link EverCropsApi#bindConfig}. The implementation reads the host's config spec on each call, so
 * values stay live across {@code /reload} and file edits; addons (and the engine) read settings
 * through {@link EverCropsApi} without importing the host's loader-specific {@code Config} class.
 *
 * <p>Vanilla Minecraft types are fine in this interface (identical across loaders); only the
 * loader-specific config-spec classes are kept out.
 *
 * @author Mark Gottschling
 */
public interface EverCropsConfigView {

    boolean cropsEnabled();

    boolean stemCropsEnabled();

    boolean bushCropsEnabled();

    boolean columnCropsEnabled();

    boolean saplingCropsEnabled();

    boolean bambooEnabled();

    boolean twistingVinesEnabled();

    boolean weepingVinesEnabled();

    boolean caveVinesEnabled();

    boolean chorusFlowerEnabled();

    boolean moddedCropsEnabled();

    boolean trackWildVines();

    boolean autoCleanupEnabled();

    int autoCleanupIntervalTicks();
}
