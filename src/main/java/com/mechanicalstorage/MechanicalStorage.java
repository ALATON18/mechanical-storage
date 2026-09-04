package com.mechanicalstorage;

import com.mechanicalstorage.block.MechanicalStorageCogwheelConnectorBlock;
import com.mechanicalstorage.block.MechanicalStorageConnectorBlock;
import com.mechanicalstorage.block.MechanicalStorageLogisticsBlock;
import com.mechanicalstorage.block.MechanicalStorageTerminalBlock;
import com.mechanicalstorage.blockentity.MechanicalStorageConnectorBlockEntity;
import com.mechanicalstorage.blockentity.MechanicalStorageLogisticsBlockEntity;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.client.MechanicalStorageClient;
import com.mechanicalstorage.config.BlockEnabledCondition;
import com.mechanicalstorage.config.MechanicalStorageConfig;
import com.mechanicalstorage.contraption.ConnectorMovementBehaviour;
import com.mechanicalstorage.contraption.TerminalMovementBehaviour;
import com.mechanicalstorage.contraption.TerminalMovingInteraction;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(MechanicalStorage.MODID)
public class MechanicalStorage {
	public static final String MODID = "mechanical_storage";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
			.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
	public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
			DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
	public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<BlockEnabledCondition>> BLOCK_ENABLED_CONDITION =
			CONDITION_CODECS.register("block_enabled", () -> BlockEnabledCondition.CODEC);

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

	public static final BlockEntry<MechanicalStorageConnectorBlock> OVERFLOW_CONNECTOR = REGISTRATE
			.block("overflow_connector", properties -> new MechanicalStorageConnectorBlock(
					machineProperties(MapColor.TERRACOTTA_ORANGE), true))
			.onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(
					new ConnectorMovementBehaviour()))
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageCogwheelConnectorBlock> COGWHEEL_OVERFLOW_CONNECTOR = REGISTRATE
			.block("cogwheel_overflow_connector", properties -> new MechanicalStorageCogwheelConnectorBlock(
					machineProperties(MapColor.TERRACOTTA_ORANGE).noOcclusion(), true))
			.onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(
					new ConnectorMovementBehaviour()))
			.addLayer(() -> RenderType::cutoutMipped)
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageTerminalBlock> TERMINAL = REGISTRATE
			.block("terminal", properties -> new MechanicalStorageTerminalBlock(machineProperties(MapColor.TERRACOTTA_ORANGE)))
			.onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(
					new TerminalMovementBehaviour()))
			.onRegister(com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.interactionBehaviour(
					new TerminalMovingInteraction()))
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageTerminalBlock> CRAFTING_TERMINAL = REGISTRATE
			.block("crafting_terminal", properties -> new MechanicalStorageTerminalBlock(machineProperties(MapColor.TERRACOTTA_ORANGE)))
			.onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(
					new TerminalMovementBehaviour()))
			.onRegister(com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.interactionBehaviour(
					new TerminalMovingInteraction()))
			.item()
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageLogisticsBlock> MONITOR = REGISTRATE
			.block("monitor", properties -> new MechanicalStorageLogisticsBlock(
					machineProperties(MapColor.TERRACOTTA_ORANGE), MechanicalStorageLogisticsBlock.Role.MONITOR))
			.item(LogisticallyLinkedBlockItem::new)
			.build()
			.register();

	public static final BlockEntry<MechanicalStorageLogisticsBlock> DISPATCH = REGISTRATE
			.block("dispatch", properties -> new MechanicalStorageLogisticsBlock(
					machineProperties(MapColor.COLOR_YELLOW), MechanicalStorageLogisticsBlock.Role.DISPATCH))
			.item(LogisticallyLinkedBlockItem::new)
			.build()
			.register();

	public static final BlockEntityEntry<MechanicalStorageConnectorBlockEntity> CONNECTOR_BLOCK_ENTITY = REGISTRATE
			.<MechanicalStorageConnectorBlockEntity>blockEntity("connector", MechanicalStorageConnectorBlockEntity::new)
			.validBlocks(CONNECTOR, COGWHEEL_CONNECTOR, OVERFLOW_CONNECTOR, COGWHEEL_OVERFLOW_CONNECTOR)
			.register();

	public static final BlockEntityEntry<TerminalBlockEntity> TERMINAL_BLOCK_ENTITY = REGISTRATE
			.<TerminalBlockEntity>blockEntity("terminal", TerminalBlockEntity::new)
			.validBlocks(TERMINAL, CRAFTING_TERMINAL)
			.register();

	public static final BlockEntityEntry<MechanicalStorageLogisticsBlockEntity> LOGISTICS_BLOCK_ENTITY = REGISTRATE
			.<MechanicalStorageLogisticsBlockEntity>blockEntity("logistics_interface", MechanicalStorageLogisticsBlockEntity::new)
			.validBlocks(MONITOR, DISPATCH)
			.register();

	public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = MENU_TYPES.register("terminal", () ->
			IMenuTypeExtension.create((int containerId, net.minecraft.world.entity.player.Inventory inventory, RegistryFriendlyByteBuf buffer) -> new TerminalMenu(containerId, inventory, buffer)));

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mechanical_storage"))
			.icon(MechanicalStorage::creativeTabIcon)
			.displayItems((parameters, output) -> addCreativeTabItems(output))
			.build());

	public MechanicalStorage(IEventBus modEventBus, ModContainer modContainer) {
		REGISTRATE.registerEventListeners(modEventBus);
		MENU_TYPES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		CONDITION_CODECS.register(modEventBus);
		modEventBus.addListener(MechanicalStorage::addLegacyRegistryAliases);
		modEventBus.addListener(MechanicalStorage::registerCapabilities);
		modContainer.registerConfig(ModConfig.Type.COMMON, MechanicalStorageConfig.SPEC);

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

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LOGISTICS_BLOCK_ENTITY.get(),
				(blockEntity, side) -> blockEntity.getDispatchInventory(side));
	}

	private static void addCreativeTabItems(CreativeModeTab.Output output) {
		addCreativeTabItem(output, CONNECTOR);
		addCreativeTabItem(output, COGWHEEL_CONNECTOR);
		addCreativeTabItem(output, OVERFLOW_CONNECTOR);
		addCreativeTabItem(output, COGWHEEL_OVERFLOW_CONNECTOR);
		addCreativeTabItem(output, TERMINAL);
		addCreativeTabItem(output, CRAFTING_TERMINAL);
		addCreativeTabItem(output, MONITOR);
		addCreativeTabItem(output, DISPATCH);
	}

	private static void addCreativeTabItem(CreativeModeTab.Output output, BlockEntry<?> block) {
		if (MechanicalStorageConfig.isBlockEnabled(block.get())) {
			output.accept(block.get());
		}
	}

	private static ItemStack creativeTabIcon() {
		if (MechanicalStorageConfig.isBlockEnabled(TERMINAL.get())) {
			return TERMINAL.asStack();
		}
		if (MechanicalStorageConfig.isBlockEnabled(MONITOR.get())) {
			return MONITOR.asStack();
		}
		for (BlockEntry<?> block : new BlockEntry<?>[] {
				CONNECTOR, COGWHEEL_CONNECTOR, OVERFLOW_CONNECTOR,
				COGWHEEL_OVERFLOW_CONNECTOR, CRAFTING_TERMINAL, DISPATCH }) {
			if (MechanicalStorageConfig.isBlockEnabled(block.get())) {
				return block.asStack();
			}
		}
		return new ItemStack(Items.BARRIER);
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
