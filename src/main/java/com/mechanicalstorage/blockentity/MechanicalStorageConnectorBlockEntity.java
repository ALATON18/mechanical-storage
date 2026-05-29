package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.DirectionalMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class MechanicalStorageConnectorBlockEntity extends BlockEntity {
	public MechanicalStorageConnectorBlockEntity(BlockPos pos, BlockState blockState) {
		super(MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY.get(), pos, blockState);
	}

	public Component describeTargetInventory() {
		IItemHandler handler = getTargetItemHandler();
		Direction facing = getBlockState().getValue(DirectionalMachineBlock.FACING);
		BlockPos targetPos = worldPosition.relative(facing);

		if (handler == null) {
			return Component.literal("Connector: no item inventory found at " + formatPos(targetPos) + ".");
		}

		int slots = handler.getSlots();
		int occupiedSlots = 0;
		int totalItems = 0;

		for (int slot = 0; slot < slots; slot++) {
			ItemStack stack = handler.getStackInSlot(slot);
			if (!stack.isEmpty()) {
				occupiedSlots++;
				totalItems += stack.getCount();
			}
		}

		return Component.literal("Connector: found item inventory at " + formatPos(targetPos) + " (" + occupiedSlots + "/" + slots + " slots used, " + totalItems + " items).");
	}

	@Nullable
	public IItemHandler getTargetItemHandler() {
		if (level == null) {
			return null;
		}

		Direction facing = getBlockState().getValue(DirectionalMachineBlock.FACING);
		BlockPos targetPos = worldPosition.relative(facing);
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

		return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState, targetBlockEntity, facing.getOpposite());
	}

	private static String formatPos(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
