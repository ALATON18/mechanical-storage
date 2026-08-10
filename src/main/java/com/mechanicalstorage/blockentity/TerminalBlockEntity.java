package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.content.logistics.filter.AttributeFilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.ListFilterItem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public class TerminalBlockEntity extends FixedStressKineticBlockEntity implements MenuProvider {
	public static final int MAX_CONNECTORS = 64;
	public static final float FIXED_STRESS_UNITS = 256.0F;
	private static final int MAX_SUMMARY_ITEMS = 8;
	// Menus refresh every 10 ticks. Sharing the same immutable snapshot prevents each
	// open terminal on a kinetic network from repeating the inventory scan.
	private static final int NETWORK_SUMMARY_CACHE_TICKS = 10;
	private static final Map<Level, LevelNetworkSummaryCache> NETWORK_SUMMARY_CACHE = new WeakHashMap<>();
	public static final int LIST_FILTER_SLOT = 0;
	public static final int ATTRIBUTE_FILTER_SLOT = 1;
	public static final int FILTER_SLOTS = 4;
	private final boolean[] filterActive = new boolean[FILTER_SLOTS];
	private final ItemStackHandler terminalFilters = new ItemStackHandler(FILTER_SLOTS) {
		@Override
		public void deserializeNBT(HolderLookup.Provider registries, CompoundTag compound) {
			CompoundTag resized = compound.copy();
			resized.putInt("Size", FILTER_SLOTS);
			super.deserializeNBT(registries, resized);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return slot >= 0 && slot < FILTER_SLOTS
					&& (stack.getItem() instanceof ListFilterItem || stack.getItem() instanceof AttributeFilterItem);
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		protected void onContentsChanged(int slot) {
			if (getStackInSlot(slot).isEmpty()) {
				setFilterActive(slot, false);
			}
			setChanged();
			if (level != null && !level.isClientSide) {
				sendData();
			}
		}
	};

	public TerminalBlockEntity(BlockEntityType<? extends TerminalBlockEntity> type, BlockPos pos, BlockState blockState) {
		super(type, pos, blockState, FIXED_STRESS_UNITS);
	}

	public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
		this(MechanicalStorage.TERMINAL_BLOCK_ENTITY.get(), pos, blockState);
	}

	public ItemStackHandler getTerminalFilters() {
		return terminalFilters;
	}

	public boolean isFilterActive(int slot) {
		return slot >= 0 && slot < FILTER_SLOTS && filterActive[slot];
	}

	public void setFilterActive(int slot, boolean active) {
		if (slot < 0 || slot >= FILTER_SLOTS) {
			return;
		}
		filterActive[slot] = active && !terminalFilters.getStackInSlot(slot).isEmpty();
		setChanged();
		if (level != null && !level.isClientSide) {
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.put("TerminalFilters", terminalFilters.serializeNBT(registries));
		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			compound.putBoolean("Filter" + slot + "Active", filterActive[slot]);
		}
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		if (compound.contains("TerminalFilters")) {
			terminalFilters.deserializeNBT(registries, compound.getCompound("TerminalFilters"));
		}
		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			String key = "Filter" + slot + "Active";
			boolean legacyActive = slot == LIST_FILTER_SLOT
					? compound.getBoolean("ListFilterActive")
					: slot == ATTRIBUTE_FILTER_SLOT && compound.getBoolean("AttributeFilterActive");
			filterActive[slot] = (compound.contains(key) ? compound.getBoolean(key) : legacyActive)
					&& !terminalFilters.getStackInSlot(slot).isEmpty();
		}
	}

	public boolean isOnline() {
		return isSpeedRequirementFulfilled() && hasNetwork();
	}

	public NetworkStatus getNetworkStatus() {
		if (isOnline()) {
			return NetworkStatus.ONLINE;
		}

		if (isOverStressed()) {
			return NetworkStatus.OVERSTRESSED;
		}
		if (getTheoreticalSpeed() != 0) {
			return NetworkStatus.TOO_SLOW;
		}
		return NetworkStatus.DISCONNECTED;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(isCraftingTerminal()
				? "container.mechanical_storage.crafting_terminal"
				: "container.mechanical_storage.terminal");
	}

	public boolean isCraftingTerminal() {
		return getBlockState().is(MechanicalStorage.CRAFTING_TERMINAL.get());
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

		if (!isOnline()) {
			return getNetworkStatus().message();
		}

		NetworkSummary networkSummary = collectNetworkSummary();
		String message = "Terminal: online at " + Math.abs(getSpeed()) + " RPM, found " + networkSummary.connectorsFound + " connector(s), " + networkSummary.inventoriesFound + " inventory/inventories, " + networkSummary.occupiedSlots + "/" + networkSummary.totalSlots + " slots used, " + networkSummary.totalItems + " items total.";
		String summary = formatItemSummary(networkSummary.orderedItems("COUNT", true));
		if (!summary.isEmpty()) {
			message += " Items: " + summary;
		}

		return Component.literal(message);
	}

	public DisplayPage getNetworkDisplayPage(int limit, int offset, String searchText, String sortMode, boolean descending) {
		NetworkSummary networkSummary = collectNetworkSummary();
		List<ItemSummary> entries = networkSummary.orderedItems(sortMode, descending);
		String normalizedSearch = normalizeSearch(searchText);
		int safeOffset = Math.max(0, offset);
		int safeLimit = Math.max(0, limit);
		if (normalizedSearch.isEmpty() && !hasActiveTerminalFilters()) {
			return createDisplayPage(entries, safeOffset, safeLimit);
		}

		List<ItemStack> stacks = new ArrayList<>(Math.min(safeLimit, entries.size()));
		int matchingItems = 0;
		for (ItemSummary summary : entries) {
			if (!matchesTerminalFilters(summary.representative) || !matchesSearch(summary, normalizedSearch)) {
				continue;
			}

			if (matchingItems >= safeOffset && stacks.size() < safeLimit) {
				ItemStack displayStack = summary.representative.copy();
				displayStack.setCount(summary.count);
				stacks.add(displayStack);
			}
			matchingItems++;
		}

		return new DisplayPage(stacks, matchingItems);
	}

	private static DisplayPage createDisplayPage(List<ItemSummary> entries, int offset, int limit) {
		int safeOffset = Math.min(offset, entries.size());
		int end = safeOffset + Math.min(limit, entries.size() - safeOffset);
		List<ItemStack> stacks = new ArrayList<>(end - safeOffset);
		for (int index = safeOffset; index < end; index++) {
			ItemSummary summary = entries.get(index);
			ItemStack displayStack = summary.representative.copy();
			displayStack.setCount(summary.count);
			stacks.add(displayStack);
		}
		return new DisplayPage(stacks, entries.size());
	}

	private boolean hasActiveTerminalFilters() {
		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			if (filterActive[slot] && !terminalFilters.getStackInSlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean matchesTerminalFilters(ItemStack stack) {
		if (level == null) {
			return false;
		}

		boolean hasActiveFilter = false;
		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			ItemStack filter = terminalFilters.getStackInSlot(slot);
			if (!filterActive[slot] || filter.isEmpty()) {
				continue;
			}
			hasActiveFilter = true;
			boolean matches = filter.getItem() instanceof ListFilterItem
					? matchesModListFilter(filter, stack)
					: FilterItemStack.of(filter.copy()).test(level, stack);
			if (matches) {
				return true;
			}
		}
		return !hasActiveFilter;
	}

	private boolean matchesModListFilter(ItemStack filterStack, ItemStack candidate) {
		FilterItemStack filter = FilterItemStack.of(filterStack.copy());
		if (!(filter instanceof FilterItemStack.ListFilterItemStack listFilter)) {
			return filter.test(level, candidate);
		}

		String candidateNamespace = BuiltInRegistries.ITEM.getKey(candidate.getItem()).getNamespace();
		boolean matchedNamespace = false;
		for (FilterItemStack containedFilter : listFilter.containedItems) {
			ItemStack listedItem = containedFilter.item();
			if (!listedItem.isEmpty() && BuiltInRegistries.ITEM.getKey(listedItem.getItem()).getNamespace().equals(candidateNamespace)) {
				matchedNamespace = true;
				break;
			}
		}

		return listFilter.isBlacklist != matchedNamespace;
	}

	public List<ItemStack> getNetworkDisplayStacks(int limit, String searchText, String sortMode, boolean descending) {
		return getNetworkDisplayPage(limit, 0, searchText, sortMode, descending).stacks();
	}

	public ItemStack extractMatchingStackToPlayer(ItemStack filterStack, int amount, Player player) {
		ItemStack extracted = extractMatchingStack(filterStack, amount);
		if (!extracted.isEmpty()) {
			ItemHandlerHelper.giveItemToPlayer(player, extracted.copy());
		}

		return extracted;
	}

	public ItemStack extractMatchingStack(ItemStack filterStack, int amount) {
		if (level == null || filterStack.isEmpty() || amount <= 0 || !isOnline()) {
			return ItemStack.EMPTY;
		}

		ItemStack collected = ItemStack.EMPTY;
		int remainingAmount = amount;
		for (MechanicalStorageConnectorBlockEntity connector : findNetworkConnectors()) {
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
					return finishExtraction(collected);
				}
			}
		}

		return finishExtraction(collected);
	}

	public Component insertHeldStack(Player player, InteractionHand hand) {
		if (level == null) {
			return Component.literal("Terminal: level is not available.");
		}

		if (!isOnline()) {
			return getNetworkStatus().message();
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
		if (level == null || stack.isEmpty() || !isOnline()) {
			return stack;
		}

		int startingCount = stack.getCount();
		ItemStack remaining = stack.copy();
		List<IItemHandler> handlers = new ArrayList<>();
		for (MechanicalStorageConnectorBlockEntity connector : findNetworkConnectors()) {
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler != null) {
				handlers.add(handler);
			}
		}

		remaining = insertIntoMatchingStacks(handlers, remaining);
		if (remaining.isEmpty()) {
			invalidateNetworkSummary();
			return ItemStack.EMPTY;
		}

		remaining = insertIntoEmptySlots(handlers, remaining);
		if (remaining.isEmpty() || remaining.getCount() < startingCount) {
			invalidateNetworkSummary();
		}
		return remaining;
	}

	private static ItemStack insertIntoMatchingStacks(List<IItemHandler> handlers, ItemStack stack) {
		ItemStack remaining = stack;
		for (IItemHandler handler : handlers) {
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack existing = handler.getStackInSlot(slot);
				if (!existing.isEmpty() && sameDisplayGroup(existing, remaining)) {
					remaining = handler.insertItem(slot, remaining, false);
					if (remaining.isEmpty()) {
						return ItemStack.EMPTY;
					}
				}
			}
		}

		return remaining;
	}

	private static ItemStack insertIntoEmptySlots(List<IItemHandler> handlers, ItemStack stack) {
		ItemStack remaining = stack;
		for (IItemHandler handler : handlers) {
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				if (handler.getStackInSlot(slot).isEmpty()) {
					remaining = handler.insertItem(slot, remaining, false);
					if (remaining.isEmpty()) {
						return ItemStack.EMPTY;
					}
				}
			}
		}

		return remaining;
	}

	private NetworkSummary collectNetworkSummary() {
		if (level == null || level.isClientSide || network == null || !isOnline()) {
			return NetworkSummary.EMPTY;
		}

		long cacheEpoch = Math.floorDiv(level.getGameTime(), NETWORK_SUMMARY_CACHE_TICKS);
		NetworkSummary cached = getCachedNetworkSummary(level, network, cacheEpoch);
		if (cached != null) {
			return cached;
		}

		int connectorsFound = 0;
		int inventoriesFound = 0;
		int totalSlots = 0;
		int occupiedSlots = 0;
		int totalItems = 0;
		List<ItemSummary> itemSummaries = new ArrayList<>();
		// Minecraft's strategy hashes the item and complete data-component patch while
		// ignoring count, matching ItemStack.isSameItemSameComponents exactly.
		Map<ItemStack, ItemSummary> summariesByStack =
				new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
		for (MechanicalStorageConnectorBlockEntity connector : findNetworkConnectors()) {
			connectorsFound++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			inventoriesFound++;
			totalSlots += handler.getSlots();
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (!stack.isEmpty()) {
					occupiedSlots++;
					totalItems = saturatedAdd(totalItems, stack.getCount());
					addToSummary(summariesByStack, itemSummaries, stack);
				}
			}
		}

		NetworkSummary networkSummary = new NetworkSummary(connectorsFound, inventoriesFound, totalSlots,
				occupiedSlots, totalItems, itemSummaries);
		cacheNetworkSummary(level, network, cacheEpoch, networkSummary);
		return networkSummary;
	}

	private List<MechanicalStorageConnectorBlockEntity> findNetworkConnectors() {
		return StorageNetworkRegistry.findConnectors(this, MAX_CONNECTORS);
	}

	private static void addToSummary(Map<ItemStack, ItemSummary> summariesByStack,
			List<ItemSummary> itemSummaries, ItemStack stack) {
		ItemSummary summary = summariesByStack.get(stack);
		if (summary != null) {
			summary.count = saturatedAdd(summary.count, stack.getCount());
			return;
		}

		summary = new ItemSummary(stack);
		summariesByStack.put(summary.representative, summary);
		itemSummaries.add(summary);
	}

	@Nullable
	private static NetworkSummary getCachedNetworkSummary(Level level, Long networkId, long cacheEpoch) {
		synchronized (NETWORK_SUMMARY_CACHE) {
			LevelNetworkSummaryCache levelCache = NETWORK_SUMMARY_CACHE.computeIfAbsent(level,
					ignored -> new LevelNetworkSummaryCache());
			levelCache.advanceTo(cacheEpoch);
			return levelCache.summaries.get(networkId);
		}
	}

	private static void cacheNetworkSummary(Level level, Long networkId, long cacheEpoch,
			NetworkSummary networkSummary) {
		synchronized (NETWORK_SUMMARY_CACHE) {
			LevelNetworkSummaryCache levelCache = NETWORK_SUMMARY_CACHE.computeIfAbsent(level,
					ignored -> new LevelNetworkSummaryCache());
			levelCache.advanceTo(cacheEpoch);
			levelCache.summaries.put(networkId, networkSummary);
		}
	}

	private void invalidateNetworkSummary() {
		if (level == null || network == null) {
			return;
		}

		synchronized (NETWORK_SUMMARY_CACHE) {
			LevelNetworkSummaryCache levelCache = NETWORK_SUMMARY_CACHE.get(level);
			if (levelCache != null) {
				levelCache.summaries.remove(network);
			}
		}
	}

	private ItemStack finishExtraction(ItemStack extracted) {
		if (!extracted.isEmpty()) {
			invalidateNetworkSummary();
		}
		return extracted;
	}

	private static int saturatedAdd(int first, int second) {
		long result = (long) first + second;
		return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
	}

	private static boolean sameDisplayGroup(ItemStack first, ItemStack second) {
		return ItemStack.isSameItemSameComponents(first, second);
	}

	private static String normalizeSearch(String searchText) {
		return searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean matchesSearch(ItemSummary itemSummary, String searchText) {
		if (searchText.isEmpty()) {
			return true;
		}

		if (searchText.startsWith("@")) {
			return itemSummary.itemId.getNamespace().contains(searchText.substring(1));
		}

		if (searchText.startsWith("#")) {
			return matchesTagSearch(itemSummary.representative, searchText.substring(1));
		}

		return itemSummary.normalizedDisplayName.contains(searchText)
				|| itemSummary.itemId.getPath().contains(searchText);
	}

	private static boolean matchesTagSearch(ItemStack stack, String searchText) {
		if (searchText.isBlank()) {
			return false;
		}

		String normalizedSearch = searchText.toLowerCase(Locale.ROOT);
		for (TagKey<Item> tagKey : stack.getTags().toList()) {
			if (tagKey.location().toString().toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
				return true;
			}
		}

		return false;
	}

	private static String formatItemSummary(List<ItemSummary> orderedItems) {
		if (orderedItems.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		int shown = 0;
		for (ItemSummary summary : orderedItems) {
			if (shown >= MAX_SUMMARY_ITEMS) {
				break;
			}

			if (!builder.isEmpty()) {
				builder.append(", ");
			}
			builder.append(summary.displayName).append(" x").append(summary.count);
			shown++;
		}

		int hidden = orderedItems.size() - shown;
		if (hidden > 0) {
			builder.append(", +").append(hidden).append(" more");
		}

		return builder.toString();
	}

	public record DisplayPage(List<ItemStack> stacks, int totalItems) {
	}

	public enum NetworkStatus {
		ONLINE,
		OVERSTRESSED,
		DISCONNECTED,
		TOO_SLOW;

		public Component message() {
			return switch (this) {
				case ONLINE -> Component.translatable("status.mechanical_storage.online");
				case OVERSTRESSED -> Component.translatable("status.mechanical_storage.overstressed");
				case DISCONNECTED -> Component.translatable("status.mechanical_storage.disconnected");
				case TOO_SLOW -> Component.translatable("status.mechanical_storage.too_slow");
			};
		}
	}

	private static class LevelNetworkSummaryCache {
		private long epoch = Long.MIN_VALUE;
		private final Map<Long, NetworkSummary> summaries = new HashMap<>();

		private void advanceTo(long nextEpoch) {
			if (epoch == nextEpoch) {
				return;
			}
			epoch = nextEpoch;
			summaries.clear();
		}
	}

	private static class NetworkSummary {
		private static final NetworkSummary EMPTY = new NetworkSummary(0, 0, 0, 0, 0, List.of());

		private final int connectorsFound;
		private final int inventoriesFound;
		private final int totalSlots;
		private final int occupiedSlots;
		private final int totalItems;
		private final List<ItemSummary> itemSummary;
		private List<ItemSummary> nameAscending;
		private List<ItemSummary> nameDescending;
		private List<ItemSummary> countAscending;
		private List<ItemSummary> countDescending;

		private NetworkSummary(int connectorsFound, int inventoriesFound, int totalSlots, int occupiedSlots,
				int totalItems, List<ItemSummary> itemSummary) {
			this.connectorsFound = connectorsFound;
			this.inventoriesFound = inventoriesFound;
			this.totalSlots = totalSlots;
			this.occupiedSlots = occupiedSlots;
			this.totalItems = totalItems;
			this.itemSummary = List.copyOf(itemSummary);
		}

		private synchronized List<ItemSummary> orderedItems(String sortMode, boolean descending) {
			if ("NAME".equals(sortMode)) {
				if (descending) {
					if (nameDescending == null) {
						nameDescending = sortedItems(true, true);
					}
					return nameDescending;
				}
				if (nameAscending == null) {
					nameAscending = sortedItems(true, false);
				}
				return nameAscending;
			}

			if (descending) {
				if (countDescending == null) {
					countDescending = sortedItems(false, true);
				}
				return countDescending;
			}
			if (countAscending == null) {
				countAscending = sortedItems(false, false);
			}
			return countAscending;
		}

		private List<ItemSummary> sortedItems(boolean byName, boolean descending) {
			List<ItemSummary> sorted = new ArrayList<>(itemSummary);
			Comparator<ItemSummary> comparator = byName
					? Comparator.comparing(summary -> summary.normalizedDisplayName)
					: Comparator.comparingInt(summary -> summary.count);
			if (descending) {
				comparator = comparator.reversed();
			}
			sorted.sort(comparator.thenComparing(summary -> summary.itemId));
			return List.copyOf(sorted);
		}
	}

	private static class ItemSummary {
		private final ItemStack representative;
		private final ResourceLocation itemId;
		private final String displayName;
		private final String normalizedDisplayName;
		private int count;

		private ItemSummary(ItemStack stack) {
			this.representative = stack.copy();
			this.representative.setCount(1);
			this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			this.displayName = stack.getHoverName().getString();
			this.normalizedDisplayName = displayName.toLowerCase(Locale.ROOT);
			this.count = stack.getCount();
		}
	}
}
