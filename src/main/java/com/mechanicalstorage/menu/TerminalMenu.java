package com.mechanicalstorage.menu;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.simibubi.create.content.logistics.filter.AttributeFilterItem;
import com.simibubi.create.content.logistics.filter.ListFilterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TerminalMenu extends AbstractContainerMenu {
	public static final int GRID_COLUMNS = 9;
	public static final int DEFAULT_GRID_ROWS = 6;
	public static final int MAX_GRID_ROWS = 18;
	public static final int NETWORK_SLOTS = GRID_COLUMNS * MAX_GRID_ROWS;
	public static final int FILTER_SLOTS = TerminalBlockEntity.FILTER_SLOTS;
	public static final int NETWORK_SLOT_X = 32;
	public static final int NETWORK_SLOT_Y = 39;
	public static final int PLAYER_INVENTORY_Y = NETWORK_SLOT_Y + MAX_GRID_ROWS * 18 + 11;
	public static final int FILTER_SLOT_X = 212;
	public static final int FILTER_SLOT_Y = 17;
	public static final int FILTER_SLOT_SPACING = 18;
	public static final int CRAFTING_INPUT_X = NETWORK_SLOT_X + 18;
	public static final int CRAFTING_INPUT_Y = PLAYER_INVENTORY_Y;
	public static final int CRAFTING_RESULT_X = 140;
	public static final int CRAFTING_RESULT_Y = CRAFTING_INPUT_Y + 18;
	public static final int CRAFTING_SECTION_HEIGHT = 69;
	public static final int JEI_TRANSFER_BEGIN_BUTTON = 90000;
	public static final int JEI_TRANSFER_BEGIN_MAX_BUTTON = 90001;
	public static final int JEI_TRANSFER_FINISH_BUTTON = 90002;
	public static final int SEARCH_CLEAR_BUTTON = 100000;
	public static final int SEARCH_BACKSPACE_BUTTON = 100001;
	public static final int SEARCH_APPLY_BUTTON = 100002;
	public static final int SORT_CYCLE_BUTTON = 100003;
	public static final int DISPLAY_MODE_BUTTON = 100004;
	public static final int SCROLL_UP_BUTTON = 100005;
	public static final int SCROLL_DOWN_BUTTON = 100006;
	public static final int SINGLE_DEPOSIT_BUTTON = 100007;
	public static final int TOGGLE_FILTER_BUTTON_BASE = 100008;
	public static final int RETURN_CRAFTING_BUTTON = 100012;
	public static final int CRAFTING_TO_INVENTORY_BUTTON = 100013;
	public static final int SEARCH_CHAR_BASE_BUTTON = 200000;
	public static final int SINGLE_EXTRACT_SLOT_BASE_BUTTON = 300000;
	public static final int GRID_ROWS_BUTTON_BASE = 400000;
	public static final int SCROLL_TO_ROW_BASE_BUTTON = 500000;
	public static final int SEARCH_MAX_LENGTH = 64;

	private static final int JEI_TRANSFER_ITEM_FLAG = 0x40000000;
	private static final int JEI_TRANSFER_SLOT_SHIFT = 26;
	private static final int JEI_TRANSFER_COUNT_SHIFT = 20;
	private static final int JEI_TRANSFER_ITEM_MASK = 0xFFFFF;

	public static final int FILTER_SLOT_START = NETWORK_SLOTS;
	public static final int CRAFTING_INPUT_SLOT_START = FILTER_SLOT_START + FILTER_SLOTS;
	public static final int CRAFTING_INPUT_SLOTS = 9;
	public static final int CRAFTING_RESULT_SLOT = CRAFTING_INPUT_SLOT_START + CRAFTING_INPUT_SLOTS;
	private static final int PLAYER_INVENTORY_SLOTS = 27;
	private static final int HOTBAR_SLOTS = 9;

	private final ItemStackHandler displayItems = new ItemStackHandler(NETWORK_SLOTS);
	private final ItemStackHandler filterItems;
	private final Inventory playerInventory;
	private final BlockPos terminalPos;
	private final boolean craftingTerminal;
	private final TransientCraftingContainer craftingItems = new TransientCraftingContainer(this, 3, 3);
	private final ResultContainer craftingResult = new ResultContainer();
	private final int playerInventoryStart;
	private final int hotbarStart;
	private final DataSlot scrollRow = DataSlot.standalone();
	private final DataSlot totalMatchingItems = DataSlot.standalone();
	private final DataSlot networkStatus = DataSlot.standalone();
	private final DataSlot visibleRows = DataSlot.standalone();
	private final DataSlot sortModeState = DataSlot.standalone();
	private final DataSlot sortDescendingState = DataSlot.standalone();
	private final DataSlot displayModeState = DataSlot.standalone();
	private final DataSlot[] filterActive = new DataSlot[FILTER_SLOTS];
	private final DataSlot[] networkSlotCountLow = new DataSlot[NETWORK_SLOTS];
	private final DataSlot[] networkSlotCountHigh = new DataSlot[NETWORK_SLOTS];
	private final DataSlot[] networkSlotFluid = new DataSlot[NETWORK_SLOTS];
	private final FluidStack[] displayFluids = new FluidStack[NETWORK_SLOTS];
	@Nullable
	private final TerminalBlockEntity terminal;
	private String searchText = "";
	private int refreshCooldown;
	private boolean jeiMaxTransfer;

	public TerminalMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, playerInventory, readOpeningData(buffer), null);
	}

	public TerminalMenu(int containerId, Inventory playerInventory, TerminalBlockEntity terminal) {
		this(containerId, playerInventory,
				new OpeningData(terminal.getBlockPos(), terminal.isCraftingTerminal()), terminal);
	}

	private TerminalMenu(int containerId, Inventory playerInventory, OpeningData openingData,
			@Nullable TerminalBlockEntity terminal) {
		super(MechanicalStorage.TERMINAL_MENU.get(), containerId);
		this.playerInventory = playerInventory;
		this.terminalPos = openingData.terminalPos();
		this.craftingTerminal = openingData.craftingTerminal();
		this.terminal = terminal;
		this.filterItems = terminal == null ? new ItemStackHandler(FILTER_SLOTS) : terminal.getTerminalFilters();
		this.playerInventoryStart = CRAFTING_INPUT_SLOT_START
				+ (craftingTerminal ? CRAFTING_INPUT_SLOTS + 1 : 0);
		this.hotbarStart = playerInventoryStart + PLAYER_INVENTORY_SLOTS;
		this.visibleRows.set(DEFAULT_GRID_ROWS);
		setSortState(SortMode.COUNT, true);
		setDisplayMode(DisplayMode.ITEMS);
		Arrays.fill(displayFluids, FluidStack.EMPTY);

		addNetworkSlots();
		addTerminalFilterSlots();
		if (craftingTerminal) {
			addCraftingSlots(playerInventory.player);
		}
		addPlayerInventorySlots(playerInventory);
		addDataSlot(scrollRow);
		addDataSlot(totalMatchingItems);
		addDataSlot(networkStatus);
		addDataSlot(visibleRows);
		addDataSlot(sortModeState);
		addDataSlot(sortDescendingState);
		addDataSlot(displayModeState);
		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			filterActive[slot] = DataSlot.standalone();
			addDataSlot(filterActive[slot]);
		}
		for (int slot = 0; slot < NETWORK_SLOTS; slot++) {
			networkSlotCountLow[slot] = DataSlot.standalone();
			networkSlotCountHigh[slot] = DataSlot.standalone();
			networkSlotFluid[slot] = DataSlot.standalone();
			addDataSlot(networkSlotCountLow[slot]);
			addDataSlot(networkSlotCountHigh[slot]);
			addDataSlot(networkSlotFluid[slot]);
		}
		refreshDisplay();
	}

	private static OpeningData readOpeningData(RegistryFriendlyByteBuf buffer) {
		return new OpeningData(buffer.readBlockPos(), buffer.readBoolean());
	}

	public static int encodeJeiTransferItemButton(int craftingSlot, int rawItemId, int count) {
		if (craftingSlot < 0 || craftingSlot >= CRAFTING_INPUT_SLOTS
				|| rawItemId < 0 || rawItemId > JEI_TRANSFER_ITEM_MASK) {
			return -1;
		}
		int encodedCount = Math.max(0, Math.min(63, count - 1));
		return JEI_TRANSFER_ITEM_FLAG
				| ((craftingSlot & 0xF) << JEI_TRANSFER_SLOT_SHIFT)
				| ((encodedCount & 0x3F) << JEI_TRANSFER_COUNT_SHIFT)
				| (rawItemId & JEI_TRANSFER_ITEM_MASK);
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(player.level(), terminalPos), player,
				craftingTerminal
						? MechanicalStorage.CRAFTING_TERMINAL.get()
						: MechanicalStorage.TERMINAL.get());
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return (!craftingTerminal || slots.indexOf(slot) != CRAFTING_RESULT_SLOT)
				&& super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == JEI_TRANSFER_BEGIN_BUTTON || id == JEI_TRANSFER_BEGIN_MAX_BUTTON) {
			if (craftingTerminal && terminal != null) {
				prepareCraftingGridForJei(player);
				jeiMaxTransfer = id == JEI_TRANSFER_BEGIN_MAX_BUTTON;
				refreshAfterInteraction();
			}
			return true;
		}

		if ((id & JEI_TRANSFER_ITEM_FLAG) != 0) {
			if (craftingTerminal && terminal != null) {
				handleJeiTransferItemButton(id);
			}
			return true;
		}

		if (id == JEI_TRANSFER_FINISH_BUTTON) {
			if (craftingTerminal && terminal != null) {
				slotsChanged(craftingItems);
				refreshAfterInteraction();
			}
			return true;
		}

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

		if (id == SORT_CYCLE_BUTTON) {
			cycleSortState();
			scrollRow.set(0);
			refreshAfterSearchChange();
			return true;
		}

		if (id == DISPLAY_MODE_BUTTON) {
			setDisplayMode(getDisplayMode().next());
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

		if (id == RETURN_CRAFTING_BUTTON && craftingTerminal) {
			returnCraftingItemsToStorage();
			refreshAfterInteraction();
			return true;
		}

		if (id == CRAFTING_TO_INVENTORY_BUTTON && craftingTerminal) {
			moveCraftingItemsToInventory();
			refreshAfterInteraction();
			return true;
		}

		if (id >= TOGGLE_FILTER_BUTTON_BASE && id < TOGGLE_FILTER_BUTTON_BASE + FILTER_SLOTS) {
			toggleFilter(id - TOGGLE_FILTER_BUTTON_BASE);
			return true;
		}

		if (id >= SINGLE_EXTRACT_SLOT_BASE_BUTTON && id < SINGLE_EXTRACT_SLOT_BASE_BUTTON + NETWORK_SLOTS) {
			int slot = id - SINGLE_EXTRACT_SLOT_BASE_BUTTON;
			ItemStack displayedStack = displayItems.getStackInSlot(slot);
			ItemStack carried = getCarried();
			ItemStack extractionTarget = carried.isEmpty() ? displayedStack : carried;
			if (!extractionTarget.isEmpty()) {
				extractDisplayedStackToCursor(extractionTarget, 1);
				refreshAfterInteraction();
			}
			return true;
		}

		if (id >= GRID_ROWS_BUTTON_BASE && id <= GRID_ROWS_BUTTON_BASE + MAX_GRID_ROWS) {
			setVisibleRows(id - GRID_ROWS_BUTTON_BASE);
			return true;
		}

		if (id >= SCROLL_TO_ROW_BASE_BUTTON) {
			setScrollRow(id - SCROLL_TO_ROW_BASE_BUTTON);
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
			if (isNetworkSlotFluid(index)) {
				return ItemStack.EMPTY;
			}
			int amount = getOneStackExtractAmount(index, clickedStack);
			ItemStack extracted = extractDisplayedStackToInventory(clickedStack, amount, player);
			refreshDisplay();
			return extracted.isEmpty() ? ItemStack.EMPTY : originalStack;
		}

		if (isFilterSlot(index)) {
			if (!moveItemStackTo(clickedStack, playerInventoryStart, hotbarStart + HOTBAR_SLOTS, true)) {
				return ItemStack.EMPTY;
			}

			slot.set(clickedStack.isEmpty() ? ItemStack.EMPTY : clickedStack);
			slot.setChanged();
			refreshDisplay();
			return originalStack;
		}

		if (isCraftingResultSlot(index)) {
			return quickMoveCraftingResult(player);
		}

		if (isCraftingInputSlot(index)) {
			ItemStack remaining = insertIntoNetwork(clickedStack.copy());
			if (!ItemStack.matches(remaining, clickedStack)) {
				slot.set(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
				slot.setChanged();
				refreshDisplay();
				return originalStack;
			}

			if (!moveItemStackTo(clickedStack, playerInventoryStart, hotbarStart + HOTBAR_SLOTS, true)) {
				return ItemStack.EMPTY;
			}

			slot.set(clickedStack.isEmpty() ? ItemStack.EMPTY : clickedStack);
			slot.setChanged();
			return originalStack;
		}

		if (isPlayerSlot(index)) {
			if (isSupportedFilter(clickedStack)
					&& moveItemStackTo(clickedStack, FILTER_SLOT_START, FILTER_SLOT_START + FILTER_SLOTS, false)) {
				slot.set(clickedStack.isEmpty() ? ItemStack.EMPTY : clickedStack);
				slot.setChanged();
				refreshDisplay();
				return originalStack;
			}

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
		if (isCraftingResultSlot(slotId) && clickType == ClickType.QUICK_MOVE) {
			if (terminal != null) {
				craftOneStackToInventory(player);
				refreshAfterInteraction();
			}
			return;
		}

		if (isCraftingResultSlot(slotId)) {
			ItemStack[] craftingTemplates = snapshotCraftingTemplates();
			super.clicked(slotId, button, clickType, player);
			refillConsumedCraftingSlots(craftingTemplates);
			refreshAfterInteraction();
			return;
		}

		if (isNetworkSlot(slotId)) {
			if (isNetworkSlotFluid(slotId)) {
				if (button == 1 && clickType == ClickType.PICKUP) {
					tryFillBucketFromDisplayedFluid(slotId, player);
					refreshAfterInteraction();
				}
				return;
			}

			ItemStack carried = getCarried();
			if (!carried.isEmpty()) {
				int amount = button == 1 ? 1 : carried.getCount();
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

		if (isFilterSlot(slotId)) {
			super.clicked(slotId, button, clickType, player);
			refreshAfterInteraction();
			return;
		}

		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public void slotsChanged(Container container) {
		if (craftingTerminal && container == craftingItems && terminal != null
				&& playerInventory.player instanceof ServerPlayer serverPlayer) {
			List<ItemStack> inputItems = new ArrayList<>(CRAFTING_INPUT_SLOTS);
			for (int slot = 0; slot < CRAFTING_INPUT_SLOTS; slot++) {
				inputItems.add(craftingItems.getItem(slot));
			}
			CraftingInput input = CraftingInput.of(3, 3, inputItems);
			Optional<RecipeHolder<CraftingRecipe>> recipe = serverPlayer.level().getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, input, serverPlayer.level());
			ItemStack result = ItemStack.EMPTY;
			if (recipe.isPresent()
					&& craftingResult.setRecipeUsed(serverPlayer.level(), serverPlayer, recipe.get())) {
				result = recipe.get().value().assemble(input, serverPlayer.level().registryAccess());
			}
			craftingResult.setItem(0, result);
		}
		super.slotsChanged(container);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!craftingTerminal || terminal == null || player.level().isClientSide) {
			return;
		}

		for (int slot = 0; slot < craftingItems.getContainerSize(); slot++) {
			ItemStack stack = craftingItems.removeItemNoUpdate(slot);
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack remaining = insertIntoNetwork(stack);
			if (!remaining.isEmpty()) {
				ItemHandlerHelper.giveItemToPlayer(player, remaining);
			}
		}
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

		int low = networkSlotCountLow[slot].get() & 0xFFFF;
		int high = networkSlotCountHigh[slot].get() & 0x7FFF;
		return (high << 16) | low;
	}

	public int getScrollRow() {
		return scrollRow.get();
	}

	public int getTotalMatchingItems() {
		return totalMatchingItems.get();
	}

	public int getVisibleRows() {
		return Math.max(1, Math.min(MAX_GRID_ROWS, visibleRows.get()));
	}

	public boolean isCraftingTerminal() {
		return craftingTerminal;
	}

	public int getFirstTranslatedSlotIndex() {
		return craftingTerminal ? CRAFTING_INPUT_SLOT_START : playerInventoryStart;
	}

	public boolean isSortingByName() {
		return getSortMode() == SortMode.NAME;
	}

	public boolean isSortDescending() {
		return sortDescendingState.get() != 0;
	}

	public int getSortState() {
		if (getSortMode() == SortMode.NAME) {
			return isSortDescending() ? 1 : 0;
		}
		return isSortDescending() ? 2 : 3;
	}

	public int getDisplayModeState() {
		return getDisplayMode().ordinal();
	}

	public boolean isNetworkSlotFluid(int slot) {
		return isNetworkSlot(slot) && networkSlotFluid[slot].get() != 0;
	}

	public void setVisibleRowsClient(int rows) {
		visibleRows.set(clampVisibleRows(rows));
		scrollRow.set(Math.min(scrollRow.get(), getMaximumScrollRow()));
	}

	public void setScrollRowClient(int row) {
		scrollRow.set(Math.max(0, Math.min(getMaximumScrollRow(), row)));
	}

	public int getMaximumScrollRow() {
		return Math.max(0, (getTotalMatchingItems() + GRID_COLUMNS - 1) / GRID_COLUMNS - getVisibleRows());
	}

	public boolean isFilterSlotIndex(int index) {
		return isFilterSlot(index);
	}

	public ItemStack getTerminalFilter(int slot) {
		return slot >= 0 && slot < FILTER_SLOTS ? filterItems.getStackInSlot(slot) : ItemStack.EMPTY;
	}

	public boolean isFilterActive(int slot) {
		return slot >= 0 && slot < FILTER_SLOTS && filterActive[slot].get() != 0;
	}

	public TerminalBlockEntity.NetworkStatus getNetworkStatus() {
		int status = networkStatus.get();
		TerminalBlockEntity.NetworkStatus[] values = TerminalBlockEntity.NetworkStatus.values();
		return status >= 0 && status < values.length ? values[status] : TerminalBlockEntity.NetworkStatus.DISCONNECTED;
	}

	private void addNetworkSlots() {
		for (int row = 0; row < MAX_GRID_ROWS; row++) {
			for (int column = 0; column < GRID_COLUMNS; column++) {
				int slot = column + row * GRID_COLUMNS;
				this.addSlot(new NetworkDisplaySlot(this, displayItems, slot, row, NETWORK_SLOT_X + column * 18, NETWORK_SLOT_Y + row * 18));
			}
		}
	}

	private void addTerminalFilterSlots() {
		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			this.addSlot(new TerminalFilterSlot(filterItems, slot, FILTER_SLOT_X, FILTER_SLOT_Y + slot * FILTER_SLOT_SPACING));
		}
	}

	private void addCraftingSlots(Player player) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				this.addSlot(new Slot(craftingItems, column + row * 3,
						CRAFTING_INPUT_X + column * 18, CRAFTING_INPUT_Y + row * 18));
			}
		}

		this.addSlot(new ResultSlot(player, craftingItems, craftingResult, 0,
				CRAFTING_RESULT_X, CRAFTING_RESULT_Y));
	}

	private void addPlayerInventorySlots(Inventory inventory) {
		int inventoryY = PLAYER_INVENTORY_Y + (craftingTerminal ? CRAFTING_SECTION_HEIGHT : 0);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(inventory, column + row * 9 + 9,
						NETWORK_SLOT_X + column * 18, inventoryY + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(inventory, column, NETWORK_SLOT_X + column * 18, inventoryY + 58));
		}
	}

	private boolean isNetworkSlot(int index) {
		return index >= 0 && index < NETWORK_SLOTS;
	}

	private boolean isPlayerSlot(int index) {
		return index >= playerInventoryStart && index < hotbarStart + HOTBAR_SLOTS;
	}

	private boolean isFilterSlot(int index) {
		return index >= FILTER_SLOT_START && index < FILTER_SLOT_START + FILTER_SLOTS;
	}

	private boolean isCraftingInputSlot(int index) {
		return craftingTerminal && index >= CRAFTING_INPUT_SLOT_START && index < CRAFTING_RESULT_SLOT;
	}

	private boolean isCraftingResultSlot(int index) {
		return craftingTerminal && index == CRAFTING_RESULT_SLOT;
	}

	private void refreshDisplay() {
		// The client menu has no terminal. Its predicted click handling must leave the
		// server-synchronised display alone or it clears every visible result before
		// the authoritative click response arrives.
		if (terminal == null) {
			return;
		}

		for (int slot = 0; slot < NETWORK_SLOTS; slot++) {
			displayItems.setStackInSlot(slot, ItemStack.EMPTY);
			displayFluids[slot] = FluidStack.EMPTY;
			setNetworkSlotCount(slot, 0);
			networkSlotFluid[slot].set(0);
		}

		for (int slot = 0; slot < FILTER_SLOTS; slot++) {
			filterActive[slot].set(terminal.isFilterActive(slot) ? 1 : 0);
		}

		networkStatus.set(terminal.getNetworkStatus().ordinal());
		TerminalBlockEntity.DisplayPage page = terminal.getNetworkDisplayPage(NETWORK_SLOTS,
				scrollRow.get() * GRID_COLUMNS, searchText, getSortMode().name(), isSortDescending(),
				getDisplayMode().name());
		totalMatchingItems.set(page.totalItems());
		int maximumScrollRow = getMaximumScrollRow();
		if (scrollRow.get() > maximumScrollRow) {
			scrollRow.set(maximumScrollRow);
			page = terminal.getNetworkDisplayPage(NETWORK_SLOTS, scrollRow.get() * GRID_COLUMNS, searchText,
					getSortMode().name(), isSortDescending(), getDisplayMode().name());
		}

		List<TerminalBlockEntity.DisplayEntry> entries = page.entries();
		for (int slot = 0; slot < Math.min(NETWORK_SLOTS, entries.size()); slot++) {
			TerminalBlockEntity.DisplayEntry entry = entries.get(slot);
			setNetworkSlotCount(slot, entry.amount());
			networkSlotFluid[slot].set(entry.isFluid() ? 1 : 0);
			displayFluids[slot] = entry.fluid().copy();
			displayItems.setStackInSlot(slot, entry.icon().copyWithCount(1));
		}
	}

	private void setNetworkSlotCount(int slot, int count) {
		int safeCount = Math.max(0, count);
		networkSlotCountLow[slot].set(safeCount & 0xFFFF);
		networkSlotCountHigh[slot].set((safeCount >>> 16) & 0x7FFF);
	}

	private void toggleFilter(int slot) {
		if (terminal == null || terminal.getTerminalFilters().getStackInSlot(slot).isEmpty()) {
			return;
		}
		terminal.setFilterActive(slot, !terminal.isFilterActive(slot));
		refreshAfterInteraction();
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
		setScrollRow(scrollRow.get() + rows);
	}

	private void setScrollRow(int row) {
		int nextRow = Math.max(0, Math.min(getMaximumScrollRow(), row));
		if (nextRow == scrollRow.get()) {
			return;
		}
		scrollRow.set(nextRow);
		refreshAfterInteraction();
	}

	private void setVisibleRows(int rows) {
		int clampedRows = clampVisibleRows(rows);
		if (clampedRows == visibleRows.get()) {
			return;
		}
		visibleRows.set(clampedRows);
		scrollRow.set(Math.min(scrollRow.get(), getMaximumScrollRow()));
		refreshAfterInteraction();
	}

	private static int clampVisibleRows(int rows) {
		return Math.max(1, Math.min(MAX_GRID_ROWS, rows));
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

	private boolean tryFillBucketFromDisplayedFluid(int slot, Player player) {
		if (terminal == null || !isNetworkSlotFluid(slot)) {
			return false;
		}

		FluidStack fluid = displayFluids[slot];
		if (fluid.isEmpty() || getNetworkSlotCount(slot) < TerminalBlockEntity.BUCKET_VOLUME) {
			return false;
		}

		ItemStack carried = getCarried();
		if (carried.is(Items.BUCKET)) {
			ItemStack filledBucket = terminal.extractFluidBucket(fluid);
			if (filledBucket.isEmpty()) {
				return false;
			}
			if (carried.getCount() == 1) {
				setCarried(filledBucket);
			} else {
				carried.shrink(1);
				setCarried(carried);
				ItemHandlerHelper.giveItemToPlayer(player, filledBucket);
			}
			return true;
		}

		if (!carried.isEmpty()) {
			return false;
		}

		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(Items.BUCKET)) {
				continue;
			}

			ItemStack filledBucket = terminal.extractFluidBucket(fluid);
			if (filledBucket.isEmpty()) {
				return false;
			}
			if (held.getCount() == 1) {
				player.setItemInHand(hand, filledBucket);
			} else {
				held.shrink(1);
				player.setItemInHand(hand, held);
				ItemHandlerHelper.giveItemToPlayer(player, filledBucket);
			}
			return true;
		}

		return false;
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

	private void returnCraftingItemsToStorage() {
		if (!craftingTerminal || terminal == null) {
			return;
		}

		for (int slot = 0; slot < craftingItems.getContainerSize(); slot++) {
			ItemStack stack = craftingItems.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack remaining = insertIntoNetwork(stack.copy());
			if (!ItemStack.matches(remaining, stack)) {
				craftingItems.setItem(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
			}
		}
	}

	private void moveCraftingItemsToInventory() {
		if (!craftingTerminal) {
			return;
		}

		for (int slot = 0; slot < craftingItems.getContainerSize(); slot++) {
			ItemStack stack = craftingItems.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			if (moveItemStackTo(stack, playerInventoryStart, hotbarStart + HOTBAR_SLOTS, true)) {
				craftingItems.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
			}
		}
		slotsChanged(craftingItems);
	}

	private void prepareCraftingGridForJei(Player player) {
		if (!craftingTerminal || terminal == null) {
			return;
		}

		for (int slot = 0; slot < craftingItems.getContainerSize(); slot++) {
			ItemStack stack = craftingItems.removeItemNoUpdate(slot);
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack remaining = insertIntoNetwork(stack);
			if (!remaining.isEmpty()) {
				ItemHandlerHelper.giveItemToPlayer(player, remaining);
			}
		}
		craftingResult.setItem(0, ItemStack.EMPTY);
	}

	private void handleJeiTransferItemButton(int id) {
		int targetSlot = (id >>> JEI_TRANSFER_SLOT_SHIFT) & 0xF;
		if (targetSlot < 0 || targetSlot >= CRAFTING_INPUT_SLOTS || !craftingItems.getItem(targetSlot).isEmpty()) {
			return;
		}

		int rawItemId = id & JEI_TRANSFER_ITEM_MASK;
		Item item = BuiltInRegistries.ITEM.byId(rawItemId);
		if (item == null) {
			return;
		}

		int recipeCount = ((id >>> JEI_TRANSFER_COUNT_SHIFT) & 0x3F) + 1;
		ItemStack template = new ItemStack(item);
		int requested = jeiMaxTransfer ? template.getMaxStackSize() : recipeCount;
		ItemStack collected = terminal.extractMatchingStack(template, requested);
		int remaining = requested - collected.getCount();

		if (remaining > 0) {
			ItemStack fromPlayer = extractMatchingFromPlayerInventory(template, remaining);
			if (!fromPlayer.isEmpty()) {
				if (collected.isEmpty()) {
					collected = fromPlayer;
				} else if (ItemStack.isSameItemSameComponents(collected, fromPlayer)) {
					collected.grow(fromPlayer.getCount());
				} else {
					ItemHandlerHelper.giveItemToPlayer(playerInventory.player, fromPlayer);
				}
			}
		}

		if (!collected.isEmpty()) {
			craftingItems.setItem(targetSlot, collected);
		}
	}

	private ItemStack extractMatchingFromPlayerInventory(ItemStack template, int amount) {
		ItemStack collected = ItemStack.EMPTY;
		int remaining = amount;
		for (int slotIndex = playerInventoryStart;
				slotIndex < hotbarStart + HOTBAR_SLOTS && remaining > 0; slotIndex++) {
			Slot slot = slots.get(slotIndex);
			ItemStack existing = slot.getItem();
			if (existing.isEmpty() || existing.getItem() != template.getItem()) {
				continue;
			}

			int take = Math.min(remaining, existing.getCount());
			ItemStack removed = slot.remove(take);
			if (removed.isEmpty()) {
				continue;
			}

			if (collected.isEmpty()) {
				collected = removed;
			} else if (ItemStack.isSameItemSameComponents(collected, removed)) {
				collected.grow(removed.getCount());
			} else {
				ItemHandlerHelper.giveItemToPlayer(playerInventory.player, removed);
				continue;
			}
			remaining -= removed.getCount();
		}
		return collected;
	}

	private void craftOneStackToInventory(Player player) {
		if (!craftingTerminal || terminal == null) {
			return;
		}

		ItemStack firstResult = craftingResult.getItem(0);
		if (firstResult.isEmpty()) {
			return;
		}

		ItemStack resultType = firstResult.copyWithCount(1);
		int maximumCrafted = firstResult.getMaxStackSize();
		int crafted = 0;
		ItemStack overflow = ItemStack.EMPTY;
		while (crafted < maximumCrafted) {
			ItemStack nextResult = craftingResult.getItem(0);
			if (nextResult.isEmpty()
					|| !ItemStack.isSameItemSameComponents(resultType, nextResult)
					|| crafted + nextResult.getCount() > maximumCrafted) {
				break;
			}

			CraftingTransfer transfer = transferCraftingResult(player);
			if (transfer.crafted().isEmpty()) {
				break;
			}
			crafted += transfer.crafted().getCount();
			if (!transfer.overflow().isEmpty()) {
				if (overflow.isEmpty()) {
					overflow = transfer.overflow().copy();
				} else {
					overflow.grow(transfer.overflow().getCount());
				}
			}
		}

		if (!overflow.isEmpty()) {
			player.drop(overflow, false);
		}
	}

	private ItemStack quickMoveCraftingResult(Player player) {
		CraftingTransfer transfer = transferCraftingResult(player);
		if (!transfer.overflow().isEmpty()) {
			player.drop(transfer.overflow(), false);
		}
		return transfer.crafted();
	}

	private CraftingTransfer transferCraftingResult(Player player) {
		if (!craftingTerminal || CRAFTING_RESULT_SLOT >= slots.size()) {
			return CraftingTransfer.EMPTY;
		}

		Slot slot = slots.get(CRAFTING_RESULT_SLOT);
		if (!slot.hasItem()) {
			return CraftingTransfer.EMPTY;
		}

		ItemStack crafted = slot.getItem().copy();
		ItemStack[] craftingTemplates = snapshotCraftingTemplates();
		slot.onQuickCraft(ItemStack.EMPTY, crafted);
		slot.set(ItemStack.EMPTY);
		slot.onTake(player, ItemStack.EMPTY);
		refillConsumedCraftingSlots(craftingTemplates);

		ItemStack overflow = crafted.copy();
		moveItemStackTo(overflow, playerInventoryStart, hotbarStart + HOTBAR_SLOTS, true);
		if (!overflow.isEmpty()) {
			overflow = insertIntoMatchingNetworkInventories(overflow);
		}
		return new CraftingTransfer(crafted, overflow);
	}

	private ItemStack[] snapshotCraftingTemplates() {
		ItemStack[] templates = new ItemStack[CRAFTING_INPUT_SLOTS];
		for (int slot = 0; slot < CRAFTING_INPUT_SLOTS; slot++) {
			ItemStack stack = craftingItems.getItem(slot);
			templates[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
		}
		return templates;
	}

	private void refillConsumedCraftingSlots(ItemStack[] templates) {
		if (terminal == null || templates.length != CRAFTING_INPUT_SLOTS) {
			return;
		}

		for (int slot = 0; slot < CRAFTING_INPUT_SLOTS; slot++) {
			ItemStack template = templates[slot];
			if (template.isEmpty() || !craftingItems.getItem(slot).isEmpty()) {
				continue;
			}

			ItemStack refill = terminal.extractMatchingStack(template, 1);
			if (!refill.isEmpty()) {
				craftingItems.setItem(slot, refill);
			}
		}
	}

	private ItemStack insertIntoNetwork(ItemStack stack) {
		if (terminal == null || stack.isEmpty()) {
			return stack;
		}

		return terminal.insertStackIntoNetwork(stack);
	}

	private ItemStack insertIntoMatchingNetworkInventories(ItemStack stack) {
		if (terminal == null || stack.isEmpty()) {
			return stack;
		}

		return terminal.insertStackIntoMatchingInventories(stack);
	}

	private static boolean isAllowedSearchCharacter(char character) {
		return character >= 32 && character != 127;
	}

	private static boolean isSupportedFilter(ItemStack stack) {
		return stack.getItem() instanceof ListFilterItem || stack.getItem() instanceof AttributeFilterItem;
	}

	private SortMode getSortMode() {
		int state = sortModeState.get();
		SortMode[] modes = SortMode.values();
		return state >= 0 && state < modes.length ? modes[state] : SortMode.COUNT;
	}

	private void setSortState(SortMode mode, boolean descending) {
		sortModeState.set(mode.ordinal());
		sortDescendingState.set(descending ? 1 : 0);
	}

	private void cycleSortState() {
		switch (getSortState()) {
			case 0 -> setSortState(SortMode.NAME, true);
			case 1 -> setSortState(SortMode.COUNT, true);
			case 2 -> setSortState(SortMode.COUNT, false);
			default -> setSortState(SortMode.NAME, false);
		}
	}

	private DisplayMode getDisplayMode() {
		int state = displayModeState.get();
		DisplayMode[] modes = DisplayMode.values();
		return state >= 0 && state < modes.length ? modes[state] : DisplayMode.ITEMS;
	}

	private void setDisplayMode(DisplayMode mode) {
		displayModeState.set(mode.ordinal());
	}

	private record OpeningData(BlockPos terminalPos, boolean craftingTerminal) {
	}

	private record CraftingTransfer(ItemStack crafted, ItemStack overflow) {
		private static final CraftingTransfer EMPTY = new CraftingTransfer(ItemStack.EMPTY, ItemStack.EMPTY);
	}

	private enum SortMode {
		NAME,
		COUNT
	}

	private enum DisplayMode {
		ITEMS,
		FLUIDS,
		BOTH;

		private DisplayMode next() {
			DisplayMode[] modes = values();
			return modes[(ordinal() + 1) % modes.length];
		}
	}

	private static class NetworkDisplaySlot extends SlotItemHandler {
		private final TerminalMenu menu;
		private final int row;

		private NetworkDisplaySlot(TerminalMenu menu, ItemStackHandler itemHandler, int index, int row, int xPosition, int yPosition) {
			super(itemHandler, index, xPosition, yPosition);
			this.menu = menu;
			this.row = row;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return true;
		}

		@Override
		public boolean isActive() {
			return row < menu.getVisibleRows();
		}
	}

	private static class TerminalFilterSlot extends SlotItemHandler {
		private TerminalFilterSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
			super(itemHandler, index, xPosition, yPosition);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return isSupportedFilter(stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
