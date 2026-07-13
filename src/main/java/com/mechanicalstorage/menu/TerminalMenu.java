package com.mechanicalstorage.menu;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TerminalMenu extends AbstractContainerMenu {
	public static final int GRID_COLUMNS = 9;
	public static final int GRID_ROWS = 6;
	public static final int NETWORK_SLOTS = GRID_COLUMNS * GRID_ROWS;
	public static final int SEARCH_CLEAR_BUTTON = 100000;
	public static final int SEARCH_BACKSPACE_BUTTON = 100001;
	public static final int SEARCH_APPLY_BUTTON = 100002;
	public static final int SORT_NAME_BUTTON = 100003;
	public static final int SORT_COUNT_BUTTON = 100004;
	public static final int SCROLL_UP_BUTTON = 100005;
	public static final int SCROLL_DOWN_BUTTON = 100006;
	public static final int SINGLE_DEPOSIT_BUTTON = 100007;
	public static final int SEARCH_CHAR_BASE_BUTTON = 200000;
	public static final int SINGLE_EXTRACT_SLOT_BASE_BUTTON = 300000;
	public static final int SEARCH_MAX_LENGTH = 64;

	private static final int PLAYER_INVENTORY_START = NETWORK_SLOTS;
	private static final int PLAYER_INVENTORY_SLOTS = 27;
	private static final int HOTBAR_START = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOTS;
	private static final int HOTBAR_SLOTS = 9;

	private final ItemStackHandler displayItems = new ItemStackHandler(NETWORK_SLOTS);
	private final Inventory playerInventory;
	private final BlockPos terminalPos;
	private final DataSlot scrollRow = DataSlot.standalone();
	private final DataSlot totalMatchingItems = DataSlot.standalone();
	private final DataSlot networkStatus = DataSlot.standalone();
	@Nullable
	private final TerminalBlockEntity terminal;
	private String searchText = "";
	private SortMode sortMode = SortMode.COUNT;
	private boolean sortDescending = true;
	private int refreshCooldown;

	public TerminalMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, playerInventory, buffer.readBlockPos(), null);
	}

	public TerminalMenu(int containerId, Inventory playerInventory, TerminalBlockEntity terminal) {
		this(containerId, playerInventory, terminal.getBlockPos(), terminal);
	}

	private TerminalMenu(int containerId, Inventory playerInventory, BlockPos terminalPos, @Nullable TerminalBlockEntity terminal) {
		super(MechanicalStorage.TERMINAL_MENU.get(), containerId);
		this.playerInventory = playerInventory;
		this.terminalPos = terminalPos;
		this.terminal = terminal;

		addNetworkSlots();
		addPlayerInventorySlots(playerInventory);
		addDataSlot(scrollRow);
		addDataSlot(totalMatchingItems);
		addDataSlot(networkStatus);
		refreshDisplay();
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(player.level(), terminalPos), player, MechanicalStorage.MECHANICAL_STORAGE_TERMINAL.get());
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == SEARCH_CLEAR_BUTTON) {
			searchText = "";
			scrollRow.set(0);
			refreshAfterSearchChange();
			return true;
		}

		if (id == SEARCH_BACKSPACE_BUTTON) {
			if (!searchText.isEmpty()) {
				searchText = searchText.substring(0, searchText.length() - 1);
				refreshAfterSearchChange();
			}
			return true;
		}

		if (id == SEARCH_APPLY_BUTTON) {
			refreshAfterSearchChange();
			return true;
		}

		if (id == SORT_NAME_BUTTON) {
			if (sortMode == SortMode.NAME) {
				sortDescending = !sortDescending;
			} else {
				sortMode = SortMode.NAME;
				sortDescending = false;
			}
			scrollRow.set(0);
			refreshAfterSearchChange();
			return true;
		}

		if (id == SORT_COUNT_BUTTON) {
			if (sortMode == SortMode.COUNT) {
				sortDescending = !sortDescending;
			} else {
				sortMode = SortMode.COUNT;
				sortDescending = true;
			}
			scrollRow.set(0);
			refreshAfterSearchChange();
			return true;
		}

		if (id == SCROLL_UP_BUTTON) {
			scrollByRows(-1);
			return true;
		}

		if (id == SCROLL_DOWN_BUTTON) {
			scrollByRows(1);
			return true;
		}

		if (id == SINGLE_DEPOSIT_BUTTON) {
			depositCarriedStack(1);
			refreshAfterInteraction();
			return true;
		}

		if (id >= SINGLE_EXTRACT_SLOT_BASE_BUTTON && id < SINGLE_EXTRACT_SLOT_BASE_BUTTON + NETWORK_SLOTS) {
			int slot = id - SINGLE_EXTRACT_SLOT_BASE_BUTTON;
			ItemStack displayedStack = displayItems.getStackInSlot(slot);
			if (!displayedStack.isEmpty()) {
				extractDisplayedStackToCursor(displayedStack, 1);
				refreshAfterInteraction();
			}
			return true;
		}

		if (id >= SEARCH_CHAR_BASE_BUTTON && id <= SEARCH_CHAR_BASE_BUTTON + Character.MAX_VALUE) {
			char character = (char) (id - SEARCH_CHAR_BASE_BUTTON);
			if (searchText.length() < SEARCH_MAX_LENGTH && isAllowedSearchCharacter(character)) {
				searchText += character;
			}
			return true;
		}

		return super.clickMenuButton(player, id);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= this.slots.size()) {
			return ItemStack.EMPTY;
		}

		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack clickedStack = slot.getItem();
		ItemStack originalStack = clickedStack.copy();

		if (isNetworkSlot(index)) {
			int amount = getOneStackExtractAmount(index, clickedStack);
			ItemStack extracted = extractDisplayedStackToInventory(clickedStack, amount, player);
			refreshDisplay();
			return extracted.isEmpty() ? ItemStack.EMPTY : originalStack;
		}

		if (isPlayerSlot(index)) {
			ItemStack remaining = insertIntoNetwork(clickedStack.copy());
			if (ItemStack.matches(remaining, clickedStack)) {
				return ItemStack.EMPTY;
			}

			if (remaining.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.set(remaining);
			}

			slot.setChanged();
			refreshDisplay();
			return originalStack;
		}

		return ItemStack.EMPTY;
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (isNetworkSlot(slotId)) {
			ItemStack carried = getCarried();
			if (!carried.isEmpty()) {
				int amount = button == 1 ? Math.max(1, (carried.getCount() + 1) / 2) : carried.getCount();
				depositCarriedStack(amount);
				refreshAfterInteraction();
				return;
			}

			Slot slot = this.slots.get(slotId);
			ItemStack displayedStack = slot.getItem();
			if (!displayedStack.isEmpty()) {
				int oneStackAmount = getOneStackExtractAmount(slotId, displayedStack);
				if (clickType == ClickType.QUICK_MOVE) {
					extractDisplayedStackToInventory(displayedStack, oneStackAmount, player);
				} else if (clickType == ClickType.PICKUP) {
					int amount = button == 1 ? Math.max(1, oneStackAmount / 2) : oneStackAmount;
					extractDisplayedStackToCursor(displayedStack, amount);
				}

				refreshAfterInteraction();
			}
			return;
		}

		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public void broadcastChanges() {
		if (terminal != null) {
			refreshCooldown--;
			if (refreshCooldown <= 0) {
				refreshDisplay();
				refreshCooldown = 10;
			}
		}

		super.broadcastChanges();
	}

	public int getNetworkSlotCount(int slot) {
		if (!isNetworkSlot(slot)) {
			return 0;
		}

		return displayItems.getStackInSlot(slot).getCount();
	}

	public int getScrollRow() {
		return scrollRow.get();
	}

	public int getTotalMatchingItems() {
		return totalMatchingItems.get();
	}

	public int getMaximumScrollRow() {
		return Math.max(0, (getTotalMatchingItems() + GRID_COLUMNS - 1) / GRID_COLUMNS - GRID_ROWS);
	}

	public TerminalBlockEntity.NetworkStatus getNetworkStatus() {
		int status = networkStatus.get();
		TerminalBlockEntity.NetworkStatus[] values = TerminalBlockEntity.NetworkStatus.values();
		return status >= 0 && status < values.length ? values[status] : TerminalBlockEntity.NetworkStatus.DISCONNECTED;
	}

	private void addNetworkSlots() {
		int startX = 32;
		int startY = 47;

		for (int row = 0; row < GRID_ROWS; row++) {
			for (int column = 0; column < GRID_COLUMNS; column++) {
				int slot = column + row * GRID_COLUMNS;
				this.addSlot(new NetworkDisplaySlot(displayItems, slot, startX + column * 18, startY + row * 18));
			}
		}
	}

	private void addPlayerInventorySlots(Inventory inventory) {
		int startX = 32;
		int startY = 170;

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(inventory, column + row * 9 + 9, startX + column * 18, startY + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(inventory, column, startX + column * 18, 228));
		}
	}

	private boolean isNetworkSlot(int index) {
		return index >= 0 && index < NETWORK_SLOTS;
	}

	private boolean isPlayerSlot(int index) {
		return index >= PLAYER_INVENTORY_START && index < HOTBAR_START + HOTBAR_SLOTS;
	}

	private void refreshDisplay() {
		for (int slot = 0; slot < NETWORK_SLOTS; slot++) {
			displayItems.setStackInSlot(slot, ItemStack.EMPTY);
		}

		if (terminal == null) {
			return;
		}

		networkStatus.set(terminal.getNetworkStatus().ordinal());
		TerminalBlockEntity.DisplayPage page = terminal.getNetworkDisplayPage(NETWORK_SLOTS, scrollRow.get() * GRID_COLUMNS, searchText, sortMode.name(), sortDescending);
		totalMatchingItems.set(page.totalItems());
		int maximumScrollRow = getMaximumScrollRow();
		if (scrollRow.get() > maximumScrollRow) {
			scrollRow.set(maximumScrollRow);
			page = terminal.getNetworkDisplayPage(NETWORK_SLOTS, scrollRow.get() * GRID_COLUMNS, searchText, sortMode.name(), sortDescending);
		}

		List<ItemStack> stacks = page.stacks();
		for (int slot = 0; slot < Math.min(NETWORK_SLOTS, stacks.size()); slot++) {
			displayItems.setStackInSlot(slot, stacks.get(slot).copy());
		}
	}

	private void refreshAfterSearchChange() {
		refreshDisplay();
		broadcastChanges();
	}

	private void refreshAfterInteraction() {
		refreshDisplay();
		broadcastChanges();
	}

	private void scrollByRows(int rows) {
		int nextRow = Math.max(0, Math.min(getMaximumScrollRow(), scrollRow.get() + rows));
		if (nextRow != scrollRow.get()) {
			scrollRow.set(nextRow);
			refreshAfterInteraction();
		}
	}

	private int getOneStackExtractAmount(int slot, ItemStack displayedStack) {
		int available = getNetworkSlotCount(slot);
		if (available <= 0) {
			available = displayedStack.getCount();
		}

		return Math.min(available, displayedStack.getMaxStackSize());
	}

	private ItemStack extractDisplayedStackToInventory(ItemStack displayedStack, int amount, Player player) {
		if (terminal == null || displayedStack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		return terminal.extractMatchingStackToPlayer(displayedStack, amount, player);
	}

	private void extractDisplayedStackToCursor(ItemStack displayedStack, int amount) {
		if (terminal == null || displayedStack.isEmpty() || amount <= 0) {
			return;
		}

		ItemStack carried = getCarried();
		if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, displayedStack)) {
			return;
		}

		int space = carried.isEmpty() ? displayedStack.getMaxStackSize() : carried.getMaxStackSize() - carried.getCount();
		int amountToExtract = Math.min(amount, space);
		if (amountToExtract <= 0) {
			return;
		}

		ItemStack extracted = terminal.extractMatchingStack(displayedStack, amountToExtract);
		if (extracted.isEmpty()) {
			return;
		}

		if (carried.isEmpty()) {
			setCarried(extracted.copy());
		} else {
			carried.grow(extracted.getCount());
			setCarried(carried);
		}
	}

	private void depositCarriedStack(int amount) {
		ItemStack carried = getCarried();
		if (carried.isEmpty() || amount <= 0) {
			return;
		}

		int amountToInsert = Math.min(amount, carried.getCount());
		ItemStack offered = carried.copyWithCount(amountToInsert);
		ItemStack remaining = insertIntoNetwork(offered);
		int inserted = amountToInsert - remaining.getCount();
		if (inserted > 0) {
			carried.shrink(inserted);
			setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
		}
	}

	private ItemStack insertIntoNetwork(ItemStack stack) {
		if (terminal == null || stack.isEmpty()) {
			return stack;
		}

		return terminal.insertStackIntoNetwork(stack);
	}

	private static boolean isAllowedSearchCharacter(char character) {
		return character >= 32 && character != 127;
	}

	private enum SortMode {
		NAME,
		COUNT
	}

	private static class NetworkDisplaySlot extends SlotItemHandler {
		private NetworkDisplaySlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
			super(itemHandler, index, xPosition, yPosition);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return true;
		}
	}
}
