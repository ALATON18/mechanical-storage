package com.mechanicalstorage;

import com.mechanicalstorage.block.MechanicalStorageConnectorBlock;
import com.mechanicalstorage.block.MechanicalStorageTerminalBlock;
import com.mechanicalstorage.blockentity.MechanicalStorageConnectorBlockEntity;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.client.MechanicalStorageClient;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MechanicalStorage.MODID)
public class MechanicalStorage {
	public static final String MODID = "mechanical_storage";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	public static final DeferredBlock<Block> MECHANICAL_STORAGE_CONNECTOR = BLOCKS.register("mechanical_storage_connector", () ->
			new MechanicalStorageConnectorBlock(machineProperties(MapColor.COLOR_GRAY)));

	public static final DeferredBlock<Block> MECHANICAL_STORAGE_TERMINAL = BLOCKS.register("mechanical_storage_terminal", () ->
			new MechanicalStorageTerminalBlock(machineProperties(MapColor.TERRACOTTA_ORANGE)));

	public static final DeferredItem<BlockItem> MECHANICAL_STORAGE_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem("mechanical_storage_connector", MECHANICAL_STORAGE_CONNECTOR);
	public static final DeferredItem<BlockItem> MECHANICAL_STORAGE_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem("mechanical_storage_terminal", MECHANICAL_STORAGE_TERMINAL);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanicalStorageConnectorBlockEntity>> MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("mechanical_storage_connector", () ->
			BlockEntityType.Builder.of(MechanicalStorageConnectorBlockEntity::new, MECHANICAL_STORAGE_CONNECTOR.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("mechanical_storage_terminal", () ->
			BlockEntityType.Builder.of(TerminalBlockEntity::new, MECHANICAL_STORAGE_TERMINAL.get()).build(null));

	public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = MENU_TYPES.register("terminal", () ->
			IMenuTypeExtension.create((int containerId, net.minecraft.world.entity.player.Inventory inventory, RegistryFriendlyByteBuf buffer) -> new TerminalMenu(containerId, inventory, buffer)));

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MECHANICAL_STORAGE_TAB = CREATIVE_MODE_TABS.register("mechanical_storage", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mechanical_storage"))
			.icon(() -> MECHANICAL_STORAGE_TERMINAL_ITEM.get().getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(MECHANICAL_STORAGE_CONNECTOR_ITEM.get());
				output.accept(MECHANICAL_STORAGE_TERMINAL_ITEM.get());
			})
			.build());

	public MechanicalStorage(IEventBus modEventBus, ModContainer modContainer) {
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		BLOCK_ENTITY_TYPES.register(modEventBus);
		MENU_TYPES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			MechanicalStorageClient.register(modEventBus);
		}

		LOGGER.info("Mechanical Storage loaded");
	}

	private static BlockBehaviour.Properties machineProperties(MapColor mapColor) {
		return BlockBehaviour.Properties.of()
				.mapColor(mapColor)
				.strength(2.0F, 6.0F)
				.sound(SoundType.COPPER);
	}
}
