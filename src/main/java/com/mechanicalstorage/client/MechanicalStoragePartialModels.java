package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

import java.util.stream.IntStream;

public final class MechanicalStoragePartialModels {
	public static final PartialModel[] TERMINAL_SCREEN_FRAMES = IntStream.range(0, 6)
			.mapToObj(frame -> block("terminal_screen_frame_" + frame))
			.toArray(PartialModel[]::new);

	private MechanicalStoragePartialModels() {
	}

	public static void init() {
		// Load this class before model baking so Flywheel registers every screen frame.
	}

	private static PartialModel block(String path) {
		return PartialModel.of(ResourceLocation.fromNamespaceAndPath(MechanicalStorage.MODID, "block/" + path));
	}
}

