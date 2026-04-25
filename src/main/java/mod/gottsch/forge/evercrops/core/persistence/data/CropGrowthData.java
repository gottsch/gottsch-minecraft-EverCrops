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
package mod.gottsch.forge.evercrops.core.persistence.data;

import java.io.Serializable;

/**
 * Data-gathering model — tracks call/growth counts and deltas for analysis.
 * Not used by the production mod; referenced only by the inactive data-mixin classes.
 *
 * @author Mark Gottschling on 3/13/2025
 */
public class CropGrowthData implements Serializable {

    private long lastCallGameTime;
    private long lastGrowthGameTime;

    private int callCount;
    private int growthCount;

    private long totalCallDelta;
    private long totalGrowthDelta;

    public CropGrowthData() {}

    public CropGrowthData(long lastCallGameTime, long lastGrowthGameTime) {
        this.lastCallGameTime = lastCallGameTime;
        this.lastGrowthGameTime = lastGrowthGameTime;
    }

    public long getLastCallGameTime() { return lastCallGameTime; }
    public void setLastCallGameTime(long lastCallGameTime) { this.lastCallGameTime = lastCallGameTime; }

    public long getLastGrowthGameTime() { return lastGrowthGameTime; }
    public void setLastGrowthGameTime(long lastGrowthGameTime) { this.lastGrowthGameTime = lastGrowthGameTime; }

    public int getCallCount() { return callCount; }
    public void setCallCount(int callCount) { this.callCount = callCount; }

    public int getGrowthCount() { return growthCount; }
    public void setGrowthCount(int growthCount) { this.growthCount = growthCount; }

    public long getTotalCallDelta() { return totalCallDelta; }
    public void setTotalCallDelta(long totalCallDelta) { this.totalCallDelta = totalCallDelta; }

    public long getTotalGrowthDelta() { return totalGrowthDelta; }
    public void setTotalGrowthDelta(long totalGrowthDelta) { this.totalGrowthDelta = totalGrowthDelta; }

    @Override
    public String toString() {
        return "CropGrowthData{" +
                "lastCallGameTime=" + lastCallGameTime +
                ", lastGrowthGameTime=" + lastGrowthGameTime +
                ", callCount=" + callCount +
                ", growthCount=" + growthCount +
                ", totalCallDelta=" + totalCallDelta +
                ", totalGrowthDelta=" + totalGrowthDelta +
                '}';
    }
}
