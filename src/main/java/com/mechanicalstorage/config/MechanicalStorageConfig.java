package com.mechanicalstorage.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common, modpack-controlled availability switches for Mechanical Storage.
 *
 * <p>Blocks stay registered so changing the config cannot corrupt an existing
 * world. A disabled block stops operating and is omitted from normal discovery
 * paths such as recipes, the recipe book, JEI and the creative tab.</p>
 */
public final class MechanicalStorageConfig {
	public static final ModConfigSpec SPEC;
	private static final Values VALUES;

	static {
		var configured = new ModConfigSpec.Builder().configure(Values::new);
		VALUES = configured.getLeft();
		SPEC = configured.getRight();
	}

	private MechanicalStorageConfig() {
	}

	public static boolean isBlockEnabled(Block block) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		return id == null || !id.getNamespace().equals("mechanical_storage") || isBlockEnabled(id.getPath());
	}

	public static boolean isBlockEnabled(String blockId) {
		// Data generators and early registry construction run before a config file is
		// attached. Defaults are all enabled, so that phase should see the same values.
		if (!SPEC.isLoaded()) {
			return true;
		}

		String path = blockId.startsWith("mechanical_storage:")
				? blockId.substring("mechanical_storage:".length())
				: blockId;
		return switch (path) {
			case "connector" -> VALUES.connector.get();
			case "cogwheel_connector" -> VALUES.cogwheelConnector.get();
			case "overflow_connector" -> VALUES.overflowConnector.get();
			case "cogwheel_overflow_connector" -> VALUES.cogwheelOverflowConnector.get();
			case "terminal" -> VALUES.terminal.get();
			case "crafting_terminal" -> VALUES.craftingTerminal.get();
			case "monitor" -> VALUES.monitor.get();
			case "dispatch" -> VALUES.dispatch.get();
			default -> false;
		};
	}

	private static final class Values {
		private final ModConfigSpec.BooleanValue connector;
		private final ModConfigSpec.BooleanValue cogwheelConnector;
		private final ModConfigSpec.BooleanValue overflowConnector;
		private final ModConfigSpec.BooleanValue cogwheelOverflowConnector;
		private final ModConfigSpec.BooleanValue terminal;
		private final ModConfigSpec.BooleanValue craftingTerminal;
		private final ModConfigSpec.BooleanValue monitor;
		private final ModConfigSpec.BooleanValue dispatch;

		private Values(ModConfigSpec.Builder builder) {
			builder.comment(
					"Enable or disable individual Mechanical Storage blocks.",
					"Disabled blocks remain registered for world safety, but stop operating and are hidden from recipes and item listings.")
					.push("blocks");
			connector = enabled(builder, "connector", "Connector");
			cogwheelConnector = enabled(builder, "cogwheelConnector", "Cogwheel Connector");
			overflowConnector = enabled(builder, "overflowConnector", "Overflow Connector");
			cogwheelOverflowConnector = enabled(builder, "cogwheelOverflowConnector", "Cogwheel Overflow Connector");
			terminal = enabled(builder, "terminal", "Terminal");
			craftingTerminal = enabled(builder, "craftingTerminal", "Crafting Terminal");
			monitor = enabled(builder, "monitor", "Monitor");
			dispatch = enabled(builder, "dispatch", "Dispatch");
			builder.pop();
		}

		private static ModConfigSpec.BooleanValue enabled(ModConfigSpec.Builder builder, String key, String name) {
			return builder.comment("Whether the " + name + " is available and operational.")
					.define(key, true);
		}
	}
}
