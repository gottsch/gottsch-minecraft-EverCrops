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

import java.util.Objects;

/**
 * Base catch-up state POJO (v4 public API): the two timestamps every catch-up growable needs —
 * when it was last random-ticked ({@code lastCallGameTime}) and when it last actually grew
 * ({@code lastGrowthGameTime}). Plain Java, no loader or Minecraft imports, so it ports verbatim
 * across loaders and is trivially unit-testable.
 *
 * <p>Subclasses add their own axes: {@link CropState} adds light levels and a last-age field; a
 * future tree/beehive state would add whatever it needs. Setters return {@code this} for chaining
 * and are overridden covariantly by subclasses so chains keep the subclass type.
 *
 * @author Mark Gottschling
 */
public class CatchUpState {

    private long lastCallGameTime;
    private long lastGrowthGameTime;

    public CatchUpState() {}

    public long getLastCallGameTime() {
        return lastCallGameTime;
    }

    public CatchUpState setLastCallGameTime(long lastCallGameTime) {
        this.lastCallGameTime = lastCallGameTime;
        return this;
    }

    public long getLastGrowthGameTime() {
        return lastGrowthGameTime;
    }

    public CatchUpState setLastGrowthGameTime(long lastGrowthGameTime) {
        this.lastGrowthGameTime = lastGrowthGameTime;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CatchUpState that = (CatchUpState) o;
        return lastCallGameTime == that.lastCallGameTime && lastGrowthGameTime == that.lastGrowthGameTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastCallGameTime, lastGrowthGameTime);
    }

    @Override
    public String toString() {
        return "CatchUpState{lastCallGameTime=" + lastCallGameTime
                + ", lastGrowthGameTime=" + lastGrowthGameTime + '}';
    }
}
