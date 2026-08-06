package com.mechanicalstorage.compat.jei;

import com.mechanicalstorage.MechanicalStorage;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public class MechanicalStorageJeiPlugin implements IModPlugin {
	private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(MechanicalStorage.MODID, "jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return PLUGIN_ID;
	}

	@Override
	public void registerExtraIngredients(IExtraIngredientRegistration registration) {
		registration.addExtraItemStacks(List.of(
				MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR.get().asItem().getDefaultInstance(),
				MechanicalStorage.MECHANICAL_STORAGE_TERMINAL.get().asItem().getDefaultInstance()
		));
	}
}
