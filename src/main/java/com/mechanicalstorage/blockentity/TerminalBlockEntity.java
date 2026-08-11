package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mechanicalstorage.network.StorageConnectorEndpoint;
import com.mechanicalstorage.network.StorageNetworkKey;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.content.logistics.filter.AttributeFilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.ListFilterItem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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
	public static final int BUCKET_VOLUME = 1_000;
	private static final int MAX_SUMMARY_ITEMS = 8;
	// Menus refresh every 10 ticks. Sharing the same immutable snapshot prevents each
	// open terminal on a kinetic network from repeating the inventory scan.
	private static final int NETWORK_SUMMARY_CACHE_TICKS = 10;
	private static final int MOVING_ENDPOINT_GRACE_TICKS = 2;
	private static final Map<Level, LevelNetworkSummaryCache> NETWORK_SUMMARY_CACHE = new WeakHashMap<>();
	public static final int LIST_FILTER_SLOT = 0;
	public static final int ATTRIBUTE_FILTER_SLOT = 1;
	public static final int FILTER_SLOTS = 4;
	private final boolean[] filterActive = new boolean[FILTER_SLOTS];
	@Nullable
	private StorageNetworkKey movingNetworkKey;
	@Nullable
	private BlockPos movingLocalPos;
	@Nullable
	private Runnable movingDataSaver;
	private int movingEntityId = -1;
	private long movingLastSeenTick = Long.MIN_VALUE;
	private BlockPos movingWorldPos = BlockPos.ZERO;
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
			terminalDataChanged();
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
		terminalDataChanged();
	}

	private void terminalDataChanged() {
		if (movingDataSaver != null) {
			movingDataSaver.run();
			return;
		}
		setChanged();
		if (level != null && !level.isClientSide) {
			sendData();
		}
	}

	public void configureMovingNetwork(StorageNetworkKey networkKey, int entityId, BlockPos localPos,
			Runnable dataSaver) {
		this.movingNetworkKey = networkKey;
		this.movingEntityId = entityId;
		this.movingLocalPos = localPos.immutable();
		this.movingDataSaver = dataSaver;
	}

	public void updateMovingState(int entityId, BlockPos worldPos, long gameTime) {
		this.movingEntityId = entityId;
		this.movingWorldPos = worldPos.immutable();
		this.movingLastSeenTick = gameTime;
	}

	public void clearMovingNetwork() {
		movingNetworkKey = null;
		movingLocalPos = null;
		movingDataSaver = null;
		movingEntityId = -1;
		movingLastSeenTick = Long.MIN_VALUE;
	}

	public boolean isMovingTerminal() {
		return movingNetworkKey != null;
	}

	public int getMovingEntityId() {
		return movingEntityId;
	}

	@Nullable
	public BlockPos getMovingLocalPos() {
		return movingLocalPos;
	}

	public BlockPos getMenuPosition() {
		return isMovingTerminal() ? movingWorldPos : getBlockPos();
	}

	public boolean isMovingEndpointAvailable(long gameTime) {
		if (movingNetworkKey == null || level == null || movingEntityId < 0
				|| gameTime - movingLastSeenTick > MOVING_ENDPOINT_GRACE_TICKS) {
			return false;
		}
		var entity = level.getEntity(movingEntityId);
		return entity != null && entity.isAlive();
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
		if (movingNetworkKey != null) {
			return level != null && isMovingEndpointAvailable(level.getGameTime());
		}
		return isSpeedRequirementFulfilled() && hasNetwork();
	}

	@Nullable
	public Long getKineticNetworkId() {
		return network;
	}

	@Nullable
	public StorageNetworkKey getStorageNetworkKey() {
		if (movingNetworkKey != null) {
			return movingNetworkKey;
		}
		return network == null ? null : StorageNetworkKey.kinetic(network);
	}

	public NetworkStatus getNetworkStatus() {
		if (isOnline()) {
			return NetworkStatus.ONLINE;
		}
		if (movingNetworkKey != null) {
			return NetworkStatus.DISCONNECTED;
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
		String message = "Terminal: online at " + Math.abs(getSpeed()) + " RPM, found "
				+ networkSummary.connectorsFound + " connector(s), " + networkSummary.inventoriesFound
				+ " item inventory/inventories, " + networkSummary.occupiedSlots + "/" + networkSummary.totalSlots
				+ " slots used, " + networkSummary.totalItems + " items, " + networkSummary.fluidHandlersFound
				+ " fluid handler(s), " + networkSummary.totalFluid + " mB.";
		String summary = formatSummary(networkSummary.orderedEntries("COUNT", true, DisplayMode.ITEMS));
		if (!summary.isEmpty()) {
			message += " Items: " + summary;
		}
		String fluidSummary = formatSummary(networkSummary.orderedEntries("COUNT", true, DisplayMode.FLUIDS));
		if (!fluidSummary.isEmpty()) {
			message += " Fluids: " + fluidSummary;
		}

		return Component.literal(message);
	}

	public DisplayPage getNetworkDisplayPage(int limit, int offset, String searchText, String sortMode,
			boolean descending, String displayMode) {
		NetworkSummary networkSummary = collectNetworkSummary();
		String normalizedSearch = normalizeSearch(searchText);
		DisplayMode requestedMode = DisplayMode.fromName(displayMode);
		DisplayMode effectiveMode = normalizedSearch.isEmpty() ? requestedMode : DisplayMode.BOTH;
		List<NetworkEntry> entries = networkSummary.orderedEntries(sortMode, descending, effectiveMode);
		int safeOffset = Math.max(0, offset);
		int safeLimit = Math.max(0, limit);
		if (normalizedSearch.isEmpty() && !hasActiveTerminalFilters()) {
			return createDisplayPage(entries, safeOffset, safeLimit);
		}

		List<DisplayEntry> displayEntries = new ArrayList<>(Math.min(safeLimit, entries.size()));
		int matchingItems = 0;
		for (NetworkEntry entry : entries) {
			if (entry instanceof ItemSummary itemSummary && !matchesTerminalFilters(itemSummary.representative)) {
				continue;
			}
			if (!matchesSearch(entry, normalizedSearch)) {
				continue;
			}

			if (matchingItems >= safeOffset && displayEntries.size() < safeLimit) {
				displayEntries.add(entry.toDisplayEntry());
			}
			matchingItems++;
		}

		return new DisplayPage(displayEntries, matchingItems);
	}

	private static DisplayPage createDisplayPage(List<NetworkEntry> entries, int offset, int limit) {
		int safeOffset = Math.min(offset, entries.size());
		int end = safeOffset + Math.min(limit, entries.size() - safeOffset);
		List<DisplayEntry> displayEntries = new ArrayList<>(end - safeOffset);
		for (int index = safeOffset; index < end; index++) {
			displayEntries.add(entries.get(index).toDisplayEntry());
		}
		return new DisplayPage(displayEntries, entries.size());
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
		return getNetworkDisplayPage(limit, 0, searchText, sortMode, descending, DisplayMode.ITEMS.name())
				.entries().stream().map(DisplayEntry::icon).toList();
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
		for (StorageConnectorEndpoint connector : findNetworkConnectors()) {
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

	public ItemStack extractFluidBucket(FluidStack filterFluid) {
		if (level == null || filterFluid.isEmpty() || !isOnline()) {
			return ItemStack.EMPTY;
		}

		ItemStack filledBucket = FluidUtil.getFilledBucket(filterFluid.copyWithAmount(BUCKET_VOLUME));
		if (filledBucket.isEmpty()) {
			return ItemStack.EMPTY;
		}

		List<IFluidHandler> handlers = new ArrayList<>();
		int available = 0;
		for (StorageConnectorEndpoint connector : findNetworkConnectors()) {
			IFluidHandler handler = connector.getTargetFluidHandler();
			if (handler == null) {
				continue;
			}
			handlers.add(handler);
			FluidStack simulated = handler.drain(filterFluid.copyWithAmount(BUCKET_VOLUME - available),
					IFluidHandler.FluidAction.SIMULATE);
			if (!simulated.isEmpty() && FluidStack.isSameFluidSameComponents(filterFluid, simulated)) {
				available += simulated.getAmount();
				if (available >= BUCKET_VOLUME) {
					break;
				}
			}
		}
		if (available < BUCKET_VOLUME) {
			return ItemStack.EMPTY;
		}

		int remaining = BUCKET_VOLUME;
		for (IFluidHandler handler : handlers) {
			FluidStack drained = handler.drain(filterFluid.copyWithAmount(remaining),
					IFluidHandler.FluidAction.EXECUTE);
			if (!drained.isEmpty() && FluidStack.isSameFluidSameComponents(filterFluid, drained)) {
				remaining -= drained.getAmount();
				if (remaining <= 0) {
					invalidateNetworkSummary();
					return filledBucket;
				}
			}
		}

		return ItemStack.EMPTY;
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
		return insertStackIntoMatchingInventories(stack);
	}

	/**
	 * Inserts only into inventories which already contain the exact item and
	 * component variant. Once an inventory qualifies, its handler remains in
	 * charge of deciding which matching or empty slots accept the item and how
	 * many each slot can hold.
	 */
	public ItemStack insertStackIntoMatchingInventories(ItemStack stack) {
		if (level == null || stack.isEmpty() || !isOnline()) {
			return stack;
		}

		int startingCount = stack.getCount();
		List<IItemHandler> matchingHandlers = new ArrayList<>();
		for (StorageConnectorEndpoint connector : findNetworkConnectors()) {
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler != null && containsMatchingStack(handler, stack)) {
				matchingHandlers.add(handler);
			}
		}

		ItemStack remaining = insertIntoMatchingStacks(matchingHandlers, stack.copy());
		if (!remaining.isEmpty()) {
			remaining = insertIntoEmptySlots(matchingHandlers, remaining);
		}
		if (remaining.isEmpty() || remaining.getCount() < startingCount) {
			invalidateNetworkSummary();
		}
		return remaining;
	}

	private static boolean containsMatchingStack(IItemHandler handler, ItemStack stack) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			ItemStack existing = handler.getStackInSlot(slot);
			if (!existing.isEmpty() && sameDisplayGroup(existing, stack)) {
				return true;
			}
		}
		return false;
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
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || level.isClientSide || networkKey == null || !isOnline()) {
			return NetworkSummary.EMPTY;
		}

		long cacheEpoch = Math.floorDiv(level.getGameTime(), NETWORK_SUMMARY_CACHE_TICKS);
		NetworkSummary cached = getCachedNetworkSummary(level, networkKey, cacheEpoch);
		if (cached != null) {
			return cached;
		}

		int connectorsFound = 0;
		int inventoriesFound = 0;
		int totalSlots = 0;
		int occupiedSlots = 0;
		int totalItems = 0;
		int fluidHandlersFound = 0;
		int totalTanks = 0;
		int occupiedTanks = 0;
		int totalFluid = 0;
		List<ItemSummary> itemSummaries = new ArrayList<>();
		List<FluidSummary> fluidSummaries = new ArrayList<>();
		// Minecraft's strategy hashes the item and complete data-component patch while
		// ignoring count, matching ItemStack.isSameItemSameComponents exactly.
		Map<ItemStack, ItemSummary> summariesByStack =
				new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
		Map<FluidKey, FluidSummary> summariesByFluid = new HashMap<>();
		for (StorageConnectorEndpoint connector : findNetworkConnectors()) {
			connectorsFound++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler != null) {
				inventoriesFound++;
				totalSlots += handler.getSlots();
				for (int slot = 0; slot < handler.getSlots(); slot++) {
					ItemStack stack = handler.getStackInSlot(slot);
					if (!stack.isEmpty()) {
						occupiedSlots++;
						totalItems = saturatedAdd(totalItems, stack.getCount());
						addToItemSummary(summariesByStack, itemSummaries, stack);
					}
				}
			}

			IFluidHandler fluidHandler = connector.getTargetFluidHandler();
			if (fluidHandler != null) {
				fluidHandlersFound++;
				totalTanks += fluidHandler.getTanks();
				for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
					FluidStack fluid = fluidHandler.getFluidInTank(tank);
					if (!fluid.isEmpty()) {
						occupiedTanks++;
						totalFluid = saturatedAdd(totalFluid, fluid.getAmount());
						addToFluidSummary(summariesByFluid, fluidSummaries, fluid);
					}
				}
			}
		}

		NetworkSummary networkSummary = new NetworkSummary(connectorsFound, inventoriesFound, totalSlots,
				occupiedSlots, totalItems, fluidHandlersFound, totalTanks, occupiedTanks, totalFluid,
				itemSummaries, fluidSummaries);
		cacheNetworkSummary(level, networkKey, cacheEpoch, networkSummary);
		return networkSummary;
	}

	private List<StorageConnectorEndpoint> findNetworkConnectors() {
		return StorageNetworkRegistry.findConnectors(this, MAX_CONNECTORS);
	}

	private static void addToItemSummary(Map<ItemStack, ItemSummary> summariesByStack,
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

	private static void addToFluidSummary(Map<FluidKey, FluidSummary> summariesByFluid,
			List<FluidSummary> fluidSummaries, FluidStack stack) {
		FluidKey key = new FluidKey(stack.getFluid(), stack.getComponentsPatch());
		FluidSummary summary = summariesByFluid.get(key);
		if (summary != null) {
			summary.count = saturatedAdd(summary.count, stack.getAmount());
			return;
		}

		summary = new FluidSummary(stack);
		summariesByFluid.put(key, summary);
		fluidSummaries.add(summary);
	}

	@Nullable
	private static NetworkSummary getCachedNetworkSummary(Level level, StorageNetworkKey networkKey,
			long cacheEpoch) {
		synchronized (NETWORK_SUMMARY_CACHE) {
			LevelNetworkSummaryCache levelCache = NETWORK_SUMMARY_CACHE.computeIfAbsent(level,
					ignored -> new LevelNetworkSummaryCache());
			levelCache.advanceTo(cacheEpoch);
			return levelCache.summaries.get(networkKey);
		}
	}

	private static void cacheNetworkSummary(Level level, StorageNetworkKey networkKey, long cacheEpoch,
			NetworkSummary networkSummary) {
		synchronized (NETWORK_SUMMARY_CACHE) {
			LevelNetworkSummaryCache levelCache = NETWORK_SUMMARY_CACHE.computeIfAbsent(level,
					ignored -> new LevelNetworkSummaryCache());
			levelCache.advanceTo(cacheEpoch);
			levelCache.summaries.put(networkKey, networkSummary);
		}
	}

	private void invalidateNetworkSummary() {
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || networkKey == null) {
			return;
		}

		synchronized (NETWORK_SUMMARY_CACHE) {
			LevelNetworkSummaryCache levelCache = NETWORK_SUMMARY_CACHE.get(level);
			if (levelCache != null) {
				levelCache.summaries.remove(networkKey);
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

	private static boolean matchesSearch(NetworkEntry entry, String searchText) {
		if (searchText.isEmpty()) {
			return true;
		}

		if (searchText.startsWith("@")) {
			return entry.id().getNamespace().contains(searchText.substring(1));
		}

		if (searchText.startsWith("#")) {
			return entry.matchesTag(searchText.substring(1));
		}

		return entry.normalizedDisplayName().contains(searchText)
				|| entry.id().getPath().contains(searchText);
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

	private static boolean matchesTagSearch(FluidStack stack, String searchText) {
		if (searchText.isBlank()) {
			return false;
		}

		String normalizedSearch = searchText.toLowerCase(Locale.ROOT);
		for (TagKey<Fluid> tagKey : stack.getTags().toList()) {
			if (tagKey.location().toString().toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
				return true;
			}
		}

		return false;
	}

	private static String formatSummary(List<NetworkEntry> orderedEntries) {
		if (orderedEntries.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		int shown = 0;
		for (NetworkEntry summary : orderedEntries) {
			if (shown >= MAX_SUMMARY_ITEMS) {
				break;
			}

			if (!builder.isEmpty()) {
				builder.append(", ");
			}
			builder.append(summary.displayName()).append(" x").append(summary.count());
			shown++;
		}

		int hidden = orderedEntries.size() - shown;
		if (hidden > 0) {
			builder.append(", +").append(hidden).append(" more");
		}

		return builder.toString();
	}

	public record DisplayPage(List<DisplayEntry> entries, int totalItems) {
		public DisplayPage {
			entries = List.copyOf(entries);
		}
	}

	public record DisplayEntry(ItemStack icon, FluidStack fluid, int amount) {
		public DisplayEntry {
			icon = icon.copy();
			fluid = fluid.copy();
			amount = Math.max(0, amount);
		}

		public boolean isFluid() {
			return !fluid.isEmpty();
		}
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
		private final Map<StorageNetworkKey, NetworkSummary> summaries = new HashMap<>();

		private void advanceTo(long nextEpoch) {
			if (epoch == nextEpoch) {
				return;
			}
			epoch = nextEpoch;
			summaries.clear();
		}
	}

	private static class NetworkSummary {
		private static final NetworkSummary EMPTY = new NetworkSummary(0, 0, 0, 0, 0,
				0, 0, 0, 0, List.of(), List.of());

		private final int connectorsFound;
		private final int inventoriesFound;
		private final int totalSlots;
		private final int occupiedSlots;
		private final int totalItems;
		private final int fluidHandlersFound;
		private final int totalTanks;
		private final int occupiedTanks;
		private final int totalFluid;
		private final List<ItemSummary> itemSummaries;
		private final List<FluidSummary> fluidSummaries;
		private final Map<DisplayOrderKey, List<NetworkEntry>> orderedEntryCache = new HashMap<>();

		private NetworkSummary(int connectorsFound, int inventoriesFound, int totalSlots, int occupiedSlots,
				int totalItems, int fluidHandlersFound, int totalTanks, int occupiedTanks, int totalFluid,
				List<ItemSummary> itemSummaries, List<FluidSummary> fluidSummaries) {
			this.connectorsFound = connectorsFound;
			this.inventoriesFound = inventoriesFound;
			this.totalSlots = totalSlots;
			this.occupiedSlots = occupiedSlots;
			this.totalItems = totalItems;
			this.fluidHandlersFound = fluidHandlersFound;
			this.totalTanks = totalTanks;
			this.occupiedTanks = occupiedTanks;
			this.totalFluid = totalFluid;
			this.itemSummaries = List.copyOf(itemSummaries);
			this.fluidSummaries = List.copyOf(fluidSummaries);
		}

		private synchronized List<NetworkEntry> orderedEntries(String sortMode, boolean descending,
				DisplayMode displayMode) {
			DisplayOrderKey key = new DisplayOrderKey("NAME".equals(sortMode), descending, displayMode);
			return orderedEntryCache.computeIfAbsent(key, this::sortedEntries);
		}

		private List<NetworkEntry> sortedEntries(DisplayOrderKey key) {
			List<NetworkEntry> sorted = new ArrayList<>();
			if (key.displayMode().includesItems()) {
				sorted.addAll(itemSummaries);
			}
			if (key.displayMode().includesFluids()) {
				sorted.addAll(fluidSummaries);
			}

			Comparator<NetworkEntry> comparator = key.byName()
					? Comparator.comparing(NetworkEntry::normalizedDisplayName)
					: Comparator.comparingInt(NetworkEntry::count);
			if (key.descending()) {
				comparator = comparator.reversed();
			}
			sorted.sort(comparator.thenComparing(NetworkEntry::id));
			return List.copyOf(sorted);
		}
	}

	private interface NetworkEntry {
		ResourceLocation id();

		String displayName();

		String normalizedDisplayName();

		int count();

		boolean matchesTag(String searchText);

		DisplayEntry toDisplayEntry();
	}

	private static class ItemSummary implements NetworkEntry {
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

		@Override
		public ResourceLocation id() {
			return itemId;
		}

		@Override
		public String displayName() {
			return displayName;
		}

		@Override
		public String normalizedDisplayName() {
			return normalizedDisplayName;
		}

		@Override
		public int count() {
			return count;
		}

		@Override
		public boolean matchesTag(String searchText) {
			return matchesTagSearch(representative, searchText);
		}

		@Override
		public DisplayEntry toDisplayEntry() {
			return new DisplayEntry(representative.copyWithCount(1), FluidStack.EMPTY, count);
		}
	}

	private static class FluidSummary implements NetworkEntry {
		private final FluidStack representative;
		private final ResourceLocation fluidId;
		private final String displayName;
		private final String normalizedDisplayName;
		private int count;

		private FluidSummary(FluidStack stack) {
			this.representative = stack.copyWithAmount(1);
			this.fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
			this.displayName = stack.getHoverName().getString();
			this.normalizedDisplayName = displayName.toLowerCase(Locale.ROOT);
			this.count = stack.getAmount();
		}

		@Override
		public ResourceLocation id() {
			return fluidId;
		}

		@Override
		public String displayName() {
			return displayName;
		}

		@Override
		public String normalizedDisplayName() {
			return normalizedDisplayName;
		}

		@Override
		public int count() {
			return count;
		}

		@Override
		public boolean matchesTag(String searchText) {
			return matchesTagSearch(representative, searchText);
		}

		@Override
		public DisplayEntry toDisplayEntry() {
			// Menus synchronise their visible entries through item slots. The bucket is
			// only a hidden transport marker; TerminalScreen renders the actual fluid
			// atlas sprite and supplies a fluid-specific tooltip.
			ItemStack icon = new ItemStack(Items.BUCKET);
			icon.set(DataComponents.CUSTOM_NAME, representative.getHoverName());
			return new DisplayEntry(icon, representative, count);
		}
	}

	private enum DisplayMode {
		ITEMS,
		FLUIDS,
		BOTH;

		private static DisplayMode fromName(String name) {
			try {
				return valueOf(name);
			} catch (IllegalArgumentException | NullPointerException ignored) {
				return ITEMS;
			}
		}

		private boolean includesItems() {
			return this != FLUIDS;
		}

		private boolean includesFluids() {
			return this != ITEMS;
		}
	}

	private record DisplayOrderKey(boolean byName, boolean descending, DisplayMode displayMode) {
	}

	private record FluidKey(Fluid fluid, DataComponentPatch components) {
	}
}
