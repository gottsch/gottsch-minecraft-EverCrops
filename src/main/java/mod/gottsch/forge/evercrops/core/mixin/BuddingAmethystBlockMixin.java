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

import mod.gottsch.forge.evercrops.api.CropState;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.catchup.AmethystStrategy;
import mod.gottsch.forge.evercrops.core.catchup.CatchUpEngine;
import mod.gottsch.forge.evercrops.core.persistence.CropCatchUp;
import mod.gottsch.forge.evercrops.core.persistence.CropEligibility;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Catch-up growth for budding amethyst. The growth action lives in {@code AmethystStrategy}; this is
 * the random-tick wiring.
 *
 * <p><b>Always tracked on first tick, with no wild-tracking opt-in.</b> Budding amethyst has an empty
 * loot table — it drops nothing even to Silk Touch — so it cannot be obtained or placed in survival,
 * and every one in a world is worldgen. An opt-in toggle would therefore leave the feature doing
 * nothing at all. Bloat stays bounded because entries are only created for blocks close enough to a
 * player to be random-ticked in the first place.
 *
 * <p>No light gate: vanilla's amethyst growth has none, and geodes are dark.
 *
 * @author Mark Gottschling
 */
@Mixin(BuddingAmethystBlock.class)
public abstract class BuddingAmethystBlockMixin extends AmethystBlock {

    public BuddingAmethystBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    public void everCrops_randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;

        Optional<CropState> existing = CropRegistry.get(level, pos);
        if (existing.isEmpty()) {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
            return;
        }
        CropState cropState = existing.get();

        boolean grew = CatchUpEngine.run(level, pos, state, cropState,
                EverCropsApi.config().amethystGrowthIntervalTicks(), false, randomSource,
                AmethystStrategy.INSTANCE);

        // The budding block is never consumed by its own growth — only its neighbours change — so the
        // entry always stays exactly where it is.
        CropRegistry.put(level, pos, cropState);

        if (grew) {
            ci.cancel();
        }
    }

    /**
     * Clock stamp for vanilla's own growth.
     *
     * <p>The target here is deliberately <i>not</i> the {@code setBlockAndUpdate} that actually
     * places a bud. Vanilla passes its one-in-five gate and then frequently grows nothing, because
     * the face it picked at random is walled in — which is the normal case inside a geode. Stamping
     * only on real growth would let {@code lastGrowthGameTime} drift on a mostly-enclosed block and
     * fire catch-up during ordinary loaded play.
     *
     * <p>{@code getBlockState} is called exactly once in {@code randomTick}, inside that gate and
     * before any branch, which makes it an exact marker for "a growth opportunity happened" — and an
     * opportunity, not a success, is what {@code amethystGrowthIntervalTicks} measures.
     */
    @Inject(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    public void everCrops_randomTick_opportunity(BlockState state, ServerLevel level, BlockPos pos,
                                                 RandomSource randomSource, CallbackInfo ci) {
        if (!CropEligibility.isTrackingEnabled(state)) return;
        Optional<CropState> cropState = CropRegistry.get(level, pos);
        if (cropState.isPresent()) {
            cropState.get().setLastGrowthGameTime(level.getGameTime())
                    .setLastGrowthLightLevel(level.getRawBrightness(pos, 0));
            CropRegistry.put(level, pos, cropState.get());
        } else {
            CropRegistry.put(level, pos, CropCatchUp.createState(level, pos));
        }
    }
}
