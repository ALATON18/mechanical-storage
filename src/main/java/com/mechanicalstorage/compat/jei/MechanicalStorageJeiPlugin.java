package com.mechanicalstorage.compat.jei;

import com.mechanicalstorage.MechanicalStorage;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public class MechanicalStorageJeiPlugin implements IModPlugin {
	private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(MechanicalStorage.MODID, "jei_plugin");
	private IJeiRuntime runtime;

	@Override
	public ResourceLocation getPluginUid() {
		return PLUGIN_ID;
	}

	@Override
	public void registerExtraIngredients(IExtraIngredientRegistration registration) {
		registration.addExtraItemStacks(List.of(
				MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR.get().asItem().getDefaultInstance(),
				MechanicalStorage.MECHANICAL_STORAGE_COGWHEEL_CONNECTOR.get().asItem().getDefaultInstance(),
				MechanicalStorage.MECHANICAL_STORAGE_TERMINAL.get().asItem().getDefaultInstance(),
				MechanicalStorage.MECHANICAL_STORAGE_CRAFTING_TERMINAL.get().asItem().getDefaultInstance()
		));
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		JeiSearchBridge.connect(this::getFilterText, this::setFilterText);
	}

	@Override
	public void onRuntimeUnavailable() {
		JeiSearchBridge.disconnect();
		runtime = null;
	}

	private String getFilterText() {
		return runtime == null ? "" : runtime.getIngredientFilter().getFilterText();
	}

	private void setFilterText(String filterText) {
		if (runtime != null) {
			IIngredientFilter ingredientFilter = runtime.getIngredientFilter();
			if (!ingredientFilter.getFilterText().equals(filterText)) {
				ingredientFilter.setFilterText(filterText);
			}
		}
	}
}
