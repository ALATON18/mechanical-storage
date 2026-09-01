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

	/**
	 * Returns whether this endpoint is visible from the requested network.
	 * Stationary connectors belong to one kinetic network; moving connectors may
	 * additionally expose their mounted storage through a controlling bearing.
	 */
	default boolean isOnStorageNetwork(StorageNetworkKey networkKey) {
		return networkKey != null && networkKey.equals(getStorageNetworkKey());
	}

	boolean isEndpointAvailable(long gameTime);

	BlockPos getTargetPos();

	/**
	 * Stable identity used to avoid scanning the same storage twice. Moving
	 * connectors override this with the target's contraption-local position.
	 */
	default Object getStorageIdentity() {
		return getTargetPos();
	}

	/**
	 * Whether this connector may receive an item when no inventory already
	 * contains that item type. Standard connectors remain matching-only.
	 */
	default boolean acceptsUnmatchedItems() {
		return false;
	}

	@Nullable
	IItemHandler getTargetItemHandler();

	@Nullable
	IFluidHandler getTargetFluidHandler();
}
