package com.mechanicalstorage.client;

import com.mechanicalstorage.menu.TerminalMenu;
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
	private static final int SLOT_RAISED = 0xFF929292;
	private static final int TEXT = 0xFF404040;

	private EditBox searchBox;

	public TerminalScreen(TerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 204;
		this.imageHeight = 258;
		this.titleLabelX = 32;
		this.titleLabelY = 20;
		this.inventoryLabelX = 32;
		this.inventoryLabelY = 158;
	}

	@Override
	protected void init() {
		super.init();
		searchBox = new EditBox(this.font, this.leftPos + 84, this.topPos + 16, 108, 16, Component.translatable("container.mechanical_storage.search"));
		searchBox.setMaxLength(TerminalMenu.SEARCH_MAX_LENGTH);
		searchBox.setHint(Component.literal("Search"));
		searchBox.setResponder(this::onSearchChanged);
		searchBox.setFocused(false);
		addRenderableWidget(searchBox);

		addSideButton(0, "AZ", TerminalMenu.SORT_NAME_BUTTON);
		addSideButton(1, "Qt", TerminalMenu.SORT_COUNT_BUTTON);
		addSideButton(2, "^", TerminalMenu.SCROLL_UP_BUTTON);
		addSideButton(3, "v", TerminalMenu.SCROLL_DOWN_BUTTON);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		int gridX = x + 31;
		int networkGridY = y + 47;
		int inventoryGridY = y + 170;
		int hotbarGridY = y + 228;
		int gridWidth = TerminalMenu.GRID_COLUMNS * 18;
		int networkGridHeight = TerminalMenu.GRID_ROWS * 18;
		int inventoryGridHeight = 3 * 18;
		int hotbarGridHeight = 18;

		drawModernPanel(guiGraphics, x + 24, y + 10, x + imageWidth - 4, y + imageHeight - 4);
		drawInsetPanel(guiGraphics, gridX, networkGridY, gridX + gridWidth, networkGridY + networkGridHeight);
		drawInsetPanel(guiGraphics, gridX, inventoryGridY, gridX + gridWidth, inventoryGridY + inventoryGridHeight);
		drawInsetPanel(guiGraphics, gridX, hotbarGridY, gridX + gridWidth, hotbarGridY + hotbarGridHeight);

		drawSlotBackgrounds(guiGraphics, gridX, networkGridY, TerminalMenu.GRID_COLUMNS, TerminalMenu.GRID_ROWS);
		drawSlotBackgrounds(guiGraphics, gridX, inventoryGridY, 9, 3);
		drawSlotBackgrounds(guiGraphics, gridX, hotbarGridY, 9, 1);
	}

	@Override
	protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
		int menuSlotIndex = this.menu.slots.indexOf(slot);

		if (slot.hasItem()) {
			drawSlot(guiGraphics, slot.x - 1, slot.y - 1, true);
		}

		if (menuSlotIndex >= 0 && menuSlotIndex < TerminalMenu.NETWORK_SLOTS) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				int count = this.menu.getNetworkSlotCount(menuSlotIndex);
				ItemStack renderStack = stack.copy();
				renderStack.setCount(1);
				guiGraphics.renderItem(renderStack, slot.x, slot.y);
				guiGraphics.renderItemDecorations(this.font, renderStack, slot.x, slot.y, null);
				if (count > 1) {
					drawSmallCount(guiGraphics, formatCount(count), slot.x, slot.y);
				}
				return;
			}
		}

		super.renderSlot(guiGraphics, slot);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (this.menu.getNetworkStatus() != com.mechanicalstorage.blockentity.TerminalBlockEntity.NetworkStatus.ONLINE) {
			int left = this.leftPos + 31;
			int top = this.topPos + 47;
			int right = left + TerminalMenu.GRID_COLUMNS * 18;
			int bottom = top + TerminalMenu.GRID_ROWS * 18;
			guiGraphics.fill(left, top, right, bottom, 0xB0202020);
			Component status = this.menu.getNetworkStatus().message();
			guiGraphics.drawCenteredString(this.font, status, (left + right) / 2, (top + bottom - this.font.lineHeight) / 2, 0xFF6B6B);
		}
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, Component.literal("Terminal"), this.titleLabelX, this.titleLabelY, TEXT, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		if (this.menu.getTotalMatchingItems() > 0) {
			int first = this.menu.getScrollRow() * TerminalMenu.GRID_COLUMNS + 1;
			int last = Math.min(this.menu.getTotalMatchingItems(), first + TerminalMenu.NETWORK_SLOTS - 1);
			String range = first + "-" + last + "/" + this.menu.getTotalMatchingItems();
			guiGraphics.drawString(this.font, range, 192 - this.font.width(range), 37, TEXT, false);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (searchBox != null && !isMouseOverSearchBox(mouseX, mouseY)) {
			searchBox.setFocused(false);
		}

		if (button == 0 && hasControlDown() && this.hoveredSlot != null) {
			int slot = this.menu.slots.indexOf(this.hoveredSlot);
			if (slot >= 0 && slot < TerminalMenu.NETWORK_SLOTS) {
				sendMenuButton(TerminalMenu.SINGLE_EXTRACT_SLOT_BASE_BUTTON + slot);
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isMouseOverNetworkGrid(mouseX, mouseY)) {
			if (hasControlDown()) {
				if (scrollY > 0 && this.hoveredSlot != null) {
					int slot = this.menu.slots.indexOf(this.hoveredSlot);
					if (slot >= 0 && slot < TerminalMenu.NETWORK_SLOTS) {
						sendMenuButton(TerminalMenu.SINGLE_EXTRACT_SLOT_BASE_BUTTON + slot);
					}
				} else if (scrollY < 0) {
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
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox != null && searchBox.isFocused()) {
			if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
				searchBox.setFocused(false);
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
				.bounds(this.leftPos, this.topPos + 47 + row * 23, 22, 18)
				.build());
	}

	private void onSearchChanged(String searchText) {
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
		return searchBox != null && mouseX >= searchBox.getX() && mouseX < searchBox.getX() + searchBox.getWidth() && mouseY >= searchBox.getY() && mouseY < searchBox.getY() + searchBox.getHeight();
	}

	private boolean isMouseOverNetworkGrid(double mouseX, double mouseY) {
		int left = this.leftPos + 31;
		int top = this.topPos + 47;
		return mouseX >= left && mouseX < left + TerminalMenu.GRID_COLUMNS * 18
				&& mouseY >= top && mouseY < top + TerminalMenu.GRID_ROWS * 18;
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
				int x = startX + column * 18;
				int y = startY + row * 18;
				drawSlot(guiGraphics, x, y, false);
			}
		}
	}

	private void drawSlot(GuiGraphics guiGraphics, int x, int y, boolean occupied) {
		if (occupied) {
			drawRecessedSlot(guiGraphics, x, y);
		} else {
			drawRaisedSlot(guiGraphics, x, y);
		}
	}

	private void drawRaisedSlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
		guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_RAISED);
		guiGraphics.fill(x, y, x + 18, y + 1, SLOT_LIGHT);
		guiGraphics.fill(x, y, x + 1, y + 18, SLOT_LIGHT);
		guiGraphics.fill(x, y + 17, x + 18, y + 18, SLOT_DARK);
		guiGraphics.fill(x + 17, y, x + 18, y + 18, SLOT_DARK);
	}

	private void drawRecessedSlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x, y, x + 18, y + 18, SLOT_LIGHT);
		guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
		guiGraphics.fill(x, y, x + 18, y + 1, SLOT_DARK);
		guiGraphics.fill(x, y, x + 1, y + 18, SLOT_DARK);
		guiGraphics.fill(x, y + 17, x + 18, y + 18, SLOT_LIGHT);
		guiGraphics.fill(x + 17, y, x + 18, y + 18, SLOT_LIGHT);
	}
}
