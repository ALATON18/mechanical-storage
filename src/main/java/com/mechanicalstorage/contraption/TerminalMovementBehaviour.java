package com.mechanicalstorage.contraption;

import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.network.StorageNetworkKey;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Creates the server-side Terminal endpoint that replaces the placed block
 * entity while Create owns the assembled blocks.
 */
public class TerminalMovementBehaviour implements MovementBehaviour {
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

		MovingTerminalEndpoint endpoint;
		if (context.temporaryData instanceof MovingTerminalEndpoint existing) {
			endpoint = existing;
		} else {
			endpoint = new MovingTerminalEndpoint(context);
			context.temporaryData = endpoint;
		}
		endpoint.update();
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
		if (context.temporaryData instanceof MovingTerminalEndpoint endpoint) {
			StorageNetworkRegistry.unregisterMovingTerminal(endpoint.terminal);
			endpoint.terminal.clearMovingNetwork();
			context.temporaryData = null;
		}
	}

	private static final class MovingTerminalEndpoint {
		private final MovementContext context;
		private final StorageNetworkKey networkKey;
		private final TerminalBlockEntity terminal;

		private MovingTerminalEndpoint(MovementContext context) {
			this.context = context;
			AbstractContraptionEntity entity = context.contraption.entity;
			this.networkKey = StorageNetworkKey.moving(entity.getUUID());
			this.terminal = new TerminalBlockEntity(context.localPos, context.state);
			if (context.blockEntityData != null) {
				terminal.readClient(context.blockEntityData, context.world.registryAccess());
			}
			terminal.markVirtual();
			terminal.setLevel(context.world);
			terminal.configureMovingNetwork(networkKey, entity.getId(), context.localPos, this::persistTerminalData);
		}

		private void update() {
			AbstractContraptionEntity entity = context.contraption.entity;
			Vec3 terminalCenter = entity.toGlobalVector(Vec3.atCenterOf(context.localPos), 0);
			terminal.updateMovingState(entity.getId(), BlockPos.containing(terminalCenter),
					context.world.getGameTime());
			StorageNetworkRegistry.registerMovingTerminal(terminal);
		}

		private void persistTerminalData() {
			if (context.blockEntityData == null) {
				context.blockEntityData = new CompoundTag();
			}
			terminal.writeClient(context.blockEntityData, context.world.registryAccess());
		}
	}
}
