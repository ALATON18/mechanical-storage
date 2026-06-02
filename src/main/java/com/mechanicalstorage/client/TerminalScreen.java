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
	private static final int BG_DARK = 0xFF8B8B8B;
	private static final int SLOT_OUTLINE = 0xFF373737;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int TEXT = 0xFF404040;

	private EditBox searchBox;

	public TerminalScreen(TerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 200;
		this.imageHeight = 258;
		this.titleLabelX = 32;
		this.titleLabelY = 20;
		this.inventoryLabelX = 32;
		this.inventoryLabelY = 164;
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
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		int gridX = x + 31;
		int networkGridY = y + 53;
		int inventoryGridY = y + 175;
		int hotbarGridY = y + 233;
		int gridWidth = TerminalMenu.GRID_COLUMNS * 18;
		int networkGridHeight = TerminalMenu.GRID_ROWS * 18;
		int inventoryGridHeight = 3 * 18;
		int hotbarGridHeight = 18;

		guiGraphics.fill(x + 24, y + 10, x + imageWidth - 4, y + imageHeight - 4, BG);
		guiGraphics.fill(gridX - 2, networkGridY - 2, gridX + gridWidth + 2, networkGridY + networkGridHeight + 2, BG_DARK);
		guiGraphics.fill(gridX - 2, inventoryGridY - 2, gridX + gridWidth + 2, hotbarGridY + hotbarGridHeight + 2, BG_DARK);

		drawSlotBackgrounds(guiGraphics, gridX, networkGridY, TerminalMenu.GRID_COLUMNS, TerminalMenu.GRID_ROWS);
		drawSlotBackgrounds(guiGraphics, gridX, inventoryGridY, 9, 3);
		drawSlotBackgrounds(guiGraphics, gridX, hotbarGridY, 9, 1);
	}

	@Override
	protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
		int menuSlotIndex = this.menu.slots.indexOf(slot);
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
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, Component.literal("Terminal"), this.titleLabelX, this.titleLabelY, TEXT, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
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

	private void addSideButton(int row, String label, int buttonId) {
		addRenderableWidget(Button.builder(Component.literal(label), button -> sendMenuButton(buttonId))
				.bounds(this.leftPos, this.topPos + 54 + row * 23, 22, 18)
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

	private void drawSmallCount(GuiGraphics guiGraphics, String countText, int slotX, int slotY) {
		float scale = countText.length() >= 4 ? 0.45F : 0.50F;
		int textWidth = this.font.width(countText);
		int targetX = Math.round(slotX + 17 - textWidth * scale);
		int targetY = slotY + 12;
		int scaledX = Math.round(targetX / scale);
		int scaledY = Math.round(targetY / scale);

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
		guiGraphics.pose().scale(scale, scale, 1.0F);
		guiGraphics.drawString(this.font, countText, scaledX, scaledY, 0xFFFFFF, true);
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

	private void drawSlotBackgrounds(GuiGraphics guiGraphics, int startX, int startY, int columns, int rows) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int x = startX + column * 18;
				int y = startY + row * 18;
				guiGraphics.fill(x, y, x + 18, y + 18, SLOT_OUTLINE);
				guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
			}
		}
	}
}
