package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MechanicalStorage.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MechanicalStorageClient {
	@SubscribeEvent
	public static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(MechanicalStorage.TERMINAL_MENU.get(), TerminalScreen::new);
	}
}
