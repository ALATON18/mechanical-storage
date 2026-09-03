package com.mechanicalstorage.client.ponder;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.config.MechanicalStorageConfig;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;


public final class MechanicalStoragePonderPlugin implements PonderPlugin {
	@Override
	public String getModId() {
		return MechanicalStorage.MODID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		var components = new ArrayList<ResourceLocation>();
		java.util.List<BlockEntry<?>> blocks = java.util.List.of(
				MechanicalStorage.CONNECTOR,
				MechanicalStorage.COGWHEEL_CONNECTOR,
				MechanicalStorage.OVERFLOW_CONNECTOR,
				MechanicalStorage.COGWHEEL_OVERFLOW_CONNECTOR,
				MechanicalStorage.TERMINAL,
				MechanicalStorage.CRAFTING_TERMINAL);
		for (BlockEntry<?> block : blocks) {
			if (MechanicalStorageConfig.isBlockEnabled(block.get())) {
				components.add(block.getId());
			}
		}
		if (!components.isEmpty()) {
			helper.forComponents(components.toArray(ResourceLocation[]::new))
					.addStoryBoard("storage_network", MechanicalStoragePonderScenes::storageNetwork);
		}
	}
}
