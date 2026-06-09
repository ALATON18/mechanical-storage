package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class MechanicalStorageClient {
	private MechanicalStorageClient() {
	}

	public static void register(IEventBus modEventBus) {
		modEventBus.addListener(MechanicalStorageClient::registerMenuScreens);
		modEventBus.addListener(MechanicalStorageClient::registerBlockEntityRenderers);
	}

	private static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(MechanicalStorage.TERMINAL_MENU.get(), TerminalScreen::new);
	}

	private static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY.get(), MechanicalStorageShaftRenderer::new);
		event.registerBlockEntityRenderer(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), MechanicalStorageShaftRenderer::new);
	}
}
