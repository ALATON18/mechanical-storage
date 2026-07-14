package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.content.logistics.filter.AttributeFilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.ListFilterItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TerminalBlockEntity extends KineticBlockEntity implements MenuProvider {
	public static final int MAX_CONNECTORS = 64;
	private static final int MAX_SUMMARY_ITEMS = 8;
	public static final int LIST_FILTER_SLOT = 0;
	public static final int ATTRIBUTE_FILTER_SLOT = 1;
	public static final int FILTER_SLOTS = 2;
	private boolean listFilterActive;
	private boolean attributeFilterActive;
	private final ItemStackHandler terminalFilters = new ItemStackHandler(FILTER_SLOTS) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return switch (slot) {
				case LIST_FILTER_SLOT -> stack.getItem() instanceof ListFilterItem;
				case ATTRIBUTE_FILTER_SLOT -> stack.getItem() instanceof AttributeFilterItem;
				default -> false;
			};
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
		super(type, pos, blockState);
	}

	public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
		this(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), pos, blockState);
	}

	public ItemStackHandler getTerminalFilters() {
		return terminalFilters;
	}

	public boolean isFilterActive(int slot) {
		return switch (slot) {
			case LIST_FILTER_SLOT -> listFilterActive;
			case ATTRIBUTE_FILTER_SLOT -> attributeFilterActive;
			default -> false;
		};
	}

	public void setFilterActive(int slot, boolean active) {
		boolean enabled = active && !terminalFilters.getStackInSlot(slot).isEmpty();
		if (slot == LIST_FILTER_SLOT) {
			listFilterActive = enabled;
		} else if (slot == ATTRIBUTE_FILTER_SLOT) {
			attributeFilterActive = enabled;
		} else {
			return;
		}
		setChanged();
		if (level != null && !level.isClientSide) {
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.put("TerminalFilters", terminalFilters.serializeNBT(registries));
		compound.putBoolean("ListFilterActive", listFilterActive);
		compound.putBoolean("AttributeFilterActive", attributeFilterActive);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		if (compound.contains("TerminalFilters")) {
			terminalFilters.deserializeNBT(registries, compound.getCompound("TerminalFilters"));
		}
		listFilterActive = compound.getBoolean("ListFilterActive") && !terminalFilters.getStackInSlot(LIST_FILTER_SLOT).isEmpty();
		attributeFilterActive = compound.getBoolean("AttributeFilterActive") && !terminalFilters.getStackInSlot(ATTRIBUTE_FILTER_SLOT).isEmpty();
	}

	public boolean isOnline() {
		return getSpeed() != 0 && hasNetwork();
	}

	public NetworkStatus getNetworkStatus() {
		if (isOnline()) {
			return NetworkStatus.ONLINE;
		}

		return hasKineticConnection() ? NetworkStatus.OVERSTRESSED : NetworkStatus.DISCONNECTED;
	}

	private boolean hasKineticConnection() {
		if (level == null) {
			return false;
		}

		Direction facing = getBlockState().getValue(DirectionalMachineBlock.FACING);
		BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
		return neighbour instanceof KineticBlockEntity;
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

		if (!isOnline()) {
			return getNetworkStatus().message();
		}

		NetworkSummary networkSummary = collectNetworkSummary();
		String message = "Terminal: online at " + Math.abs(getSpeed()) + " RPM, found " + networkSummary.connectorsFound + " connector(s), " + networkSummary.inventoriesFound + " inventory/inventories, " + networkSummary.occupiedSlots + "/" + networkSummary.totalSlots + " slots used, " + networkSummary.totalItems + " items total.";
		String summary = formatItemSummary(networkSummary.itemSummary);
		if (!summary.isEmpty()) {
			message += " Items: " + summary;
		}

		return Component.literal(message);
	}

	public DisplayPage getNetworkDisplayPage(int limit, int offset, String searchText, String sortMode, boolean descending) {
		NetworkSummary networkSummary = collectNetworkSummary();
		List<ItemSummary> entries = new ArrayList<>();
		String normalizedSearch = normalizeSearch(searchText);

		for (ItemSummary summary : networkSummary.itemSummary) {
			if (matchesTerminalFilters(summary.representative) && matchesSearch(summary, normalizedSearch)) {
				entries.add(summary);
			}
		}

		Comparator<ItemSummary> comparator;
		if ("NAME".equals(sortMode)) {
			comparator = Comparator.comparing(summary -> summary.displayName.toLowerCase(Locale.ROOT));
		} else {
			comparator = Comparator.comparingInt(summary -> summary.count);
		}

		if (descending) {
			comparator = comparator.reversed();
		}
		entries.sort(comparator.thenComparing(summary -> summary.itemId.toString()));

		int safeOffset = Math.max(0, Math.min(offset, entries.size()));
		int end = Math.min(entries.size(), safeOffset + Math.max(0, limit));
		List<ItemStack> stacks = new ArrayList<>(end - safeOffset);
		for (int index = safeOffset; index < end; index++) {
			ItemSummary summary = entries.get(index);
			ItemStack displayStack = summary.representative.copy();
			displayStack.setCount(summary.count);
			stacks.add(displayStack);
		}

		return new DisplayPage(stacks, entries.size());
	}

	private boolean matchesTerminalFilters(ItemStack stack) {
		if (level == null) {
			return false;
		}

		ItemStack listFilter = terminalFilters.getStackInSlot(LIST_FILTER_SLOT);
		if (listFilterActive && !listFilter.isEmpty() && !matchesModListFilter(listFilter, stack)) {
			return false;
		}

		ItemStack attributeFilter = terminalFilters.getStackInSlot(ATTRIBUTE_FILTER_SLOT);
		return !attributeFilterActive || attributeFilter.isEmpty() || FilterItemStack.of(attributeFilter.copy()).test(level, stack);
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
					return collected;
				}
			}
		}

		return collected;
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
			return ItemStack.EMPTY;
		}

		return insertIntoEmptySlots(handlers, remaining);
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
		NetworkSummary networkSummary = new NetworkSummary();
		if (!isOnline()) {
			return networkSummary;
		}

		for (MechanicalStorageConnectorBlockEntity connector : findNetworkConnectors()) {
			networkSummary.connectorsFound++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			networkSummary.inventoriesFound++;
			networkSummary.totalSlots += handler.getSlots();
			for (int slot = 0; slot < handler.getSlots(); slot++) {
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

	private List<MechanicalStorageConnectorBlockEntity> findNetworkConnectors() {
		return StorageNetworkRegistry.findConnectors(this, MAX_CONNECTORS);
	}

	private static void addToSummary(List<ItemSummary> itemSummary, ItemStack stack) {
		for (ItemSummary summary : itemSummary) {
			if (sameDisplayGroup(summary.representative, stack)) {
				summary.count = saturatedAdd(summary.count, stack.getCount());
				return;
			}
		}

		itemSummary.add(new ItemSummary(stack));
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

		String displayName = itemSummary.displayName.toLowerCase(Locale.ROOT);
		String path = itemSummary.itemId.getPath().toLowerCase(Locale.ROOT);
		if (searchText.startsWith("@")) {
			return itemSummary.itemId.getNamespace().toLowerCase(Locale.ROOT).contains(searchText.substring(1));
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
			if (tagKey.location().toString().toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
				return true;
			}
		}

		return false;
	}

	private static String formatItemSummary(List<ItemSummary> itemSummary) {
		if (itemSummary.isEmpty()) {
			return "";
		}

		List<ItemSummary> entries = new ArrayList<>(itemSummary);
		entries.sort(Comparator.comparingInt((ItemSummary summary) -> summary.count).reversed());
		StringBuilder builder = new StringBuilder();
		int shown = 0;
		for (ItemSummary summary : entries) {
			if (shown >= MAX_SUMMARY_ITEMS) {
				break;
			}

			if (!builder.isEmpty()) {
				builder.append(", ");
			}
			builder.append(summary.displayName).append(" x").append(summary.count);
			shown++;
		}

		int hidden = entries.size() - shown;
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
		DISCONNECTED;

		public Component message() {
			return switch (this) {
				case ONLINE -> Component.translatable("status.mechanical_storage.online");
				case OVERSTRESSED -> Component.translatable("status.mechanical_storage.overstressed");
				case DISCONNECTED -> Component.translatable("status.mechanical_storage.disconnected");
			};
		}
	}

	private static class NetworkSummary {
		private int connectorsFound;
		private int inventoriesFound;
		private int totalSlots;
		private int occupiedSlots;
		private int totalItems;
		private final List<ItemSummary> itemSummary = new ArrayList<>();
	}

	private static class ItemSummary {
		private final ItemStack representative;
		private final ResourceLocation itemId;
		private final String displayName;
		private int count;

		private ItemSummary(ItemStack stack) {
			this.representative = stack.copy();
			this.representative.setCount(1);
			this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			this.displayName = stack.getHoverName().getString();
			this.count = stack.getCount();
		}
	}
}
