package com.mechanicalstorage.block;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.blockentity.MechanicalStorageLogisticsBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Six-way kinetic interface used by the Create logistics bridge blocks.
 * The front performs the block's logistics job and the opposite face accepts a
 * shaft, so the same block can be installed on floors, ceilings or walls.
 */
public class MechanicalStorageLogisticsBlock extends OrientedConnectorBlock
		implements IBE<MechanicalStorageLogisticsBlockEntity> {
	private final Role role;

	public MechanicalStorageLogisticsBlock(Properties properties, Role role) {
		super(properties);
		this.role = role;
	}

	public Role role() {
		return role;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING).getOpposite();
	}

	@Override
	public Class<MechanicalStorageLogisticsBlockEntity> getBlockEntityClass() {
		return MechanicalStorageLogisticsBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MechanicalStorageLogisticsBlockEntity> getBlockEntityType() {
		return MechanicalStorage.LOGISTICS_BLOCK_ENTITY.get();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
	}

	public enum Role {
		MONITOR,
		DISPATCH
	}
}
