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

import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;

import java.io.IOException;
import java.util.Objects;

/**
 * @author by Mark Gottschling on 3/15/2025
 */
public class CropGrowthData {
    private long lastCallGameTime;
    private int callCount;
    private long totalCallDelta;
    private long lastGrowthGameTime;
    private int growthCount;
    private long totalGrowthDelta;

    public CropGrowthData() {}

    public CropGrowthData(long lastCallGameTime, long lastGrowthGameTime) {
        this.lastCallGameTime = lastCallGameTime;
        this.lastGrowthGameTime = lastGrowthGameTime;
    }

    public int getCallCount() {
        return callCount;
    }

    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }

    public long getLastCallGameTime() {
        return lastCallGameTime;
    }

    public void setLastCallGameTime(long lastCallGameTime) {
        this.lastCallGameTime = lastCallGameTime;
    }

    public long getTotalCallDelta() {
        return totalCallDelta;
    }

    public void setTotalCallDelta(long totalCallDelta) {
        this.totalCallDelta = totalCallDelta;
    }

    public long getLastGrowthGameTime() {
        return lastGrowthGameTime;
    }

    public void setLastGrowthGameTime(long lastGrowthGameTime) {
        this.lastGrowthGameTime = lastGrowthGameTime;
    }

    public int getGrowthCount() {
        return growthCount;
    }

    public void setGrowthCount(int growthCount) {
        this.growthCount = growthCount;
    }

    public long getTotalGrowthDelta() {
        return totalGrowthDelta;
    }

    public void setTotalGrowthDelta(long totalGrowthDelta) {
        this.totalGrowthDelta = totalGrowthDelta;
    }

    public static class Serializer implements org.mapdb.Serializer<CropGrowthData> {

        @Override
        public void serialize(@NotNull DataOutput2 out, @NotNull CropGrowthData value) throws IOException {
            try {
                out.writeLong(value.getLastCallGameTime());
                out.writeInt(value.getCallCount());
                out.writeLong(value.getTotalCallDelta());

                out.writeLong(value.getLastGrowthGameTime());
                out.writeInt(value.getGrowthCount());
                out.writeLong(value.getTotalGrowthDelta());
            } catch (IOException e) {
                // TODO probably want to handle somehow so the mod doesn't crash
                throw new RuntimeException(e);
            }
        }

        @Override
        public CropGrowthData deserialize(@NotNull DataInput2 input, int i) throws IOException {
            try {
                long lastCallGameTime = input.readLong();
                int callCount = input.readInt();
                long callDelta = input.readLong();

                long lastGrowthGameTime = input.readLong();
                int growthCount = input.readInt();
                long growthDelta = input.readLong();
                CropGrowthData data =  new CropGrowthData();
                data.setLastCallGameTime(lastCallGameTime);
                data.setCallCount(callCount);
                data.setTotalCallDelta(callDelta);
                data.setLastGrowthGameTime(lastGrowthGameTime);
                data.setGrowthCount(growthCount);
                data.setTotalGrowthDelta(growthDelta);
                return data;
            } catch(IOException e) {
                throw new RuntimeException();
            }
        }

        @Override
        public int fixedSize() {
            return 40; // 2 int * 4 bytes + 4 long * 8 bytes
        }

        @Override
        public boolean isTrusted() {
            return true;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CropGrowthData that = (CropGrowthData) o;
        return lastCallGameTime == that.lastCallGameTime && callCount == that.callCount && totalCallDelta == that.totalCallDelta && lastGrowthGameTime == that.lastGrowthGameTime && growthCount == that.growthCount && totalGrowthDelta == that.totalGrowthDelta;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastCallGameTime, callCount, totalCallDelta, lastGrowthGameTime, growthCount, totalGrowthDelta);
    }

    @Override
    public String toString() {
        return "CropGrowthData{" +
                "callCount=" + callCount +
                ", lastCallGameTime=" + lastCallGameTime +
                ", totalCallDelta=" + totalCallDelta +
                ", lastGrowthGameTime=" + lastGrowthGameTime +
                ", growthCount=" + growthCount +
                ", totalGrowthDelta=" + totalGrowthDelta +
                '}';
    }
}
