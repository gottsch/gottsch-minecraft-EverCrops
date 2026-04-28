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
import mod.gottsch.forge.evercrops.core.persistence.CropSavedData;
import mod.gottsch.forge.evercrops.core.persistence.CropState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        );
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

        double minutes = ticks / 1200.0;
        source.sendSuccess(() -> Component.literal(
                String.format("Backdated %d crop entries by %d ticks (%.1f min) within %d blocks. " +
                              "Use '/evercrops tick <radius>' to apply growth immediately.",
                        count, ticks, minutes, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return count;
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

        final int result = triggered;
        source.sendSuccess(() -> Component.literal(
                String.format("Triggered randomTick for %d crop blocks within %d blocks of you.",
                        result, radius))
                .withStyle(ChatFormatting.GREEN), false);
        return triggered;
    }

    // ------------------------------------------------------------------
    // /evercrops inspect [x y z]
    // ------------------------------------------------------------------

    private static int inspect(CommandSourceStack source, BlockPos targetPos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();

        BlockPos pos = targetPos != null
                ? targetPos
                : source.getPlayerOrException().blockPosition();

        CropSavedData data = CropSavedData.getOrCreate(level);
        Optional<CropState> opt = data.get(pos);

        if (opt.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No CropState recorded at " + pos.toShortString() + " in " + level.dimension().location())
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
                "=== EverCrops inspect @ " + pos.toShortString() + " ===")
                .withStyle(ChatFormatting.AQUA), false);
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
                + "  (need callDelta>" + (AVG_CALL_TICK_INTERVAL * 2)
                + " growthDelta>" + (AVG_GROWTH_TICK_INTERVAL * 2) + ")")
                .withStyle(wouldTrigger ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }
}
