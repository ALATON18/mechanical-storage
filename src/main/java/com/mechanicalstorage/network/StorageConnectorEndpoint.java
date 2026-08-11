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
	StorageNetworkKey getStorageNetworkKey();

	boolean isEndpointAvailable(long gameTime);

	BlockPos getTargetPos();

	/**
	 * Stable identity used to avoid scanning the same storage twice. Moving
	 * connectors override this with the target's contraption-local position.
	 */
	default Object getStorageIdentity() {
		return getTargetPos();
	}

	@Nullable
	IItemHandler getTargetItemHandler();

	@Nullable
	IFluidHandler getTargetFluidHandler();
}
