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
package mod.gottsch.forge.evercrops.core.command;

import com.mojang.brigadier.CommandDispatcher;
import mod.gottsch.forge.evercrops.core.persistence.data.CropDataRegistry;
import mod.gottsch.forge.evercrops.core.persistence.data.CropGrowthData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.Map;


/**
 *
 * @author Mark Gottschling on Mar 3, 2025
 *
 */
public class StatsCommand {

	/**
	 *
	 * @param dispatcher
	 */
	public static void register(CommandDispatcher<CommandSource> dispatcher) {
		dispatcher
				.register(Commands.literal("evercrops-stats")
						.requires(source -> {
							return source.hasPermission(2);
						})
						.executes(source -> {
							return stats(source.getSource());
						})
				);
	}

	/**
	 * @param source
	 */
	private static int stats(CommandSource source) {

		int totalCallCount = 0;
		long totalCallDelta = 0L;
		int totalGrowthCount = 0;
		long totalGrowthDelta = 0;

		for (Map.Entry<BlockPos, CropGrowthData> entry : CropDataRegistry.dataMap.getEntries()) {
			totalCallCount += entry.getValue().getCallCount();
			totalCallDelta += entry.getValue().getTotalCallDelta();

			totalGrowthCount += entry.getValue().getGrowthCount();
			totalGrowthDelta += entry.getValue().getTotalGrowthDelta();
		}
		if (totalCallCount > 0) {
			source.sendSuccess(new TranslationTextComponent("call count -> " + String.valueOf(totalCallCount)).withStyle(TextFormatting.GREEN), false);
			source.sendSuccess(new TranslationTextComponent("avg call time -> " + String.valueOf(totalCallDelta / totalCallCount)).withStyle(TextFormatting.GREEN), false);
			source.sendSuccess(new TranslationTextComponent("growth count -> " + String.valueOf(totalGrowthCount)).withStyle(TextFormatting.GREEN), false);
			source.sendSuccess(new TranslationTextComponent("avg growth time -> " + String.valueOf(totalGrowthDelta / totalGrowthCount)).withStyle(TextFormatting.GREEN), false);
		}
		return 1;
	}
}