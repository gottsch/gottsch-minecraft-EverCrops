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
package mod.gottsch.forge.evercrops.core.mixin;

import mod.gottsch.forge.evercrops.core.config.Config;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for chorus flowers (End dimension).
 *
 * ChorusFlowerBlock overrides randomTick directly (not inherited from a parent),
 * so this mixin targets ChorusFlowerBlock with no instanceof guard needed.
 *
 * Chorus flower growth is branching and spatial — the flower replaces itself at a
 * new position each step, making multi-step catch-up unsafe. Strategy: register/update
 * timestamps normally; when catch-up fires, remove the stale entry and let vanilla run
 * one natural growth step. The flower's new position (wherever vanilla places it)
 * registers a fresh CropState on its own first tick.
 *
 * Dead flowers (AGE == 5) stop calling randomTick via isRandomlyTicking, so no
 * explicit death handling is required here.
 *
 * AVG_GROWTH_TICK_INTERVAL equals AVG_CALL_TICK_INTERVAL (1350) because chorus
 * flower has no internal probability gate — it grows on every randomTick it receives.
 *
 * @author Mark Gottschling on 5/6/2026
 */
@Mixin(ChorusFlowerBlock.class)
public abstract class ChorusFlowerBlockMixin extends Block {

    @Unique
    private static final int AVG_GROWTH_TICK_INTERVAL = 1350;

    public ChorusFlowerBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"))
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        if (!Config.SERVER.chorusFlowerEnabled.get()) return;
        if (!state.hasProperty(ChorusFlowerBlock.AGE)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            // Wild (worldgen) flowers are tracked only when the player opts in.
            if (Config.SERVER.trackWildVines.get()) {
                CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            }
            return;
        }
        CropState cropState = existing.get();
        int steps = CropCatchUp.beginCatchUp(level, pos, cropState, AVG_GROWTH_TICK_INTERVAL, false);
        if (steps > 0) {
            // Conservative: allow vanilla to run exactly one growth step (no cancel, no loop).
            // The flower replaces itself at a new (unpredictable) position after vanilla runs.
            // Remove the now-stale entry; the new flower registers fresh on its first tick.
            CropRegistry.remove(level, pos);
        } else {
            CropRegistry.put(level, pos, cropState);
        }
    }
}
