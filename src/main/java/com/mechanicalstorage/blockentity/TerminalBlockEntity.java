package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.menu.TerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TerminalBlockEntity extends BlockEntity implements MenuProvider {
	private static final int SCAN_RADIUS = 32;
	private static final int MAX_CONNECTORS = 64;
	private static final int MAX_SUMMARY_ITEMS = 8;

	public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
		super(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), pos, blockState);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.mechanical_storage.terminal");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new TerminalMenu(containerId, inventory, this);
	}

	public Component describeNearbyConnectorNetwork() {
		if (level == null) {
			return Component.literal("Terminal: level is not available.");
		}

		NetworkSummary networkSummary = collectNetworkSummary();

		String message = "Terminal: found " + networkSummary.connectorsFound + " connector(s), " + networkSummary.inventoriesFound + " inventory/inventories, " + networkSummary.occupiedSlots + "/" + networkSummary.totalSlots + " slots used, " + networkSummary.totalItems + " items total.";
		String summary = formatItemSummary(networkSummary.itemSummary);

		if (!summary.isEmpty()) {
			message += " Items: " + summary;
		}

		return Component.literal(message);
	}

	public List<ItemStack> getNetworkDisplayStacks(int limit) {
		return getNetworkDisplayStacks(limit, "");
	}

	public List<ItemStack> getNetworkDisplayStacks(int limit, String searchText) {
		NetworkSummary networkSummary = collectNetworkSummary();
		List<Map.Entry<ResourceLocation, ItemSummary>> entries = new ArrayList<>(networkSummary.itemSummary.entrySet());
		entries.sort(Comparator.<Map.Entry<ResourceLocation, ItemSummary>>comparingInt(entry -> entry.getValue().count).reversed());

		String normalizedSearch = normalizeSearch(searchText);
		List<ItemStack> stacks = new ArrayList<>();
		for (Map.Entry<ResourceLocation, ItemSummary> entry : entries) {
			if (stacks.size() >= limit) {
				break;
			}

			if (!matchesSearch(entry.getKey(), entry.getValue(), normalizedSearch)) {
				continue;
			}

			ItemStack displayStack = entry.getValue().representative.copy();
			displayStack.setCount(entry.getValue().count);
			stacks.add(displayStack);
		}

		return stacks;
	}

	public ItemStack extractMatchingStackToPlayer(ItemStack filterStack, int amount, Player player) {
		ItemStack extracted = extractMatchingStack(filterStack, amount);
		if (!extracted.isEmpty()) {
			ItemHandlerHelper.giveItemToPlayer(player, extracted.copy());
		}

		return extracted;
	}

	public ItemStack extractMatchingStack(ItemStack filterStack, int amount) {
		if (level == null || filterStack.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}

		ItemStack collected = ItemStack.EMPTY;
		int remainingAmount = amount;

		for (MechanicalStorageConnectorBlockEntity connector : findNearbyConnectors()) {
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (stack.isEmpty() || !sameDisplayGroup(stack, filterStack)) {
					continue;
				}

				ItemStack extracted = handler.extractItem(slot, remainingAmount, false);
				if (extracted.isEmpty()) {
					continue;
				}

				if (collected.isEmpty()) {
					collected = extracted.copy();
				} else {
					collected.grow(extracted.getCount());
				}

				remainingAmount -= extracted.getCount();
				if (remainingAmount <= 0) {
					return collected;
				}
			}
		}

		return collected;
	}

	public Component extractFirstAvailableStack(Player player) {
		if (level == null) {
			return Component.literal("Terminal: level is not available.");
		}

		int connectorsChecked = 0;

		for (MechanicalStorageConnectorBlockEntity connector : findNearbyConnectors()) {
			connectorsChecked++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (stack.isEmpty()) {
					continue;
				}

				int amountToExtract = Math.min(stack.getMaxStackSize(), stack.getCount());
				ItemStack extracted = handler.extractItem(slot, amountToExtract, false);
				if (extracted.isEmpty()) {
					continue;
				}

				ItemStack delivered = extracted.copy();
				ItemHandlerHelper.giveItemToPlayer(player, delivered);
				return Component.literal("Terminal: withdrew " + extracted.getHoverName().getString() + " x" + extracted.getCount() + ".");
			}
		}

		return Component.literal("Terminal: no extractable items found across " + connectorsChecked + " connector(s).");
	}

	public Component insertHeldStack(Player player, InteractionHand hand) {
		if (level == null) {
			return Component.literal("Terminal: level is not available.");
		}

		ItemStack heldStack = player.getItemInHand(hand);
		if (heldStack.isEmpty()) {
			return describeNearbyConnectorNetwork();
		}

		String displayName = heldStack.getHoverName().getString();
		int startingCount = heldStack.getCount();
		ItemStack remaining = insertStackIntoNetwork(heldStack.copy());

		if (remaining.isEmpty()) {
			player.setItemInHand(hand, ItemStack.EMPTY);
			return Component.literal("Terminal: inserted " + displayName + " x" + startingCount + ".");
		}

		int inserted = startingCount - remaining.getCount();
		if (inserted > 0) {
			player.setItemInHand(hand, remaining.copy());
			return Component.literal("Terminal: inserted " + displayName + " x" + inserted + ", " + remaining.getCount() + " left in hand.");
		}

		return Component.literal("Terminal: no room for " + displayName + ".");
	}

	public ItemStack insertStackIntoNetwork(ItemStack stack) {
		if (level == null || stack.isEmpty()) {
			return stack;
		}

		ItemStack remaining = stack.copy();

		for (MechanicalStorageConnectorBlockEntity connector : findNearbyConnectors()) {
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			for (int slot = 0; slot < handler.getSlots(); slot++) {
				remaining = handler.insertItem(slot, remaining, false);
				if (remaining.isEmpty()) {
					return ItemStack.EMPTY;
				}
			}
		}

		return remaining;
	}

	private NetworkSummary collectNetworkSummary() {
		NetworkSummary networkSummary = new NetworkSummary();

		for (MechanicalStorageConnectorBlockEntity connector : findNearbyConnectors()) {
			networkSummary.connectorsFound++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			networkSummary.inventoriesFound++;
			int slots = handler.getSlots();
			networkSummary.totalSlots += slots;

			for (int slot = 0; slot < slots; slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (!stack.isEmpty()) {
					networkSummary.occupiedSlots++;
					networkSummary.totalItems += stack.getCount();
					addToSummary(networkSummary.itemSummary, stack);
				}
			}
		}

		return networkSummary;
	}

	private List<MechanicalStorageConnectorBlockEntity> findNearbyConnectors() {
		List<MechanicalStorageConnectorBlockEntity> connectors = new ArrayList<>();

		if (level == null) {
			return connectors;
		}

		BlockPos min = worldPosition.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
		BlockPos max = worldPosition.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);

		for (BlockPos scanPos : BlockPos.betweenClosed(min, max)) {
			if (connectors.size() >= MAX_CONNECTORS) {
				break;
			}

			BlockEntity blockEntity = level.getBlockEntity(scanPos);
			if (blockEntity instanceof MechanicalStorageConnectorBlockEntity connector) {
				connectors.add(connector);
			}
		}

		return connectors;
	}

	private static void addToSummary(Map<ResourceLocation, ItemSummary> itemSummary, ItemStack stack) {
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		ItemSummary summary = itemSummary.computeIfAbsent(itemId, ignored -> new ItemSummary(stack));
		summary.count += stack.getCount();
	}

	private static boolean sameDisplayGroup(ItemStack first, ItemStack second) {
		return ItemStack.isSameItemSameComponents(first, second);
	}

	private static String normalizeSearch(String searchText) {
		return searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean matchesSearch(ResourceLocation itemId, ItemSummary itemSummary, String searchText) {
		if (searchText.isEmpty()) {
			return true;
		}

		String displayName = itemSummary.displayName.toLowerCase(Locale.ROOT);
		String path = itemId.getPath().toLowerCase(Locale.ROOT);

		if (searchText.startsWith("@")) {
			return itemId.getNamespace().toLowerCase(Locale.ROOT).contains(searchText.substring(1));
		}

		if (searchText.startsWith("#")) {
			return matchesTagSearch(itemSummary.representative, searchText.substring(1));
		}

		return displayName.contains(searchText) || path.contains(searchText);
	}

	private static boolean matchesTagSearch(ItemStack stack, String searchText) {
		if (searchText.isBlank()) {
			return false;
		}

		String normalizedSearch = searchText.toLowerCase(Locale.ROOT);
		for (TagKey<Item> tagKey : stack.getTags().toList()) {
			String tag = tagKey.location().toString().toLowerCase(Locale.ROOT);
			if (tag.contains(normalizedSearch)) {
				return true;
			}
		}

		return false;
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

	private static class NetworkSummary {
		private int connectorsFound;
		private int inventoriesFound;
		private int totalSlots;
		private int occupiedSlots;
		private int totalItems;
		private final Map<ResourceLocation, ItemSummary> itemSummary = new LinkedHashMap<>();
	}

	private static class ItemSummary {
		private final ItemStack representative;
		private final String displayName;
		private int count;

		private ItemSummary(ItemStack stack) {
			this.representative = stack.copy();
			this.representative.setCount(1);
			this.displayName = stack.getHoverName().getString();
		}
	}
}
