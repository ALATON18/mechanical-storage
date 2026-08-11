package com.mechanicalstorage.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Common storage access exposed by stationary and contraption-mounted connectors.
 */
public interface StorageConnectorEndpoint {
	@Nullable
	Level getStorageLevel();

	@Nullable
	Long getKineticNetworkId();

	boolean isEndpointAvailable(long gameTime);

	BlockPos getTargetPos();

	@Nullable
	IItemHandler getTargetItemHandler();

	@Nullable
	IFluidHandler getTargetFluidHandler();
}
