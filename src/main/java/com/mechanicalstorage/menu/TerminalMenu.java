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

	private static final int PLAYER_INVENTORY_START = NETWORK_SLOTS;
	private static final int PLAYER_INVENTORY_SLOTS = 27;
	private static final int HOTBAR_START = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOTS;
	private static final int HOTBAR_SLOTS = 9;

	private final ItemStackHandler displayItems = new ItemStackHandler(NETWORK_SLOTS);
	private final Inventory playerInventory;
	private final BlockPos terminalPos;
	@Nullable
	private final TerminalBlockEntity terminal;
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
		refreshDisplay();
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(player.level(), terminalPos), player, MechanicalStorage.MECHANICAL_STORAGE_TERMINAL.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack clickedStack = slot.getItem();
		ItemStack originalStack = clickedStack.copy();

		if (isNetworkSlot(index)) {
			ItemStack extracted = extractDisplayedStackToInventory(clickedStack, clickedStack.getMaxStackSize(), player);
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
			Slot slot = this.slots.get(slotId);
			ItemStack displayedStack = slot.getItem();
			if (!displayedStack.isEmpty()) {
				if (clickType == ClickType.QUICK_MOVE) {
					extractDisplayedStackToInventory(displayedStack, displayedStack.getMaxStackSize(), player);
				} else if (clickType == ClickType.PICKUP) {
					int amount = button == 1 ? Math.max(1, Math.min(displayedStack.getCount(), displayedStack.getMaxStackSize()) / 2) : Math.min(displayedStack.getCount(), displayedStack.getMaxStackSize());
					extractDisplayedStackToCursor(displayedStack, amount);
				}

				refreshDisplay();
				broadcastChanges();
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

	private void addNetworkSlots() {
		int startX = 8;
		int startY = 18;

		for (int row = 0; row < GRID_ROWS; row++) {
			for (int column = 0; column < GRID_COLUMNS; column++) {
				int slot = column + row * GRID_COLUMNS;
				this.addSlot(new NetworkDisplaySlot(displayItems, slot, startX + column * 18, startY + row * 18));
			}
		}
	}

	private void addPlayerInventorySlots(Inventory inventory) {
		int startX = 8;
		int startY = 140;

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(inventory, column + row * 9 + 9, startX + column * 18, startY + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(inventory, column, startX + column * 18, 198));
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

		List<ItemStack> stacks = terminal.getNetworkDisplayStacks(NETWORK_SLOTS);
		for (int slot = 0; slot < Math.min(NETWORK_SLOTS, stacks.size()); slot++) {
			displayItems.setStackInSlot(slot, stacks.get(slot));
		}
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

	private ItemStack insertIntoNetwork(ItemStack stack) {
		if (terminal == null || stack.isEmpty()) {
			return stack;
		}

		return terminal.insertStackIntoNetwork(stack);
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
