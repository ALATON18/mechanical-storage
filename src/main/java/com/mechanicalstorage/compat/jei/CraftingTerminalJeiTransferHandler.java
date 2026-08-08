package com.mechanicalstorage.compat.jei;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.menu.TerminalMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class CraftingTerminalJeiTransferHandler implements IUniversalRecipeTransferHandler<TerminalMenu> {
	private final IRecipeTransferHandlerHelper transferHelper;

	public CraftingTerminalJeiTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
		this.transferHelper = transferHelper;
	}

	@Override
	public Class<? extends TerminalMenu> getContainerClass() {
		return TerminalMenu.class;
	}

	@Override
	public Optional<MenuType<TerminalMenu>> getMenuType() {
		return Optional.of(MechanicalStorage.TERMINAL_MENU.get());
	}

	@Override
	@Nullable
	public IRecipeTransferError transferRecipe(TerminalMenu container, Object recipe,
			IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
		if (!container.isCraftingTerminal()) {
			return transferHelper.createUserErrorWithTooltip(
					Component.literal("Open a Mechanical Storage Crafting Terminal to pull recipe ingredients."));
		}

		List<IRecipeSlotView> inputSlots = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
		if (inputSlots.isEmpty()) {
			return transferHelper.createUserErrorWithTooltip(Component.literal("This recipe has no item inputs to pull."));
		}

		boolean preserveGridPositions = inputSlots.size() == TerminalMenu.CRAFTING_INPUT_SLOTS;
		int itemInputCount = 0;
		for (IRecipeSlotView inputSlot : inputSlots) {
			if (inputSlot.getItemStacks().findAny().isPresent()) {
				itemInputCount++;
			}
		}
		if (itemInputCount == 0) {
			return transferHelper.createUserErrorWithTooltip(Component.literal("This recipe has no item inputs to pull."));
		}
		if (!preserveGridPositions && itemInputCount > TerminalMenu.CRAFTING_INPUT_SLOTS) {
			return transferHelper.createUserErrorWithTooltip(Component.literal("This recipe has more than 9 item inputs."));
		}

		if (!doTransfer) {
			return null;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gameMode == null) {
			return transferHelper.createInternalError();
		}

		minecraft.gameMode.handleInventoryButtonClick(container.containerId,
				maxTransfer ? TerminalMenu.JEI_TRANSFER_BEGIN_MAX_BUTTON : TerminalMenu.JEI_TRANSFER_BEGIN_BUTTON);

		int packedSlot = 0;
		for (int inputIndex = 0; inputIndex < inputSlots.size(); inputIndex++) {
			IRecipeSlotView inputSlot = inputSlots.get(inputIndex);
			List<ItemStack> options = inputSlot.getItemStacks()
					.filter(stack -> !stack.isEmpty())
					.limit(64)
					.toList();
			if (options.isEmpty()) {
				continue;
			}

			int targetSlot = preserveGridPositions ? inputIndex : packedSlot++;
			if (targetSlot >= TerminalMenu.CRAFTING_INPUT_SLOTS) {
				break;
			}

			for (ItemStack option : options) {
				int rawItemId = BuiltInRegistries.ITEM.getId(option.getItem());
				int buttonId = TerminalMenu.encodeJeiTransferItemButton(targetSlot, rawItemId,
						Math.max(1, Math.min(64, option.getCount())));
				if (buttonId >= 0) {
					minecraft.gameMode.handleInventoryButtonClick(container.containerId, buttonId);
				}
			}
		}

		minecraft.gameMode.handleInventoryButtonClick(container.containerId, TerminalMenu.JEI_TRANSFER_FINISH_BUTTON);
		return null;
	}
}
