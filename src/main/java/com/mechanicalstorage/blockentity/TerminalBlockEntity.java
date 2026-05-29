package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TerminalBlockEntity extends BlockEntity {
	public TerminalBlockEntity(BlockPos pos, BlockState blockState) {
		super(MechanicalStorage.MECHANICAL_STORAGE_TERMINAL_BLOCK_ENTITY.get(), pos, blockState);
	}
}
