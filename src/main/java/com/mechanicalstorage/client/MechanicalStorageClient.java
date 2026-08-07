package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class MechanicalStorageClient {
	private static final StackWalker STACK_WALKER = StackWalker.getInstance();

	private MechanicalStorageClient() {
	}

	public static void register(IEventBus modEventBus) {
		modEventBus.addListener(MechanicalStorageClient::registerMenuScreens);
		modEventBus.addListener(MechanicalStorageClient::registerBlockEntityRenderers);
		NeoForge.EVENT_BUS.addListener(MechanicalStorageClient::addMachineItemTooltip);
	}

	private static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(MechanicalStorage.TERMINAL_MENU.get(), TerminalScreen::new);
	}

	private static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(
				MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY.get(),
				MechanicalStorageShaftRenderer::new);
		event.registerBlockEntityRenderer(
				MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(),
				MechanicalStorageShaftRenderer::new);
	}

	private static void addMachineItemTooltip(ItemTooltipEvent event) {
		if (!event.getItemStack().is(MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR.get().asItem())
				&& !event.getItemStack().is(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL.get().asItem())) {
			return;
		}
		if (isJeiBuildingIngredientTooltip()) {
			return;
		}

		String modName = ModList.get().getModContainerById(MechanicalStorage.MODID)
				.map(container -> container.getModInfo().getDisplayName())
				.orElse("Mechanical Storage");
		if (event.getToolTip().stream().noneMatch(line -> line.getString().equals(modName))) {
			event.getToolTip().add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
		}
	}

	private static boolean isJeiBuildingIngredientTooltip() {
		return STACK_WALKER.walk(frames -> frames
				.map(StackWalker.StackFrame::getClassName)
				.anyMatch(className -> className.equals("mezz.jei.library.render.ItemStackRenderer")));
	}
}
