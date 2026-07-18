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
 * The beehive flavour of {@link CatchUpState}: an <i>unlit</i> growable whose growth axis is the
 * block's {@code honey_level} (0-5). Beehives have no random tick — honey only advances when a
 * nectar-carrying bee is released by the block entity — so EverCrops both <i>learns</i> each hive's
 * real production rate while it is loaded and replays the owed honey when the chunk reloads.
 *
 * <p>Beyond the two base timestamps this adds:
 * <ul>
 *   <li>{@code lastHoneyLevel} — the honey level observed last tick, to tell a real vanilla delivery
 *       (level rose) from a harvest/reset (level dropped). {@code -1} = not yet observed.</li>
 *   <li>{@code learnedIntervalTicks} — an EMA of the daytime ticks this hive takes to gain one honey
 *       level, measured from real deliveries during loaded play. {@code 0} = nothing learned yet
 *       (catch-up falls back to the configured cold-start interval).</li>
 *   <li>{@code daytimeTicksAccumulated} — daytime ticks counted since the last observed delivery,
 *       i.e. the in-progress sample feeding the next EMA update.</li>
 * </ul>
 *
 * <p>Setters are covariant so chains keep the {@code BeehiveState} type. Honey itself lives in the
 * blockstate, so it is intentionally not stored here.
 *
 * @author Mark Gottschling
 */
public class BeehiveState extends CatchUpState {

    private int lastHoneyLevel = -1;
    private long learnedIntervalTicks = 0L;
    private long daytimeTicksAccumulated = 0L;
    private boolean lastActive = false;

    public BeehiveState() {}

    @Override
    public BeehiveState setLastCallGameTime(long lastCallGameTime) {
        super.setLastCallGameTime(lastCallGameTime);
        return this;
    }

    @Override
    public BeehiveState setLastGrowthGameTime(long lastGrowthGameTime) {
        super.setLastGrowthGameTime(lastGrowthGameTime);
        return this;
    }

    public int getLastHoneyLevel() {
        return lastHoneyLevel;
    }

    public BeehiveState setLastHoneyLevel(int lastHoneyLevel) {
        this.lastHoneyLevel = lastHoneyLevel;
        return this;
    }

    public long getLearnedIntervalTicks() {
        return learnedIntervalTicks;
    }

    public BeehiveState setLearnedIntervalTicks(long learnedIntervalTicks) {
        this.learnedIntervalTicks = learnedIntervalTicks;
        return this;
    }

    public long getDaytimeTicksAccumulated() {
        return daytimeTicksAccumulated;
    }

    public BeehiveState setDaytimeTicksAccumulated(long daytimeTicksAccumulated) {
        this.daytimeTicksAccumulated = daytimeTicksAccumulated;
        return this;
    }

    /**
     * Whether the hive was producing honey (bees present + a living flower) as of its last processed
     * tick. Because bees and flowers can only change while the chunk is loaded, this pre-unload value
     * describes the entire offline gap — so it, not the instantaneous count on the reload tick, decides
     * whether an offline gap earns honey. This keeps a hive whose bees are merely out foraging on the
     * reload tick from losing the credit it earned.
     */
    public boolean isLastActive() {
        return lastActive;
    }

    public BeehiveState setLastActive(boolean lastActive) {
        this.lastActive = lastActive;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BeehiveState that = (BeehiveState) o;
        return lastHoneyLevel == that.lastHoneyLevel
                && learnedIntervalTicks == that.learnedIntervalTicks
                && daytimeTicksAccumulated == that.daytimeTicksAccumulated
                && lastActive == that.lastActive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLastCallGameTime(), getLastGrowthGameTime(),
                lastHoneyLevel, learnedIntervalTicks, daytimeTicksAccumulated, lastActive);
    }

    @Override
    public String toString() {
        return "BeehiveState{" +
                "lastCallGameTime=" + getLastCallGameTime() +
                ", lastGrowthGameTime=" + getLastGrowthGameTime() +
                ", lastHoneyLevel=" + lastHoneyLevel +
                ", learnedIntervalTicks=" + learnedIntervalTicks +
                ", daytimeTicksAccumulated=" + daytimeTicksAccumulated +
                ", lastActive=" + lastActive +
                '}';
    }
}
