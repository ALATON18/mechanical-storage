package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public class TerminalBlockEntity extends BlockEntity {
	private static final int SCAN_RADIUS = 32;
	private static final int MAX_CONNECTORS = 64;

	public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
		super(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), pos, blockState);
	}

	public Component describeNearbyConnectorNetwork() {
		if (level == null) {
			return Component.literal("Terminal: level is not available.");
		}

		int connectorsFound = 0;
		int inventoriesFound = 0;
		int totalSlots = 0;
		int occupiedSlots = 0;
		int totalItems = 0;

		BlockPos min = worldPosition.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
		BlockPos max = worldPosition.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);

		for (BlockPos scanPos : BlockPos.betweenClosed(min, max)) {
			if (connectorsFound >= MAX_CONNECTORS) {
				break;
			}

			BlockEntity blockEntity = level.getBlockEntity(scanPos);
			if (!(blockEntity instanceof MechanicalStorageConnectorBlockEntity connector)) {
				continue;
			}

			connectorsFound++;
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}

			inventoriesFound++;
			int slots = handler.getSlots();
			totalSlots += slots;

			for (int slot = 0; slot < slots; slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (!stack.isEmpty()) {
					occupiedSlots++;
					totalItems += stack.getCount();
				}
			}
		}

		return Component.literal("Terminal: found " + connectorsFound + " connector(s), " + inventoriesFound + " inventory/inventories, " + occupiedSlots + "/" + totalSlots + " slots used, " + totalItems + " items total.");
	}
}
