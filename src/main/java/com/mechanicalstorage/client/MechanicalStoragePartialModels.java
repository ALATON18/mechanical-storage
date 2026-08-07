package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class MechanicalStoragePartialModels {
	public static final PartialModel TERMINAL_SCREEN_SCANLINE = block("terminal_screen_scanline");

	private MechanicalStoragePartialModels() {
	}

	public static void init() {
		// Load this class before model baking so Flywheel registers the partial model.
	}

	private static PartialModel block(String path) {
		return PartialModel.of(ResourceLocation.fromNamespaceAndPath(MechanicalStorage.MODID, "block/" + path));
	}
}
