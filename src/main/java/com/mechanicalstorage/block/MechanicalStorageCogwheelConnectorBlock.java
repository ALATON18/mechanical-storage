package com.mechanicalstorage.block;

import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Connector variant driven by a shaftless, andesite-encased small cogwheel.
 */
public class MechanicalStorageCogwheelConnectorBlock extends MechanicalStorageConnectorBlock implements ICogWheel {
	public MechanicalStorageCogwheelConnectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return false;
	}

	@Override
	public boolean isSmallCog() {
		return true;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		return CogWheelBlock.isValidCogwheelPosition(false, world, pos, getRotationAxis(state));
	}
}
