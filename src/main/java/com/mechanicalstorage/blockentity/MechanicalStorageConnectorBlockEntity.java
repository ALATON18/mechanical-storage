package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class MechanicalStorageConnectorBlockEntity extends KineticBlockEntity {
	public MechanicalStorageConnectorBlockEntity(BlockEntityType<? extends MechanicalStorageConnectorBlockEntity> type, BlockPos pos, BlockState blockState) {
		super(type, pos, blockState);
	}

	public MechanicalStorageConnectorBlockEntity(BlockPos pos, BlockState blockState) {
		this(MechanicalStorage.MECHANICAL_STORAGE_CONNECTOR_BLOCK_ENTITY.get(), pos, blockState);
	}

	public boolean isOnline() {
		return getSpeed() != 0 && hasNetwork();
	}

	@Override
	public void tick() {
		super.tick();
		StorageNetworkRegistry.register(this);
	}

	@Override
	public void remove() {
		StorageNetworkRegistry.unregister(this);
		super.remove();
	}

	@Override
	public void onChunkUnloaded() {
		StorageNetworkRegistry.unregister(this);
		super.onChunkUnloaded();
	}

	public boolean isOnSameNetwork(TerminalBlockEntity terminal) {
		return isOnline() && terminal.isOnline() && network != null && network.equals(terminal.network);
	}

	public Component describeTargetInventory() {
		if (!isOnline()) {
			return hasKineticConnection()
					? Component.translatable("status.mechanical_storage.overstressed")
					: Component.translatable("status.mechanical_storage.disconnected");
		}

		IItemHandler handler = getTargetItemHandler();
		BlockPos targetPos = getTargetPos();

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

		return Component.literal("Connector: online at " + Math.abs(getSpeed()) + " RPM, inventory at " + formatPos(targetPos) + " (" + occupiedSlots + "/" + slots + " slots used, " + totalItems + " items).");
	}

	public BlockPos getTargetPos() {
		Direction facing = getBlockState().getValue(DirectionalMachineBlock.FACING);
		return worldPosition.relative(facing);
	}

	@Nullable
	public IItemHandler getTargetItemHandler() {
		if (level == null || !isOnline()) {
			return null;
		}

		Direction facing = getBlockState().getValue(DirectionalMachineBlock.FACING);
		BlockPos targetPos = getTargetPos();
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

		return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState, targetBlockEntity, facing.getOpposite());
	}

	private boolean hasKineticConnection() {
		if (level == null) {
			return false;
		}

		Direction facing = getBlockState().getValue(DirectionalMachineBlock.FACING);
		return level.getBlockEntity(worldPosition.relative(facing.getOpposite())) instanceof KineticBlockEntity;
	}

	private static String formatPos(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
