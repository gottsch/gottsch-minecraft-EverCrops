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
package mod.gottsch.forge.evercrops.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import mod.gottsch.forge.evercrops.api.BeehiveState;
import mod.gottsch.forge.evercrops.api.CropBlockPredicates;
import mod.gottsch.forge.evercrops.api.EverCropsApi;
import mod.gottsch.forge.evercrops.core.catchup.BeehiveCatchUp;
import mod.gottsch.forge.evercrops.core.catchup.BeehiveDecision;
import mod.gottsch.forge.evercrops.core.persistence.BeehiveRegistry;
import mod.gottsch.forge.evercrops.core.persistence.BeehiveSavedData;
import mod.gottsch.forge.evercrops.core.persistence.CropRegistry;
import mod.gottsch.forge.evercrops.core.persistence.CropSavedData;
import mod.gottsch.forge.evercrops.api.CropState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Debug/testing commands for EverCrops.
 *
 * /evercrops simulate <ticks>        – backdate all CropState entries in the current
 *                                       dimension; the next randomTick on each crop
 *                                       will immediately enter the offline-growth path.
 *
 * /evercrops tick <radius>           – force a randomTick on every tracked crop within
 *                                       <radius> blocks of the player, triggering growth
 *                                       right now (combine with simulate for instant results).
 *
 * /evercrops inspect                 – show the stored CropState for the block at the
 * /evercrops inspect <x> <y> <z>       player's feet (or at the given coordinates).
 *
 * /evercrops cleanup                 – remove registry entries (in loaded chunks) whose
 *                                       block is no longer a tracked crop, e.g. crops
 *                                       removed by pistons/explosions/other mods that
 *                                       bypass the player BreakEvent.
 *
 * @author Mark Gottschling on 4/25/2026
 */
public class EverCropsCommand {

    private static final int AVG_CALL_TICK_INTERVAL  = 1350;
    private static final int AVG_GROWTH_TICK_INTERVAL = 7000;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("evercrops")
                .requires(source -> source.hasPermission(2))

                // /evercrops simulate <ticks> <radius>
                .then(Commands.literal("simulate")
                    .then(Commands.argument("ticks", LongArgumentType.longArg(1))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                            .executes(ctx -> simulate(
                                    ctx.getSource(),
                                    LongArgumentType.getLong(ctx, "ticks"),
                                    IntegerArgumentType.getInteger(ctx, "radius"))))))

                // /evercrops tick <radius>
                .then(Commands.literal("tick")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> tick(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))))

                // /evercrops inspect  /  /evercrops inspect <x> <y> <z>
                .then(Commands.literal("inspect")
                    .executes(ctx -> inspect(ctx.getSource(), null))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> inspect(
                                ctx.getSource(),
                                BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))

                // /evercrops cleanup
                .then(Commands.literal("cleanup")
                    .executes(ctx -> cleanup(ctx.getSource())))
        );
    }

    // ------------------------------------------------------------------
    // /evercrops cleanup
    // ------------------------------------------------------------------

    /**
     * Immediately removes registry entries in loaded chunks whose block is no longer
     * a tracked crop. The same scan runs automatically on a timer (see ModEvents and
     * the autoCleanup* config), but this forces it on demand.
     */
    private static int cleanup(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int removedCrops = CropRegistry.cleanup(level, CropBlockPredicates::isCropBlock);
        int removedHives = BeehiveRegistry.cleanup(level, EverCropsCommand::isBeehive);
        int removed = removedCrops + removedHives;

        if (removed > 0) {
            source.sendSuccess(() -> Component.literal(
                    String.format("Removed %d stale entr%s from %s (%d crop, %d hive; block no longer tracked in loaded chunks).",
                            removed, removed == 1 ? "y" : "ies", level.dimension().location(), removedCrops, removedHives))
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "No stale entries found in loaded chunks of " + level.dimension().location() + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return removed;
    }

    /** A block state is a beehive/bee-nest (both use {@link BeehiveBlock}) still worth tracking for honey catch-up. */
    static boolean isBeehive(BlockState state) {
        return state.getBlock() instanceof BeehiveBlock;
    }

    // ------------------------------------------------------------------
    // /evercrops simulate <ticks>
    // ------------------------------------------------------------------

    private static int simulate(CommandSourceStack source, long ticks, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        CropSavedData data = CropSavedData.getOrCreate(level);
        int count = data.backdateInRadius(origin, radius, ticks);
        int hiveCount = BeehiveSavedData.getOrCreate(level).backdateInRadius(origin, radius, ticks);

        double minutes = ticks / 1200.0;
        source.sendSuccess(() -> Component.literal(
                String.format("Backdated %d crop and %d hive entries by %d ticks (%.1f min) within %d blocks. " +
                              "Use '/evercrops tick <radius>' to apply growth immediately.",
                        count, hiveCount, ticks, minutes, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return count + hiveCount;
    }

    // ------------------------------------------------------------------
    // /evercrops tick <radius>
    // ------------------------------------------------------------------

    private static int tick(CommandSourceStack source, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();

        CropSavedData data = CropSavedData.getOrCreate(level);
        long radiusSq = (long) radius * radius;
        int triggered = 0;

        List<Long> keySnapshot = new ArrayList<>(data.getKeys());
        for (long packedPos : keySnapshot) {
            BlockPos pos = BlockPos.of(packedPos);
            if (pos.distSqr(origin) <= radiusSq) {
                BlockState state = level.getBlockState(pos);
                // only tick blocks that are actually loaded and randomly ticking
                if (state.isRandomlyTicking()) {
                    state.randomTick(level, pos, level.getRandom());
                    triggered++;
                }
            }
        }

        // Beehives have no randomTick — drive their catch-up routine directly (same path the
        // serverTick mixin uses), so backdated hives apply their owed honey right now.
        int hivesTriggered = 0;
        List<Long> hiveKeys = new ArrayList<>(BeehiveSavedData.getOrCreate(level).getKeys());
        for (long packedPos : hiveKeys) {
            BlockPos pos = BlockPos.of(packedPos);
            if (pos.distSqr(origin) <= radiusSq && level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof BeehiveBlockEntity be) {
                BeehiveCatchUp.onServerTick(level, pos, level.getBlockState(pos), be);
                hivesTriggered++;
            }
        }

        final int result = triggered;
        final int hiveResult = hivesTriggered;
        source.sendSuccess(() -> Component.literal(
                String.format("Triggered catch-up for %d crop blocks and %d hives within %d blocks of you.",
                        result, hiveResult, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return triggered + hivesTriggered;
    }

    // ------------------------------------------------------------------
    // /evercrops inspect [x y z]
    // ------------------------------------------------------------------

    private static int inspect(CommandSourceStack source, BlockPos targetPos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();

        BlockPos pos = targetPos != null
                ? targetPos
                : source.getPlayerOrException().blockPosition();

        BlockState blockState = level.getBlockState(pos);
        CropSavedData data = CropSavedData.getOrCreate(level);
        Optional<CropState> opt = data.get(pos);

        source.sendSuccess(() -> Component.literal(
                "=== EverCrops inspect @ " + pos.toShortString() + " ===")
                .withStyle(ChatFormatting.AQUA), false);

        // Always report the actual block and its numeric growth properties (age, stage,
        // colony_age, composting, ...). Saplings advance an invisible STAGE 0 -> 1 before
        // they can become a tree, so this surfaces otherwise-invisible catch-up progress.
        source.sendSuccess(() -> Component.literal(
                "  block              : " + BuiltInRegistries.BLOCK.getKey(blockState.getBlock()))
                .withStyle(ChatFormatting.WHITE), false);
        String growthProps = blockState.getProperties().stream()
                .filter(p -> p instanceof IntegerProperty)
                .map(p -> p.getName() + "=" + blockState.getValue(p))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(
                "  growth properties  : " + (growthProps.isEmpty() ? "(none)" : growthProps))
                .withStyle(ChatFormatting.WHITE), false);

        // Beehives track a different kind of state (honey level + learned production rate), in their
        // own store — report that instead of the crop section.
        if (blockState.getBlock() instanceof BeehiveBlock) {
            return inspectBeehive(source, level, pos, blockState);
        }

        // Turtle eggs ride the ordinary crop store, so they fall through to the CropState section
        // below — but their eligibility and pace come from elsewhere, so surface those first.
        if (blockState.getBlock() instanceof TurtleEggBlock) {
            inspectTurtleEggExtras(source, level, pos, blockState);
        }

        if (opt.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "  tracked            : no — no CropState recorded (not registered for catch-up)")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        CropState state = opt.get();
        long now = level.getGameTime();
        long callDelta   = now - state.getLastCallGameTime();
        long growthDelta = now - state.getLastGrowthGameTime();
        boolean wouldTrigger = callDelta > AVG_CALL_TICK_INTERVAL * 2L
                            && growthDelta > AVG_GROWTH_TICK_INTERVAL * 2L;

        source.sendSuccess(() -> Component.literal(
                "  lastCallGameTime   : " + state.getLastCallGameTime()
                + "  (delta: " + callDelta + " ticks / " + String.format("%.1f", callDelta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  lastGrowthGameTime : " + state.getLastGrowthGameTime()
                + "  (delta: " + growthDelta + " ticks / " + String.format("%.1f", growthDelta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  lastCallLight      : " + state.getLastCallLightLevel())
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  lastGrowthLight    : " + state.getLastGrowthLightLevel())
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  offline growth?    : " + (wouldTrigger ? "YES" : "no")
                + "  (approx — need callDelta>" + (AVG_CALL_TICK_INTERVAL * 2)
                + " growthDelta>" + (AVG_GROWTH_TICK_INTERVAL * 2)
                + "; per-crop interval varies, e.g. saplings need ~18900)")
                .withStyle(wouldTrigger ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /evercrops inspect — turtle egg supplement
    // ------------------------------------------------------------------

    /**
     * Turtle-egg-specific inspect lines, printed above the shared CropState section: the live sand
     * requirement (eggs off sand never progress, in vanilla or in catch-up), the configured pace,
     * and whether catch-up is allowed to finish the hatch.
     */
    private static void inspectTurtleEggExtras(CommandSourceStack source, ServerLevel level,
                                               BlockPos pos, BlockState blockState) {
        boolean onSand = TurtleEggBlock.onSand(level, pos);
        int interval = EverCropsApi.config().turtleEggHatchIntervalTicks();
        int hatch = blockState.getValue(TurtleEggBlock.HATCH);
        int remaining = Math.max(0, TurtleEggBlock.MAX_HATCH_LEVEL + 1 - hatch);

        source.sendSuccess(() -> Component.literal(
                "  on sand            : " + (onSand ? "yes" : "no — will not hatch (vanilla or catch-up)"))
                .withStyle(onSand ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal(
                "  eggs in cluster    : " + blockState.getValue(TurtleEggBlock.EGGS)
                + "  (turtles released on hatch)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  hatch interval     : " + interval + " ticks/stage, " + remaining + " stage(s) left"
                + "  (~" + String.format("%.1f", remaining * interval / 24000.0) + " in-game days)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  may spawn turtles  : " + (EverCropsApi.config().turtleEggSpawnTurtles()
                        ? "yes" : "no — catch-up stops at fully cracked (turtleEggSpawnTurtles = false)"))
                .withStyle(ChatFormatting.WHITE), false);
    }

    // ------------------------------------------------------------------
    // /evercrops inspect — beehive branch
    // ------------------------------------------------------------------

    /**
     * Beehive-specific inspect output: honey level, the learned vs. effective production interval,
     * the bees/flower eligibility signals, and whether catch-up would currently fire.
     */
    private static int inspectBeehive(CommandSourceStack source, ServerLevel level, BlockPos pos, BlockState blockState) {
        int honey = blockState.getValue(BeehiveBlock.HONEY_LEVEL);
        source.sendSuccess(() -> Component.literal(
                "  honey level        : " + honey + " / " + BeehiveBlock.MAX_HONEY_LEVELS)
                .withStyle(ChatFormatting.WHITE), false);

        Optional<BeehiveState> opt = BeehiveRegistry.get(level, pos);
        if (opt.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "  tracked            : no — no BeehiveState recorded "
                    + (EverCropsApi.config().beehivesEnabled() ? "(awaiting first tick)" : "(beehivesEnabled = false)"))
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        BeehiveState state = opt.get();

        // Eligibility signals come from the live block entity.
        int occupants = -1;
        boolean hasFlower = false;
        boolean active = false;
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity be) {
            occupants = be.getOccupantCount();
            hasFlower = BeehiveCatchUp.hasValidFlower(level, be);
            active = BeehiveCatchUp.isActive(level, be, state);
        }

        long now = level.getGameTime();
        long callDelta = now - state.getLastCallGameTime();
        long growthDelta = now - state.getLastGrowthGameTime();
        long learned = state.getLearnedIntervalTicks();
        int fallback = EverCropsApi.config().beehiveHoneyIntervalTicks();
        int effective = BeehiveDecision.effectiveInterval(state, fallback, BeehiveCatchUp.DAYTIME_FRACTION);
        // Offline credit is gated on the hive's producing-state as of its last loaded tick, not the
        // instantaneous count — so predict with that, matching what the catch-up routine actually does.
        boolean wouldTrigger = state.isLastActive()
                && honey < BeehiveBlock.MAX_HONEY_LEVELS
                && callDelta > BeehiveCatchUp.AVG_BE_CALL_INTERVAL * 2L
                && growthDelta > effective * 2L;

        final int occ = occupants;
        final boolean flower = hasFlower;
        final boolean act = active;
        source.sendSuccess(() -> Component.literal(
                "  lastCallGameTime   : " + state.getLastCallGameTime()
                + "  (delta: " + callDelta + " ticks / " + String.format("%.1f", callDelta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  lastGrowthGameTime : " + state.getLastGrowthGameTime()
                + "  (delta: " + growthDelta + " ticks / " + String.format("%.1f", growthDelta / 1200.0) + " min)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  learned interval   : " + (learned > 0
                        ? learned + " ticks/level (measured)"
                        : "(not yet learned — using config fallback " + fallback + ")"))
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  effective interval : " + effective + " ticks/level (after " + BeehiveCatchUp.DAYTIME_FRACTION + " daytime fraction)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  bees / flower      : " + (occ < 0 ? "?" : occ) + " in hive, "
                + (flower ? "living flower in range" : "no flower in range"))
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  eligible (active)  : " + (act ? "YES" : "no — needs a living flower in range + bees (or a learned rate)"))
                .withStyle(act ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal(
                "  producing at unload: " + (state.isLastActive() ? "yes" : "no") + "  (gates offline credit)")
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(
                "  offline growth?    : " + (wouldTrigger ? "YES" : "no"))
                .withStyle(wouldTrigger ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }
}
