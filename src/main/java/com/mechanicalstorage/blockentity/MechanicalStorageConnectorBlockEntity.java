package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.OrientedConnectorBlock;
import com.mechanicalstorage.network.StorageConnectorEndpoint;
import com.mechanicalstorage.network.StorageNetworkKey;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class MechanicalStorageConnectorBlockEntity extends FixedStressKineticBlockEntity implements StorageConnectorEndpoint {
	public static final float FIXED_STRESS_UNITS = 128.0F;

	public MechanicalStorageConnectorBlockEntity(BlockEntityType<? extends MechanicalStorageConnectorBlockEntity> type, BlockPos pos, BlockState blockState) {
		super(type, pos, blockState, FIXED_STRESS_UNITS);
	}

	public MechanicalStorageConnectorBlockEntity(BlockPos pos, BlockState blockState) {
		this(MechanicalStorage.CONNECTOR_BLOCK_ENTITY.get(), pos, blockState);
	}

	public boolean isOnline() {
		return isSpeedRequirementFulfilled() && hasNetwork();
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

	@Override
	public Level getStorageLevel() {
		return level;
	}

	@Override
	@Nullable
	public StorageNetworkKey getStorageNetworkKey() {
		return network == null ? null : StorageNetworkKey.kinetic(network);
	}

	@Override
	public boolean isEndpointAvailable(long gameTime) {
		return !isRemoved() && isOnline();
	}

	public Component describeTargetInventory() {
		if (!isOnline()) {
			if (isOverStressed()) {
				return Component.translatable("status.mechanical_storage.overstressed");
			}
			if (getTheoreticalSpeed() != 0) {
				return Component.translatable("status.mechanical_storage.too_slow");
			}
			return Component.translatable("status.mechanical_storage.disconnected");
		}

		IItemHandler handler = getTargetItemHandler();
		IFluidHandler fluidHandler = getTargetFluidHandler();
		BlockPos targetPos = getTargetPos();

		if (handler == null && fluidHandler == null) {
			return Component.literal("Connector: no item or fluid storage found at " + formatPos(targetPos) + ".");
		}

		int slots = handler == null ? 0 : handler.getSlots();
		int occupiedSlots = 0;
		int totalItems = 0;

		for (int slot = 0; slot < slots; slot++) {
			ItemStack stack = handler.getStackInSlot(slot);
			if (!stack.isEmpty()) {
				occupiedSlots++;
				totalItems += stack.getCount();
			}
		}

		int tanks = fluidHandler == null ? 0 : fluidHandler.getTanks();
		int occupiedTanks = 0;
		long totalFluid = 0;
		for (int tank = 0; tank < tanks; tank++) {
			FluidStack fluid = fluidHandler.getFluidInTank(tank);
			if (!fluid.isEmpty()) {
				occupiedTanks++;
				totalFluid += fluid.getAmount();
			}
		}

		return Component.literal("Connector: online at " + Math.abs(getSpeed()) + " RPM, storage at "
				+ formatPos(targetPos) + " (" + occupiedSlots + "/" + slots + " item slots, " + totalItems
				+ " items; " + occupiedTanks + "/" + tanks + " fluid tanks, " + totalFluid + " mB).");
	}

	public BlockPos getTargetPos() {
		Direction facing = getBlockState().getValue(OrientedConnectorBlock.FACING);
		return worldPosition.relative(facing);
	}

	@Nullable
	public IItemHandler getTargetItemHandler() {
		if (level == null || !isOnline()) {
			return null;
		}

		Direction facing = getBlockState().getValue(OrientedConnectorBlock.FACING);
		BlockPos targetPos = getTargetPos();
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

		return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState, targetBlockEntity, facing.getOpposite());
	}

	@Nullable
	public IFluidHandler getTargetFluidHandler() {
		if (level == null || !isOnline()) {
			return null;
		}

		Direction facing = getBlockState().getValue(OrientedConnectorBlock.FACING);
		BlockPos targetPos = getTargetPos();
		BlockState targetState = level.getBlockState(targetPos);
		BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

		return level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetState, targetBlockEntity,
				facing.getOpposite());
	}

	private static String formatPos(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
