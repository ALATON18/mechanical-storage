package com.mechanicalstorage.contraption;

import com.mechanicalstorage.block.OrientedConnectorBlock;
import com.mechanicalstorage.network.StorageConnectorEndpoint;
import com.mechanicalstorage.network.StorageNetworkKey;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps a connector useful while Create has it mounted as a contraption actor.
 * The connector's storage relationship remains in contraption-local space and
 * the moving assembly itself supplies actor power.
 */
public class ConnectorMovementBehaviour implements MovementBehaviour {
	private static final int ENDPOINT_GRACE_TICKS = 2;

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		Direction facing = context.state.getValue(OrientedConnectorBlock.FACING);
		return Vec3.atLowerCornerOf(facing.getNormal());
	}

	@Override
	public boolean mustTickWhileDisabled() {
		return true;
	}

	@Override
	public void tick(MovementContext context) {
		if (context.world.isClientSide) {
			restoreActorVisualSpeed(context);
			return;
		}

		if (context.disabled || context.position == null || context.contraption.entity == null) {
			unregister(context);
			return;
		}

		MovingConnectorEndpoint endpoint;
		if (context.temporaryData instanceof MovingConnectorEndpoint existing) {
			endpoint = existing;
		} else {
			endpoint = new MovingConnectorEndpoint(context);
			context.temporaryData = endpoint;
		}
		endpoint.update();
		StorageNetworkRegistry.register(endpoint);
	}

	@Override
	public void stopMoving(MovementContext context) {
		unregister(context);
	}

	private static void restoreActorVisualSpeed(MovementContext context) {
		BlockEntity blockEntity = context.contraption.getBlockEntityClientSide(context.localPos);
		if (blockEntity instanceof KineticBlockEntity kinetic) {
			kinetic.setSpeed(context.getAnimationSpeed());
		}
	}

	private static void unregister(MovementContext context) {
		if (context.temporaryData instanceof MovingConnectorEndpoint endpoint) {
			StorageNetworkRegistry.unregister(endpoint);
			context.temporaryData = null;
		}
	}

	private static final class MovingConnectorEndpoint implements StorageConnectorEndpoint {
		private final MovementContext context;
		private final BlockPos localTargetPos;
		private final StorageNetworkKey networkKey;
		@Nullable
		private final MountedItemStorage mountedItems;
		@Nullable
		private final MountedFluidStorage mountedFluids;
		private long lastSeenTick = Long.MIN_VALUE;
		private BlockPos targetPos = BlockPos.ZERO;
		private Direction targetFacing = Direction.NORTH;

		private MovingConnectorEndpoint(MovementContext context) {
			this.context = context;
			this.networkKey = StorageNetworkKey.moving(context.contraption.entity.getUUID());
			Direction localFacing = context.state.getValue(OrientedConnectorBlock.FACING);
			this.localTargetPos = context.localPos.relative(localFacing);
			this.mountedItems = context.contraption.getStorage().getAllItemStorages().get(localTargetPos);
			this.mountedFluids = context.contraption.getStorage().getFluids().storages.get(localTargetPos);
			update();
		}

		private void update() {
			lastSeenTick = context.world.getGameTime();
			Vec3 targetCenter = context.contraption.entity.toGlobalVector(Vec3.atCenterOf(localTargetPos), 0);
			targetPos = BlockPos.containing(targetCenter);

			Direction localFacing = context.state.getValue(OrientedConnectorBlock.FACING);
			Vec3 rotatedFacing = context.rotation.apply(Vec3.atLowerCornerOf(localFacing.getNormal()));
			targetFacing = Direction.getNearest(rotatedFacing.x, rotatedFacing.y, rotatedFacing.z);
		}

		@Override
		public Level getStorageLevel() {
			return context.world;
		}

		@Override
		public StorageNetworkKey getStorageNetworkKey() {
			return networkKey;
		}

		@Override
		public boolean isEndpointAvailable(long gameTime) {
			return gameTime - lastSeenTick <= ENDPOINT_GRACE_TICKS
					&& context.contraption.entity != null
					&& context.contraption.entity.isAlive();
		}

		@Override
		public BlockPos getTargetPos() {
			return targetPos;
		}

		@Override
		public Object getStorageIdentity() {
			return localTargetPos;
		}

		@Override
		@Nullable
		public IItemHandler getTargetItemHandler() {
			if (mountedItems != null) {
				return mountedItems;
			}
			return getWorldItemHandler();
		}

		@Override
		@Nullable
		public IFluidHandler getTargetFluidHandler() {
			if (mountedFluids != null) {
				return mountedFluids;
			}
			return getWorldFluidHandler();
		}

		@Nullable
		private IItemHandler getWorldItemHandler() {
			Level level = context.world;
			BlockState state = level.getBlockState(targetPos);
			BlockEntity blockEntity = level.getBlockEntity(targetPos);
			return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, state, blockEntity,
					targetFacing.getOpposite());
		}

		@Nullable
		private IFluidHandler getWorldFluidHandler() {
			Level level = context.world;
			BlockState state = level.getBlockState(targetPos);
			BlockEntity blockEntity = level.getBlockEntity(targetPos);
			return level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, state, blockEntity,
					targetFacing.getOpposite());
		}
	}
}
