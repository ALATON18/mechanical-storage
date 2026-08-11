package com.mechanicalstorage;

import com.mechanicalstorage.block.MechanicalStorageCogwheelConnectorBlock;
import com.mechanicalstorage.block.MechanicalStorageConnectorBlock;
import com.mechanicalstorage.block.MechanicalStorageTerminalBlock;
import com.mechanicalstorage.blockentity.MechanicalStorageConnectorBlockEntity;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.client.MechanicalStorageClient;
import com.mechanicalstorage.contraption.ConnectorMovementBehaviour;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(MechanicalStorage.MODID)
public class MechanicalStorage {
	public static final String MODID = "mechanical_storage";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	public static final BlockEntry<MechanicalStorageConnectorBlock> CONNECTOR = REGISTRATE
			.block("connector", properties -> new MechanicalStorageConnectorBlock(machineProperties(MapColor.COLOR_GRAY)))
			.onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(
					new ConnectorMovementBehaviour()))
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageCogwheelConnectorBlock> COGWHEEL_CONNECTOR = REGISTRATE
			.block("cogwheel_connector", properties -> new MechanicalStorageCogwheelConnectorBlock(machineProperties(MapColor.COLOR_GRAY).noOcclusion()))
			.onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(
					new ConnectorMovementBehaviour()))
			.addLayer(() -> RenderType::cutoutMipped)
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageTerminalBlock> TERMINAL = REGISTRATE
			.block("terminal", properties -> new MechanicalStorageTerminalBlock(machineProperties(MapColor.TERRACOTTA_ORANGE)))
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageTerminalBlock> CRAFTING_TERMINAL = REGISTRATE
			.block("crafting_terminal", properties -> new MechanicalStorageTerminalBlock(machineProperties(MapColor.TERRACOTTA_ORANGE)))
			.item()
			.build()
			.register();

	public static final BlockEntityEntry<MechanicalStorageConnectorBlockEntity> CONNECTOR_BLOCK_ENTITY = REGISTRATE
			.<MechanicalStorageConnectorBlockEntity>blockEntity("connector", MechanicalStorageConnectorBlockEntity::new)
			.validBlocks(CONNECTOR, COGWHEEL_CONNECTOR)
			.register();

	public static final BlockEntityEntry<TerminalBlockEntity> TERMINAL_BLOCK_ENTITY = REGISTRATE
			.<TerminalBlockEntity>blockEntity("terminal", TerminalBlockEntity::new)
			.validBlocks(TERMINAL, CRAFTING_TERMINAL)
			.register();

	public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = MENU_TYPES.register("terminal", () ->
			IMenuTypeExtension.create((int containerId, net.minecraft.world.entity.player.Inventory inventory, RegistryFriendlyByteBuf buffer) -> new TerminalMenu(containerId, inventory, buffer)));

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mechanical_storage"))
			.icon(() -> TERMINAL.get().asItem().getDefaultInstance())
			.build());

	public MechanicalStorage(IEventBus modEventBus, ModContainer modContainer) {
		REGISTRATE.registerEventListeners(modEventBus);
		MENU_TYPES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		modEventBus.addListener(MechanicalStorage::addLegacyRegistryAliases);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			MechanicalStorageClient.register(modEventBus);
		}

		LOGGER.info("Mechanical Storage loaded");
	}

	private static void addLegacyRegistryAliases(RegisterEvent event) {
		Registry<?> registry = event.getRegistry();
		if (registry == Registries.BLOCK || registry == Registries.ITEM) {
			addAlias(registry, "mechanical_storage_connector", "connector");
			addAlias(registry, "mechanical_storage_cogwheel_connector", "cogwheel_connector");
			addAlias(registry, "mechanical_storage_terminal", "terminal");
			addAlias(registry, "mechanical_storage_crafting_terminal", "crafting_terminal");
		} else if (registry == Registries.BLOCK_ENTITY_TYPE) {
			addAlias(registry, "mechanical_storage_connector", "connector");
			addAlias(registry, "mechanical_storage_terminal", "terminal");
		} else if (registry == Registries.CREATIVE_MODE_TAB) {
			addAlias(registry, "mechanical_storage", "main");
		}
	}

	private static void addAlias(Registry<?> registry, String legacyPath, String currentPath) {
		registry.addAlias(id(legacyPath), id(currentPath));
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	private static BlockBehaviour.Properties machineProperties(MapColor mapColor) {
		return BlockBehaviour.Properties.of()
				.mapColor(mapColor)
				.strength(2.0F, 6.0F)
				.sound(SoundType.COPPER);
	}
}
