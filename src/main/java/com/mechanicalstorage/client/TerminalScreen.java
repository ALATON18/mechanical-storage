package com.mechanicalstorage.client;

import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.menu.TerminalMenu;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.filter.ListFilterItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
	private static final int BG = 0xFFC6C6C6;
	private static final int BG_LIGHT = 0xFFE8E8E8;
	private static final int BG_BORDER = 0xFF5F5F5F;
	private static final int BG_SHADOW = 0xFF242424;
	private static final int BG_DARK = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFDCDCDC;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int TEXT = 0xFF404040;
	private static final int PANEL_WIDTH = 204;
	private static final int BASE_IMAGE_HEIGHT = 132;
	private static final int SCROLLBAR_X = 194;
	private static final int SCROLLBAR_WIDTH = 5;
	private static final int MIN_SCROLLBAR_THUMB_HEIGHT = 12;

	private static SizeMode preferredSize = SizeMode.MEDIUM;

	private EditBox searchBox;
	private String searchQuery = "";
	private boolean draggingScrollbar;
	private boolean suppressReleaseClick;
	private int lastDraggedScrollRow = -1;

	public TerminalScreen(TerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 236;
		this.imageHeight = imageHeight(TerminalMenu.DEFAULT_GRID_ROWS);
		this.titleLabelX = 32;
		this.titleLabelY = 17;
		this.inventoryLabelX = 32;
	}

	@Override
	protected void init() {
		int rows = preferredSize.rowsFor(this.height);
		this.imageHeight = imageHeight(rows);
		this.inventoryLabelY = visualPlayerInventoryY(rows) - 12;
		super.init();

		menu.setVisibleRowsClient(rows);
		sendMenuButton(TerminalMenu.GRID_ROWS_BUTTON_BASE + rows);

		searchBox = new EditBox(this.font, this.leftPos + 84, this.topPos + 14, 108, 14, Component.translatable("container.mechanical_storage.search"));
		searchBox.setMaxLength(TerminalMenu.SEARCH_MAX_LENGTH);
		searchBox.setHint(Component.translatable("container.mechanical_storage.search"));
		searchBox.setValue(searchQuery);
		searchBox.setResponder(this::onSearchChanged);
		searchBox.setFocused(false);
		addRenderableWidget(searchBox);

		addSideButton(0, "AZ", TerminalMenu.SORT_NAME_BUTTON);
		addSideButton(1, "Qt", TerminalMenu.SORT_COUNT_BUTTON);
		addRenderableWidget(Button.builder(Component.literal(preferredSize.label), button -> cycleSize())
				.bounds(this.leftPos - 22, this.topPos + 93, 22, 18)
				.build());
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		int rows = this.menu.getVisibleRows();
		int gridX = x + 31;
		int networkGridY = y + TerminalMenu.NETWORK_SLOT_Y - 1;
		int inventoryGridY = y + visualPlayerInventoryY(rows) - 1;
		int hotbarGridY = y + visualHotbarY(rows) - 1;
		int gridWidth = TerminalMenu.GRID_COLUMNS * 18;
		int networkGridHeight = rows * 18;

		drawModernPanel(guiGraphics, x + 24, y + 10, x + PANEL_WIDTH - 4, y + imageHeight - 4);
		drawModernPanel(guiGraphics, x + PANEL_WIDTH, y + 10, x + imageWidth, y + 58);
		drawInsetPanel(guiGraphics, gridX, networkGridY, gridX + gridWidth, networkGridY + networkGridHeight);
		drawInsetPanel(guiGraphics, gridX, inventoryGridY, gridX + gridWidth, inventoryGridY + 3 * 18);
		drawInsetPanel(guiGraphics, gridX, hotbarGridY, gridX + gridWidth, hotbarGridY + 18);

		drawSlotBackgrounds(guiGraphics, gridX, networkGridY, TerminalMenu.GRID_COLUMNS, rows);
		drawSlotBackgrounds(guiGraphics, gridX, inventoryGridY, 9, 3);
		drawSlotBackgrounds(guiGraphics, gridX, hotbarGridY, 9, 1);
		drawRecessedSlot(guiGraphics, x + TerminalMenu.FILTER_SLOT_X - 1, y + TerminalMenu.LIST_FILTER_SLOT_Y - 1);
		drawRecessedSlot(guiGraphics, x + TerminalMenu.FILTER_SLOT_X - 1, y + TerminalMenu.ATTRIBUTE_FILTER_SLOT_Y - 1);
		drawInstalledFilterTab(guiGraphics, TerminalBlockEntity.LIST_FILTER_SLOT, x + 6, y + visualListFilterSlotY(rows) - 1);
		drawInstalledFilterTab(guiGraphics, TerminalBlockEntity.ATTRIBUTE_FILTER_SLOT, x + 6, y + visualAttributeFilterSlotY(rows) - 1);
		drawScrollbar(guiGraphics);
	}

	@Override
	protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
		int menuSlotIndex = this.menu.slots.indexOf(slot);
		if (menuSlotIndex >= 0 && menuSlotIndex < TerminalMenu.NETWORK_SLOTS) {
			int row = menuSlotIndex / TerminalMenu.GRID_COLUMNS;
			if (row >= menu.getVisibleRows()) {
				return;
			}

			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				int count = this.menu.getNetworkSlotCount(menuSlotIndex);
				ItemStack renderStack = stack.copyWithCount(1);
				guiGraphics.renderItem(renderStack, slot.x, slot.y);
				guiGraphics.renderItemDecorations(this.font, renderStack, slot.x, slot.y, null);
				if (count > 1) {
					drawSmallCount(guiGraphics, formatCount(count), slot.x, slot.y);
				}
			}
			return;
		}

		if (menuSlotIndex >= TerminalMenu.FILTER_SLOT_START + TerminalMenu.FILTER_SLOTS) {
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(0, -layoutDelta(), 0);
			super.renderSlot(guiGraphics, slot);
			guiGraphics.pose().popPose();
			return;
		}

		super.renderSlot(guiGraphics, slot);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (this.menu.getNetworkStatus() != com.mechanicalstorage.blockentity.TerminalBlockEntity.NetworkStatus.ONLINE) {
			int left = this.leftPos + 31;
			int top = this.topPos + TerminalMenu.NETWORK_SLOT_Y - 1;
			int right = left + TerminalMenu.GRID_COLUMNS * 18;
			int bottom = top + menu.getVisibleRows() * 18;
			guiGraphics.fill(left, top, right, bottom, 0xB0202020);
			Component status = this.menu.getNetworkStatus().message();
			guiGraphics.drawCenteredString(this.font, status, (left + right) / 2, (top + bottom - this.font.lineHeight) / 2, 0xFF6B6B);
		}

		this.renderTooltip(guiGraphics, mouseX, mouseY);
		renderFilterTabTooltip(guiGraphics, mouseX, mouseY);
		if (this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
			int slotIndex = this.menu.slots.indexOf(this.hoveredSlot);
			if (slotIndex >= TerminalMenu.FILTER_SLOT_START
					&& slotIndex < TerminalMenu.FILTER_SLOT_START + TerminalMenu.FILTER_SLOTS) {
				guiGraphics.renderTooltip(this.font, Component.literal("Create filter slot"), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		if (this.menu.getTotalMatchingItems() > 0) {
			int first = this.menu.getScrollRow() * TerminalMenu.GRID_COLUMNS + 1;
			int last = Math.min(this.menu.getTotalMatchingItems(), first + this.menu.getVisibleRows() * TerminalMenu.GRID_COLUMNS - 1);
			String range = first + "-" + last + "/" + this.menu.getTotalMatchingItems();
			guiGraphics.drawString(this.font, range, 192 - this.font.width(range), 28, TEXT, false);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (searchBox != null && !isMouseOverSearchBox(mouseX, mouseY)) {
			searchBox.setFocused(false);
		}

		if (button == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
			draggingScrollbar = true;
			lastDraggedScrollRow = -1;
			setScrollbarFromMouse(mouseY);
			return true;
		}

		if (button == 0 && clickFilterTab(mouseX, mouseY)) {
			return true;
		}

		if (button == 0 && hasControlDown() && this.hoveredSlot != null) {
			int slot = this.menu.slots.indexOf(this.hoveredSlot);
			if (slot >= 0 && slot < TerminalMenu.NETWORK_SLOTS) {
				suppressReleaseClick = true;
				sendMenuButton(TerminalMenu.SINGLE_EXTRACT_SLOT_BASE_BUTTON + slot);
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			lastDraggedScrollRow = -1;
			return true;
		}

		if (button == 0 && suppressReleaseClick) {
			suppressReleaseClick = false;
			return true;
		}

		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 0 && draggingScrollbar) {
			setScrollbarFromMouse(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isMouseOverNetworkGrid(mouseX, mouseY)) {
			if (hasControlDown()) {
				if (scrollY < 0 && this.hoveredSlot != null) {
					int slot = this.menu.slots.indexOf(this.hoveredSlot);
					if (slot >= 0 && slot < TerminalMenu.NETWORK_SLOTS) {
						sendMenuButton(TerminalMenu.SINGLE_EXTRACT_SLOT_BASE_BUTTON + slot);
					}
				} else if (scrollY > 0) {
					sendMenuButton(TerminalMenu.SINGLE_DEPOSIT_BUTTON);
				}
			} else if (scrollY > 0) {
				sendMenuButton(TerminalMenu.SCROLL_UP_BUTTON);
			} else if (scrollY < 0) {
				sendMenuButton(TerminalMenu.SCROLL_DOWN_BUTTON);
			}
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
		int adjustedY = y >= TerminalMenu.PLAYER_INVENTORY_Y ? y - layoutDelta() : y;
		return super.isHovering(x, adjustedY, width, height, mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox != null && searchBox.isFocused()) {
			if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
				return true;
			}

			if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) {
			return true;
		}

		return super.charTyped(codePoint, modifiers);
	}

	private void addSideButton(int row, String label, int buttonId) {
		addRenderableWidget(Button.builder(Component.literal(label), button -> sendMenuButton(buttonId))
				.bounds(this.leftPos - 22, this.topPos + 47 + row * 23, 22, 18)
				.build());
	}

	private void cycleSize() {
		preferredSize = preferredSize.next();
		if (this.minecraft != null) {
			this.resize(this.minecraft, this.width, this.height);
		}
	}

	private void onSearchChanged(String searchText) {
		searchQuery = searchText;
		if (this.minecraft == null || this.minecraft.gameMode == null) {
			return;
		}

		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TerminalMenu.SEARCH_CLEAR_BUTTON);
		for (int index = 0; index < searchText.length(); index++) {
			char character = searchText.charAt(index);
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TerminalMenu.SEARCH_CHAR_BASE_BUTTON + character);
		}
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TerminalMenu.SEARCH_APPLY_BUTTON);
	}

	private void sendMenuButton(int buttonId) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
		}
	}

	private boolean isMouseOverSearchBox(double mouseX, double mouseY) {
		return searchBox != null && mouseX >= searchBox.getX() && mouseX < searchBox.getX() + searchBox.getWidth()
				&& mouseY >= searchBox.getY() && mouseY < searchBox.getY() + searchBox.getHeight();
	}

	private boolean isMouseOverNetworkGrid(double mouseX, double mouseY) {
		int left = this.leftPos + 31;
		int top = this.topPos + TerminalMenu.NETWORK_SLOT_Y - 1;
		return mouseX >= left && mouseX < left + TerminalMenu.GRID_COLUMNS * 18
				&& mouseY >= top && mouseY < top + menu.getVisibleRows() * 18;
	}

	private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
		int left = this.leftPos + SCROLLBAR_X;
		int top = this.topPos + TerminalMenu.NETWORK_SLOT_Y - 1;
		return mouseX >= left && mouseX < left + SCROLLBAR_WIDTH
				&& mouseY >= top && mouseY < top + menu.getVisibleRows() * 18;
	}

	private void setScrollbarFromMouse(double mouseY) {
		int maximumRow = menu.getMaximumScrollRow();
		if (maximumRow <= 0) {
			return;
		}

		int trackTop = this.topPos + TerminalMenu.NETWORK_SLOT_Y - 1;
		int trackHeight = menu.getVisibleRows() * 18;
		int thumbHeight = scrollbarThumbHeight(trackHeight);
		int travel = Math.max(1, trackHeight - thumbHeight);
		double fraction = (mouseY - trackTop - thumbHeight / 2.0) / travel;
		int row = (int) Math.round(Math.max(0, Math.min(1, fraction)) * maximumRow);
		if (row == lastDraggedScrollRow) {
			return;
		}

		lastDraggedScrollRow = row;
		menu.setScrollRowClient(row);
		sendMenuButton(TerminalMenu.SCROLL_TO_ROW_BASE_BUTTON + row);
	}

	private void drawScrollbar(GuiGraphics guiGraphics) {
		int left = this.leftPos + SCROLLBAR_X;
		int top = this.topPos + TerminalMenu.NETWORK_SLOT_Y - 1;
		int trackHeight = menu.getVisibleRows() * 18;
		guiGraphics.fill(left, top, left + SCROLLBAR_WIDTH, top + trackHeight, SLOT_DARK);
		guiGraphics.fill(left + 1, top + 1, left + SCROLLBAR_WIDTH - 1, top + trackHeight - 1, BG_DARK);

		int thumbHeight = scrollbarThumbHeight(trackHeight);
		int maximumRow = menu.getMaximumScrollRow();
		int travel = trackHeight - thumbHeight;
		int thumbOffset = maximumRow <= 0 ? 0 : Math.round(travel * (menu.getScrollRow() / (float) maximumRow));
		int thumbTop = top + thumbOffset;
		int thumbColour = maximumRow <= 0 ? 0xFF777777 : BG_LIGHT;
		guiGraphics.fill(left + 1, thumbTop, left + SCROLLBAR_WIDTH - 1, thumbTop + thumbHeight, thumbColour);
		guiGraphics.fill(left + SCROLLBAR_WIDTH - 2, thumbTop + 1, left + SCROLLBAR_WIDTH - 1, thumbTop + thumbHeight, BG_BORDER);
	}

	private int scrollbarThumbHeight(int trackHeight) {
		int totalRows = Math.max(menu.getVisibleRows(), (menu.getTotalMatchingItems() + TerminalMenu.GRID_COLUMNS - 1) / TerminalMenu.GRID_COLUMNS);
		return Math.min(trackHeight, Math.max(MIN_SCROLLBAR_THUMB_HEIGHT,
				Math.round(trackHeight * (menu.getVisibleRows() / (float) totalRows))));
	}

	private void drawSmallCount(GuiGraphics guiGraphics, String countText, int slotX, int slotY) {
		int textWidth = this.font.width(countText);
		float scale = Math.min(0.72F, 15.0F / Math.max(1, textWidth));
		float targetX = slotX + 16.0F - textWidth * scale;
		float targetY = slotY + 17.0F - this.font.lineHeight * scale;

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(targetX, targetY, 200.0F);
		guiGraphics.pose().scale(scale, scale, 1.0F);
		guiGraphics.drawString(this.font, countText, 0, 0, 0xFFFFFF, true);
		guiGraphics.pose().popPose();
	}

	private static String formatCount(int count) {
		if (count >= 1_000_000) {
			return count / 1_000_000 + "M";
		}
		if (count >= 1_000) {
			return count / 1_000 + "K";
		}
		return Integer.toString(count);
	}

	private void drawModernPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
		guiGraphics.fill(left + 3, top + 3, right + 2, bottom + 2, BG_SHADOW);
		guiGraphics.fill(left + 1, top, right - 1, top + 1, BG_BORDER);
		guiGraphics.fill(left, top + 1, right, bottom - 1, BG_BORDER);
		guiGraphics.fill(left + 1, bottom - 1, right - 1, bottom, BG_BORDER);
		guiGraphics.fill(left + 2, top + 1, right - 2, bottom - 1, BG);
		guiGraphics.fill(left + 1, top + 2, right - 1, bottom - 2, BG);
		guiGraphics.fill(left + 2, top + 1, right - 2, top + 2, BG_LIGHT);
		guiGraphics.fill(left + 1, top + 2, left + 2, bottom - 2, BG_LIGHT);
	}

	private void drawInsetPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
		guiGraphics.fill(left, top, right, bottom, SLOT_DARK);
		guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, BG_DARK);
	}

	private void drawSlotBackgrounds(GuiGraphics guiGraphics, int startX, int startY, int columns, int rows) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				drawRecessedSlot(guiGraphics, startX + column * 18, startY + row * 18);
			}
		}
	}

	private void drawInstalledFilterTab(GuiGraphics guiGraphics, int filterSlot, int x, int y) {
		ItemStack filter = menu.getTerminalFilter(filterSlot);
		if (filter.isEmpty()) {
			return;
		}

		boolean active = menu.isFilterActive(filterSlot);
		guiGraphics.fill(x - 4, y - 1, x + 20, y + 19, BG_BORDER);
		guiGraphics.fill(x - 3, y, x + 20, y + 18, active ? 0xFFB6D7A8 : BG);
		drawRecessedSlot(guiGraphics, x, y);
		if (active) {
			guiGraphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF67A34D);
		}

		ItemStack icon = filterTabIcon(filterSlot, filter);
		guiGraphics.renderItem(icon, x + 1, y + 1);
	}

	private ItemStack filterTabIcon(int filterSlot, ItemStack filter) {
		if (filter.getItem() instanceof ListFilterItem) {
			FilterItemStack wrapped = FilterItemStack.of(filter.copy());
			if (wrapped instanceof FilterItemStack.ListFilterItemStack listFilter && !listFilter.containedItems.isEmpty()) {
				ItemStack firstItem = listFilter.containedItems.getFirst().item();
				if (!firstItem.isEmpty()) {
					return firstItem.copyWithCount(1);
				}
			}
		}
		return filter.copyWithCount(1);
	}

	private boolean clickFilterTab(double mouseX, double mouseY) {
		int rows = menu.getVisibleRows();
		if (isMouseOverFilterTab(mouseX, mouseY, visualListFilterSlotY(rows))
				&& !menu.getTerminalFilter(TerminalBlockEntity.LIST_FILTER_SLOT).isEmpty()) {
			sendMenuButton(TerminalMenu.TOGGLE_LIST_FILTER_BUTTON);
			return true;
		}
		if (isMouseOverFilterTab(mouseX, mouseY, visualAttributeFilterSlotY(rows))
				&& !menu.getTerminalFilter(TerminalBlockEntity.ATTRIBUTE_FILTER_SLOT).isEmpty()) {
			sendMenuButton(TerminalMenu.TOGGLE_ATTRIBUTE_FILTER_BUTTON);
			return true;
		}
		return false;
	}

	private boolean isMouseOverFilterTab(double mouseX, double mouseY, int slotY) {
		int left = this.leftPos + 2;
		int top = this.topPos + slotY - 2;
		return mouseX >= left && mouseX < left + 24 && mouseY >= top && mouseY < top + 20;
	}

	private void renderFilterTabTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int rows = menu.getVisibleRows();
		if (isMouseOverFilterTab(mouseX, mouseY, visualListFilterSlotY(rows))
				&& !menu.getTerminalFilter(TerminalBlockEntity.LIST_FILTER_SLOT).isEmpty()) {
			guiGraphics.renderTooltip(this.font, filterToggleTooltip(TerminalBlockEntity.LIST_FILTER_SLOT), mouseX, mouseY);
		} else if (isMouseOverFilterTab(mouseX, mouseY, visualAttributeFilterSlotY(rows))
				&& !menu.getTerminalFilter(TerminalBlockEntity.ATTRIBUTE_FILTER_SLOT).isEmpty()) {
			guiGraphics.renderTooltip(this.font, filterToggleTooltip(TerminalBlockEntity.ATTRIBUTE_FILTER_SLOT), mouseX, mouseY);
		}
	}

	private Component filterToggleTooltip(int slot) {
		ItemStack filter = menu.getTerminalFilter(slot);
		String type = filter.getItem() instanceof ListFilterItem ? "mod" : "attribute";
		return Component.literal((menu.isFilterActive(slot) ? "Disable " : "Enable ") + type + " filter");
	}

	private void drawRecessedSlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x, y, x + 18, y + 18, SLOT_LIGHT);
		guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
		guiGraphics.fill(x, y, x + 18, y + 1, SLOT_DARK);
		guiGraphics.fill(x, y, x + 1, y + 18, SLOT_DARK);
		guiGraphics.fill(x, y + 17, x + 18, y + 18, SLOT_LIGHT);
		guiGraphics.fill(x + 17, y, x + 18, y + 18, SLOT_LIGHT);
	}

	private int layoutDelta() {
		return (TerminalMenu.MAX_GRID_ROWS - menu.getVisibleRows()) * 18;
	}

	private static int imageHeight(int rows) {
		return BASE_IMAGE_HEIGHT + rows * 18;
	}

	private static int visualPlayerInventoryY(int rows) {
		return TerminalMenu.NETWORK_SLOT_Y + rows * 18 + 11;
	}

	private static int visualHotbarY(int rows) {
		return visualPlayerInventoryY(rows) + 58;
	}

	private static int visualListFilterSlotY(int rows) {
		return TerminalMenu.NETWORK_SLOT_Y + rows * 18 - 34;
	}

	private static int visualAttributeFilterSlotY(int rows) {
		return visualListFilterSlotY(rows) + 18;
	}

	private enum SizeMode {
		SMALL("S"),
		MEDIUM("M"),
		LARGE("L"),
		STRETCH("Fit");

		private final String label;

		SizeMode(String label) {
			this.label = label;
		}

		private int rowsFor(int screenHeight) {
			return switch (this) {
				case SMALL -> 4;
				case MEDIUM -> TerminalMenu.DEFAULT_GRID_ROWS + 1;
				case LARGE -> 10;
				case STRETCH -> Math.max(3, Math.min(TerminalMenu.MAX_GRID_ROWS, (screenHeight - BASE_IMAGE_HEIGHT - 20) / 18));
			};
		}

		private SizeMode next() {
			SizeMode[] modes = values();
			return modes[(ordinal() + 1) % modes.length];
		}
	}
}
