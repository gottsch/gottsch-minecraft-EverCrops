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
package mod.gottsch.forge.evercrops.core.persistence;

import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

/**
 * @author by Mark Gottschling on 3/17/2025
 */
public class CropState implements Serializable {
    private long lastCallGameTime;
    private long lastGrowthGameTime;

    private int lastCallLightLevel;
    private int lastGrowthLightLevel;

    /**
     * Last age/stage value observed for this block, used to detect in-place harvests
     * (e.g. Harvest With Ease, vanilla sweet-berry harvest) that reset the age without
     * firing a break/place event. {@code -1} means "not yet observed".
     */
    private int lastAge = -1;

    public CropState() {}

    public long getLastCallGameTime() {
        return lastCallGameTime;
    }

    public CropState setLastCallGameTime(long lastCallGameTime) {
        this.lastCallGameTime = lastCallGameTime;
        return this;
    }

    public int getLastCallLightLevel() {
        return lastCallLightLevel;
    }

    public CropState setLastCallLightLevel(int lastCallLightLevel) {
        this.lastCallLightLevel = lastCallLightLevel;
        return this;
    }

    public long getLastGrowthGameTime() {
        return lastGrowthGameTime;
    }

    public CropState setLastGrowthGameTime(long lastGrowthGameTime) {
        this.lastGrowthGameTime = lastGrowthGameTime;
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
        CropState cropState = (CropState) o;
        return lastCallGameTime == cropState.lastCallGameTime && lastGrowthGameTime == cropState.lastGrowthGameTime && lastCallLightLevel == cropState.lastCallLightLevel && lastGrowthLightLevel == cropState.lastGrowthLightLevel && lastAge == cropState.lastAge;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastCallGameTime, lastGrowthGameTime, lastCallLightLevel, lastGrowthLightLevel, lastAge);
    }

    @Override
    public String toString() {
        return "CropState{" +
                "lastCallGameTime=" + lastCallGameTime +
                ", lastGrowthGameTime=" + lastGrowthGameTime +
                ", lastCallLightLevel=" + lastCallLightLevel +
                ", lastGrowthLightLevel=" + lastGrowthLightLevel +
                ", lastAge=" + lastAge +
                '}';
    }

    // TODO replace with RocksDb
//    public static class Serializer implements org.mapdb.Serializer<CropState> {
//
//        @Override
//        public void serialize(@NotNull DataOutput2 out, @NotNull CropState cropState) throws IOException {
//            try {
//                out.writeLong(cropState.getLastCallGameTime());
//                out.writeLong(cropState.getLastGrowthGameTime());
//                out.writeInt(cropState.getLastCallLightLevel());
//                out.writeInt(cropState.getLastGrowthLightLevel());
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        @Override
//        public CropState deserialize(@NotNull DataInput2 input, int i) throws IOException {
//            try {
//                long lastCallGameTime = input.readLong();
//                long lastGrowthGameTime = input.readLong();
//                int lastCallLightLevel = input.readInt();
//                int lastGrowthLightLevel = input.readInt();
//                CropState data =  new CropState();
//                data
//                    .setLastCallGameTime(lastCallGameTime)
//                    .setLastGrowthGameTime(lastGrowthGameTime)
//                    .setLastCallLightLevel(lastCallLightLevel)
//                    .setLastGrowthLightLevel(lastGrowthLightLevel);
//                return data;
//            } catch(IOException e) {
//                throw new RuntimeException();
//            }
//        }
//
//        @Override
//        public int fixedSize() {
//            return 24; // 2 int * 4 bytes + 2 long * 8 bytes
//        }
//
//        @Override
//        public boolean isTrusted() {
//            return true;
//        }
//    }
}
