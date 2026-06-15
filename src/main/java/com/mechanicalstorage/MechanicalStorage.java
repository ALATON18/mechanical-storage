package com.mechanicalstorage;

import com.mechanicalstorage.block.MechanicalStorageConnectorBlock;
import com.mechanicalstorage.block.MechanicalStorageTerminalBlock;
import com.mechanicalstorage.blockentity.MechanicalStorageConnectorBlockEntity;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.client.MechanicalStorageClient;
import com.mechanicalstorage.client.MechanicalStorageShaftRenderer;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MechanicalStorage.MODID)
public class MechanicalStorage {
	public static final String MODID = "mechanical_storage";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	public static final BlockEntry<MechanicalStorageConnectorBlock> MECHANICAL_STORAGE_CONNECTOR = REGISTRATE
			.block("mechanical_storage_connector", properties -> new MechanicalStorageConnectorBlock(machineProperties(MapColor.COLOR_GRAY)))
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageTerminalBlock> MECHANICAL_STORAGE_TERMINAL = REGISTRATE
			.block("mechanical_storage_terminal", properties -> new MechanicalStorageTerminalBlock(machineProperties(MapColor.TERRACOTTA_ORANGE)))
			.item()
			.build()
			.register();

	public static final BlockEntityEntry<MechanicalStorageConnectorBlockEntity> MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY = REGISTRATE
			.blockEntity("mechanical_storage_connector", MechanicalStorage::createConnectorBlockEntity)
			.visual(() -> OrientedRotatingVisual.backHorizontal(AllPartialModels.SHAFT_HALF), false)
			.validBlocks(MECHANICAL_STORAGE_CONNECTOR)
			.renderer(() -> MechanicalStorageShaftRenderer::new)
			.register();

	public static final BlockEntityEntry<TerminalBlockEntity> MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY = REGISTRATE
			.blockEntity("mechanical_storage_terminal", MechanicalStorage::createTerminalBlockEntity)
			.visual(() -> OrientedRotatingVisual.backHorizontal(AllPartialModels.SHAFT_HALF), false)
			.validBlocks(MECHANICAL_STORAGE_TERMINAL)
			.renderer(() -> MechanicalStorageShaftRenderer::new)
			.register();

	public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = MENU_TYPES.register("terminal", () ->
			IMenuTypeExtension.create((int containerId, net.minecraft.world.entity.player.Inventory inventory, RegistryFriendlyByteBuf buffer) -> new TerminalMenu(containerId, inventory, buffer)));

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MECHANICAL_STORAGE_TAB = CREATIVE_MODE_TABS.register("mechanical_storage", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mechanical_storage"))
			.icon(() -> MECHANICAL_STORAGE_TERMINAL.get().asItem().getDefaultInstance())
			.build());

	public MechanicalStorage(IEventBus modEventBus, ModContainer modContainer) {
		REGISTRATE.registerEventListeners(modEventBus);
		MENU_TYPES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			MechanicalStorageClient.register(modEventBus);
		}

		LOGGER.info("Mechanical Storage loaded");
	}

	private static MechanicalStorageConnectorBlockEntity createConnectorBlockEntity(
			BlockEntityType<MechanicalStorageConnectorBlockEntity> type,
			BlockPos pos,
			BlockState state) {
		return new MechanicalStorageConnectorBlockEntity(pos, state);
	}

	private static TerminalBlockEntity createTerminalBlockEntity(
			BlockEntityType<TerminalBlockEntity> type,
			BlockPos pos,
			BlockState state) {
		return new TerminalBlockEntity(pos, state);
	}

	private static BlockBehaviour.Properties machineProperties(MapColor mapColor) {
		return BlockBehaviour.Properties.of()
				.mapColor(mapColor)
				.strength(2.0F, 6.0F)
				.sound(SoundType.COPPER);
	}
}
