package com.mechanicalstorage.client;

import com.mechanicalstorage.menu.TerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
	private EditBox searchBox;

	public TerminalScreen(TerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 262;
		this.inventoryLabelY = 168;
	}

	@Override
	protected void init() {
		super.init();
		searchBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 17, 160, 16, Component.translatable("container.mechanical_storage.search"));
		searchBox.setMaxLength(TerminalMenu.SEARCH_MAX_LENGTH);
		searchBox.setHint(Component.literal("Search items, @mod, #tag"));
		searchBox.setResponder(this::onSearchChanged);
		searchBox.setFocused(false);
		addRenderableWidget(searchBox);

		addRenderableWidget(Button.builder(Component.literal("A-Z"), button -> sendMenuButton(TerminalMenu.SORT_NAME_BUTTON))
				.bounds(this.leftPos + 8, this.topPos + 37, 38, 18)
				.build());

		addRenderableWidget(Button.builder(Component.literal("Qty"), button -> sendMenuButton(TerminalMenu.SORT_COUNT_BUTTON))
				.bounds(this.leftPos + 50, this.topPos + 37, 38, 18)
				.build());
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;

		guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2118);
		guiGraphics.fill(x + 4, y + 14, x + imageWidth - 4, y + 34, 0xFF3B3024);
		guiGraphics.fill(x + 4, y + 34, x + imageWidth - 4, y + 56, 0xFF3B3024);
		guiGraphics.fill(x + 4, y + 56, x + imageWidth - 4, y + 166, 0xFF3B3024);
		guiGraphics.fill(x + 4, y + 176, x + imageWidth - 4, y + imageHeight - 4, 0xFF3B3024);

		drawSlotBackgrounds(guiGraphics, x + 7, y + 57, TerminalMenu.GRID_COLUMNS, TerminalMenu.GRID_ROWS);
		drawSlotBackgrounds(guiGraphics, x + 7, y + 179, 9, 3);
		drawSlotBackgrounds(guiGraphics, x + 7, y + 237, 9, 1);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE8D9B5, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xE8D9B5, false);
		guiGraphics.drawString(this.font, Component.literal("LMB: stack to cursor  RMB: half  Shift: move stack"), 8, 158, 0xC8B89A, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (searchBox != null && !isMouseOverSearchBox(mouseX, mouseY)) {
			searchBox.setFocused(false);
		}

		return super.mouseClicked(mouseX, mouseY, button);
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

	private void drawSlotBackgrounds(GuiGraphics guiGraphics, int startX, int startY, int columns, int rows) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int x = startX + column * 18;
				int y = startY + row * 18;
				guiGraphics.fill(x, y, x + 18, y + 18, 0xFF1A1510);
				guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF5A4734);
				guiGraphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF2F261D);
			}
		}
	}
}
