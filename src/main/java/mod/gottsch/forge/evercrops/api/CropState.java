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
package mod.gottsch.forge.evercrops.api;

import java.util.Objects;

/**
 * The crop flavour of {@link CatchUpState}: adds the light levels observed at the last call/growth
 * (for the night-time daylight fallback in the light gate) and the last age/stage observed (for
 * in-place-harvest detection). Setters are covariant so chains keep the {@code CropState} type.
 *
 * @author by Mark Gottschling on 3/17/2025
 */
public class CropState extends CatchUpState {

    private int lastCallLightLevel;
    private int lastGrowthLightLevel;

    /**
     * Last age/stage value observed for this block, used to detect in-place harvests
     * (e.g. Harvest With Ease, vanilla sweet-berry harvest) that reset the age without
     * firing a break/place event. {@code -1} means "not yet observed".
     */
    private int lastAge = -1;

    public CropState() {}

    @Override
    public CropState setLastCallGameTime(long lastCallGameTime) {
        super.setLastCallGameTime(lastCallGameTime);
        return this;
    }

    @Override
    public CropState setLastGrowthGameTime(long lastGrowthGameTime) {
        super.setLastGrowthGameTime(lastGrowthGameTime);
        return this;
    }

    public int getLastCallLightLevel() {
        return lastCallLightLevel;
    }

    public CropState setLastCallLightLevel(int lastCallLightLevel) {
        this.lastCallLightLevel = lastCallLightLevel;
        return this;
    }

    public int getLastGrowthLightLevel() {
        return lastGrowthLightLevel;
    }

    public CropState setLastGrowthLightLevel(int lastGrowthLightLevel) {
        this.lastGrowthLightLevel = lastGrowthLightLevel;
        return this;
    }

    public int getLastAge() {
        return lastAge;
    }

    public CropState setLastAge(int lastAge) {
        this.lastAge = lastAge;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CropState cropState = (CropState) o;
        return lastCallLightLevel == cropState.lastCallLightLevel
                && lastGrowthLightLevel == cropState.lastGrowthLightLevel
                && lastAge == cropState.lastAge;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLastCallGameTime(), getLastGrowthGameTime(),
                lastCallLightLevel, lastGrowthLightLevel, lastAge);
    }

    @Override
    public String toString() {
        return "CropState{" +
                "lastCallGameTime=" + getLastCallGameTime() +
                ", lastGrowthGameTime=" + getLastGrowthGameTime() +
                ", lastCallLightLevel=" + lastCallLightLevel +
                ", lastGrowthLightLevel=" + lastGrowthLightLevel +
                ", lastAge=" + lastAge +
                '}';
    }
}
