package com.mechanicalstorage.network;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime identity for a Mechanical Storage network.
 *
 * <p>Placed blocks use Create's kinetic network id. Blocks captured by a
 * contraption use the contraption identity instead, so their relationship does
 * not depend on a world position or on the kinetic network that existed before
 * assembly.</p>
 */
public record StorageNetworkKey(Kind kind, long kineticNetworkId, UUID movingStructureId) {
	public StorageNetworkKey {
		Objects.requireNonNull(kind, "kind");
		if (kind == Kind.MOVING) {
			Objects.requireNonNull(movingStructureId, "movingStructureId");
		}
	}

	public static StorageNetworkKey kinetic(long kineticNetworkId) {
		return new StorageNetworkKey(Kind.KINETIC, kineticNetworkId, null);
	}

	public static StorageNetworkKey moving(UUID movingStructureId) {
		return new StorageNetworkKey(Kind.MOVING, 0, movingStructureId);
	}

	public enum Kind {
		KINETIC,
		MOVING
	}
}
