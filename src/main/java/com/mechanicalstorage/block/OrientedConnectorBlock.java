package com.mechanicalstorage.block;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared six-direction orientation for inventory-facing connector variants.
 */
public abstract class OrientedConnectorBlock extends DirectionalKineticBlock {
	protected OrientedConnectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	public BlockPos getTargetPosition(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING));
	}
}
