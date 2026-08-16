package com.mechanicalstorage.client.ponder;

import com.mechanicalstorage.MechanicalStorage;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class MechanicalStoragePonderPlugin implements PonderPlugin {
	@Override
	public String getModId() {
		return MechanicalStorage.MODID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		helper.forComponents(
				MechanicalStorage.CONNECTOR.getId(),
				MechanicalStorage.COGWHEEL_CONNECTOR.getId(),
				MechanicalStorage.TERMINAL.getId(),
				MechanicalStorage.CRAFTING_TERMINAL.getId())
			.addStoryBoard("storage_network", MechanicalStoragePonderScenes::storageNetwork);
	}
}
