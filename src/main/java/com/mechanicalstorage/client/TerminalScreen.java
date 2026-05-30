package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.menu.TerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
	public TerminalScreen(TerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 222;
		this.inventoryLabelY = 128;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;

		guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2118);
		guiGraphics.fill(x + 4, y + 14, x + imageWidth - 4, y + 126, 0xFF3B3024);
		guiGraphics.fill(x + 4, y + 136, x + imageWidth - 4, y + imageHeight - 4, 0xFF3B3024);

		drawSlotBackgrounds(guiGraphics, x + 7, y + 17, TerminalMenu.GRID_COLUMNS, TerminalMenu.GRID_ROWS);
		drawSlotBackgrounds(guiGraphics, x + 7, y + 139, 9, 3);
		drawSlotBackgrounds(guiGraphics, x + 7, y + 197, 9, 1);
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
		guiGraphics.drawString(this.font, Component.literal("LMB: stack  RMB: half  Shift player items to import"), 8, 118, 0xC8B89A, false);
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
