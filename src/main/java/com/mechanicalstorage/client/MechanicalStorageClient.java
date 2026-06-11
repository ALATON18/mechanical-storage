package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class MechanicalStorageClient {
	private MechanicalStorageClient() {
	}

	public static void register(IEventBus modEventBus) {
		modEventBus.addListener(MechanicalStorageClient::registerMenuScreens);
	}

	private static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(MechanicalStorage.TERMINAL_MENU.get(), TerminalScreen::new);
	}
}
