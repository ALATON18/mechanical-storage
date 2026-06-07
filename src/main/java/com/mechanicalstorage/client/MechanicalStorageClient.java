package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MechanicalStorage.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MechanicalStorageClient {
	@SubscribeEvent
	public static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(MechanicalStorage.TERMINAL_MENU.get(), TerminalScreen::new);
	}

	@SubscribeEvent
	public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY.get(), ShaftRenderer::new);
		event.registerBlockEntityRenderer(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), ShaftRenderer::new);
	}
}