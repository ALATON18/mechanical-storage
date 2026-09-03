package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.client.ponder.MechanicalStoragePonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class MechanicalStorageClient {
	private static final StackWalker STACK_WALKER = StackWalker.getInstance();

	private MechanicalStorageClient() {
	}

	public static void register(IEventBus modEventBus) {
		MechanicalStoragePartialModels.init();
		modEventBus.addListener(MechanicalStorageClient::clientSetup);
		modEventBus.addListener(MechanicalStorageClient::registerMenuScreens);
		modEventBus.addListener(MechanicalStorageClient::registerBlockEntityRenderers);
		NeoForge.EVENT_BUS.addListener(MechanicalStorageClient::addMachineItemTooltip);
	}

	private static void clientSetup(FMLClientSetupEvent event) {
		PonderIndex.addPlugin(new MechanicalStoragePonderPlugin());
	}

	private static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(MechanicalStorage.TERMINAL_MENU.get(), TerminalScreen::new);
	}

	private static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(
				MechanicalStorage.CONNECTOR_BLOCK_ENTITY.get(),
				MechanicalStorageShaftRenderer::new);
		event.registerBlockEntityRenderer(
				MechanicalStorage.TERMINAL_BLOCK_ENTITY.get(),
				MechanicalStorageShaftRenderer::new);
		event.registerBlockEntityRenderer(
				MechanicalStorage.LOGISTICS_BLOCK_ENTITY.get(),
				MechanicalStorageShaftRenderer::new);
	}

	private static void addMachineItemTooltip(ItemTooltipEvent event) {
		if (isJeiBuildingIngredientTooltip()) {
			return;
		}

		ItemStack stack = event.getItemStack();
		boolean inTerminal = Minecraft.getInstance().screen instanceof TerminalScreen;
		boolean mechanicalStorageItem = stack.is(MechanicalStorage.CONNECTOR.get().asItem())
				|| stack.is(MechanicalStorage.COGWHEEL_CONNECTOR.get().asItem())
				|| stack.is(MechanicalStorage.OVERFLOW_CONNECTOR.get().asItem())
				|| stack.is(MechanicalStorage.COGWHEEL_OVERFLOW_CONNECTOR.get().asItem())
				|| stack.is(MechanicalStorage.TERMINAL.get().asItem())
				|| stack.is(MechanicalStorage.CRAFTING_TERMINAL.get().asItem())
				|| stack.is(MechanicalStorage.MONITOR.get().asItem())
				|| stack.is(MechanicalStorage.DISPATCH.get().asItem());

		if (!inTerminal && !mechanicalStorageItem) {
			return;
		}
		if (!inTerminal && stack.is(MechanicalStorage.MONITOR.get().asItem())) {
			event.getToolTip().add(Component.translatable("tooltip.mechanical_storage.monitor")
					.withStyle(ChatFormatting.GRAY));
			event.getToolTip().add(Component.translatable("tooltip.mechanical_storage.rear_shaft")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (!inTerminal && stack.is(MechanicalStorage.DISPATCH.get().asItem())) {
			event.getToolTip().add(Component.translatable("tooltip.mechanical_storage.dispatch")
					.withStyle(ChatFormatting.GRAY));
			event.getToolTip().add(Component.translatable("tooltip.mechanical_storage.dispatch_packager")
					.withStyle(ChatFormatting.DARK_GRAY));
			event.getToolTip().add(Component.translatable("tooltip.mechanical_storage.rear_shaft")
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		String modId = stack.getItem().getCreatorModId(stack);
		if (modId == null || modId.isBlank()) {
			return;
		}

		String modName = ModList.get().getModContainerById(modId)
				.map(container -> container.getModInfo().getDisplayName())
				.orElseGet(() -> humanizeModId(modId));
		if (event.getToolTip().stream().noneMatch(line -> line.getString().equals(modName))) {
			event.getToolTip().add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
		}
	}

	private static String humanizeModId(String modId) {
		StringBuilder name = new StringBuilder(modId.length());
		boolean capitalizeNext = true;
		for (int index = 0; index < modId.length(); index++) {
			char character = modId.charAt(index);
			if (character == '_' || character == '-') {
				name.append(' ');
				capitalizeNext = true;
			} else {
				name.append(capitalizeNext ? Character.toUpperCase(character) : character);
				capitalizeNext = false;
			}
		}
		return name.toString();
	}

	private static boolean isJeiBuildingIngredientTooltip() {
		return STACK_WALKER.walk(frames -> frames
				.map(StackWalker.StackFrame::getClassName)
				.anyMatch(className -> className.equals("mezz.jei.library.render.ItemStackRenderer")));
	}
}
