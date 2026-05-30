package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TerminalBlockEntity extends BlockEntity {
	private static final int SCAN_RADIUS = 32;
	private static final int MAX_CONNECTORS = 64;
	private static final int MAX_SUMMARY_ITEMS = 8;

	public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
		super(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), pos, blockState);
	}

	public Component describeNearbyConnectorNetwork() {
		if (level == null) {
			return Component.literal("Terminal: level is not available.");
		}

		int connectorsFound = 0;
		int inventoriesFound = 0;
		int totalSlots = 0;
		int occupiedSlots = 0;
		int totalItems = 0;
		Map<ResourceLocation, ItemSummary> itemSummary = new LinkedHashMap<>();

		BlockPos min = worldPosition.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
		BlockPos max = worldPosition.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);

		for (BlockPos scanPos : BlockPos.betweenClosed(min, max)) {
			if (connectorsFound >= MAX_CONNECTORS) {
				break;
			}

			BlockEntity blockEntity = level.getBlockEntity(scanPos);
			if (!(blockEntity instanceof MechanicalStorageConnectorBlockEntity connector)) {
				continue;
			}

			connectorsFound++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			inventoriesFound++;
			int slots = handler.getSlots();
			totalSlots += slots;

			for (int slot = 0; slot < slots; slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (!stack.isEmpty()) {
					occupiedSlots++;
					totalItems += stack.getCount();
					addToSummary(itemSummary, stack);
				}
			}
		}

		String message = "Terminal: found " + connectorsFound + " connector(s), " + inventoriesFound + " inventory/inventories, " + occupiedSlots + "/" + totalSlots + " slots used, " + totalItems + " items total.";
		String summary = formatItemSummary(itemSummary);

		if (!summary.isEmpty()) {
			message += " Items: " + summary;
		}

		return Component.literal(message);
	}

	private static void addToSummary(Map<ResourceLocation, ItemSummary> itemSummary, ItemStack stack) {
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		ItemSummary summary = itemSummary.computeIfAbsent(itemId, ignored -> new ItemSummary(stack.getHoverName().getString()));
		summary.count += stack.getCount();
	}

	private static String formatItemSummary(Map<ResourceLocation, ItemSummary> itemSummary) {
		if (itemSummary.isEmpty()) {
			return "";
		}

		List<Map.Entry<ResourceLocation, ItemSummary>> entries = new ArrayList<>(itemSummary.entrySet());
		entries.sort(Comparator.<Map.Entry<ResourceLocation, ItemSummary>>comparingInt(entry -> entry.getValue().count).reversed());

		StringBuilder builder = new StringBuilder();
		int shown = 0;
		for (Map.Entry<ResourceLocation, ItemSummary> entry : entries) {
			if (shown >= MAX_SUMMARY_ITEMS) {
				break;
			}

			if (builder.length() > 0) {
				builder.append(", ");
			}

			builder.append(entry.getValue().displayName).append(" x").append(entry.getValue().count);
			shown++;
		}

		int hidden = entries.size() - shown;
		if (hidden > 0) {
			builder.append(", +").append(hidden).append(" more");
		}

		return builder.toString();
	}

	private static class ItemSummary {
		private final String displayName;
		private int count;

		private ItemSummary(String displayName) {
			this.displayName = displayName;
		}
	}
}
