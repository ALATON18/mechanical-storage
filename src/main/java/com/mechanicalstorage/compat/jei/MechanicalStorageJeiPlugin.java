package com.mechanicalstorage.compat.jei;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.config.MechanicalStorageConfig;
import com.tterrag.registrate.util.entry.BlockEntry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.ArrayList;

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
		List<net.minecraft.world.item.ItemStack> enabledBlocks = new ArrayList<>();
		List<BlockEntry<?>> blocks = List.of(
				MechanicalStorage.CONNECTOR,
				MechanicalStorage.COGWHEEL_CONNECTOR,
				MechanicalStorage.OVERFLOW_CONNECTOR,
				MechanicalStorage.COGWHEEL_OVERFLOW_CONNECTOR,
				MechanicalStorage.TERMINAL,
				MechanicalStorage.CRAFTING_TERMINAL,
				MechanicalStorage.MONITOR,
				MechanicalStorage.DISPATCH);
		for (BlockEntry<?> block : blocks) {
			if (MechanicalStorageConfig.isBlockEnabled(block.get())) {
				enabledBlocks.add(block.asStack());
			}
		}
		registration.addExtraItemStacks(enabledBlocks);
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		if (MechanicalStorageConfig.isBlockEnabled(MechanicalStorage.CRAFTING_TERMINAL.get())) {
			registration.addUniversalRecipeTransferHandler(
					new CraftingTerminalJeiTransferHandler(registration.getTransferHelper()));
		}
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
