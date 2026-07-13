package com.mechanicalstorage.network;

import com.mechanicalstorage.blockentity.MechanicalStorageConnectorBlockEntity;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class StorageNetworkRegistry {
	private static final Map<Level, Set<MechanicalStorageConnectorBlockEntity>> CONNECTORS_BY_LEVEL = new WeakHashMap<>();

	private StorageNetworkRegistry() {
	}

	public static synchronized void register(MechanicalStorageConnectorBlockEntity connector) {
		Level level = connector.getLevel();
		if (level == null || level.isClientSide) {
			return;
		}

		CONNECTORS_BY_LEVEL.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(connector);
	}

	public static synchronized void unregister(MechanicalStorageConnectorBlockEntity connector) {
		Level level = connector.getLevel();
		if (level == null) {
			return;
		}

		Set<MechanicalStorageConnectorBlockEntity> connectors = CONNECTORS_BY_LEVEL.get(level);
		if (connectors == null) {
			return;
		}

		connectors.remove(connector);
		if (connectors.isEmpty()) {
			CONNECTORS_BY_LEVEL.remove(level);
		}
	}

	public static synchronized List<MechanicalStorageConnectorBlockEntity> findConnectors(TerminalBlockEntity terminal, int limit) {
		Level level = terminal.getLevel();
		if (level == null || level.isClientSide || !terminal.isOnline()) {
			return List.of();
		}

		Set<MechanicalStorageConnectorBlockEntity> registered = CONNECTORS_BY_LEVEL.get(level);
		if (registered == null || registered.isEmpty()) {
			return List.of();
		}

		registered.removeIf(connector -> connector.isRemoved() || connector.getLevel() != level);

		List<MechanicalStorageConnectorBlockEntity> candidates = new ArrayList<>(registered);
		candidates.sort(Comparator.comparing(MechanicalStorageConnectorBlockEntity::getBlockPos));

		List<MechanicalStorageConnectorBlockEntity> result = new ArrayList<>();
		Set<BlockPos> seenTargets = new HashSet<>();
		for (MechanicalStorageConnectorBlockEntity connector : candidates) {
			if (result.size() >= limit) {
				break;
			}

			if (connector.isOnSameNetwork(terminal) && seenTargets.add(connector.getTargetPos())) {
				result.add(connector);
			}
		}

		return result;
	}
}
