package com.mechanicalstorage.blockentity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class FixedStressKineticBlockEntity extends KineticBlockEntity {
	public static final float MINIMUM_SPEED_RPM = 2.0F;

	private final float fixedStressUnits;

	protected FixedStressKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, float fixedStressUnits) {
		super(type, pos, blockState);
		this.fixedStressUnits = fixedStressUnits;
	}

	public float getFixedStressUnits() {
		return fixedStressUnits;
	}

	@Override
	public boolean isSpeedRequirementFulfilled() {
		return Math.abs(getSpeed()) >= MINIMUM_SPEED_RPM;
	}

	@Override
	public float calculateStressApplied() {
		float speed = Math.abs(getTheoreticalSpeed());
		float impact = speed == 0 ? 0 : fixedStressUnits / speed;
		lastStressApplied = impact;
		return impact;
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		if (level != null && !level.isClientSide && hasNetwork()) {
			getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
		}
	}
}
