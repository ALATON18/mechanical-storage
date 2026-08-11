package com.mechanicalstorage.network;

import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

public final class StorageNetworkRegistry {
	private static final Map<Level, Set<StorageConnectorEndpoint>> CONNECTORS_BY_LEVEL = new WeakHashMap<>();
	private static final Map<Level, Map<MovingTerminalId, TerminalBlockEntity>> MOVING_TERMINALS_BY_LEVEL =
			new WeakHashMap<>();

	private StorageNetworkRegistry() {
	}

	public static synchronized void register(StorageConnectorEndpoint connector) {
		Level level = connector.getStorageLevel();
		if (level == null || level.isClientSide) {
			return;
		}

		CONNECTORS_BY_LEVEL.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(connector);
	}

	public static synchronized void unregister(StorageConnectorEndpoint connector) {
		Level level = connector.getStorageLevel();
		if (level == null) {
			return;
		}

		Set<StorageConnectorEndpoint> connectors = CONNECTORS_BY_LEVEL.get(level);
		if (connectors == null) {
			return;
		}

		connectors.remove(connector);
		if (connectors.isEmpty()) {
			CONNECTORS_BY_LEVEL.remove(level);
		}
	}

	public static synchronized List<StorageConnectorEndpoint> findConnectors(TerminalBlockEntity terminal, int limit) {
		Level level = terminal.getLevel();
		StorageNetworkKey networkKey = terminal.getStorageNetworkKey();
		if (level == null || level.isClientSide || !terminal.isOnline() || networkKey == null) {
			return List.of();
		}

		Set<StorageConnectorEndpoint> registered = CONNECTORS_BY_LEVEL.get(level);
		if (registered == null || registered.isEmpty()) {
			return List.of();
		}

		long gameTime = level.getGameTime();
		registered.removeIf(connector -> connector.getStorageLevel() != level
				|| !connector.isEndpointAvailable(gameTime));

		List<StorageConnectorEndpoint> candidates = new ArrayList<>(registered);
		candidates.sort(Comparator.comparing(StorageConnectorEndpoint::getTargetPos));

		List<StorageConnectorEndpoint> result = new ArrayList<>();
		Set<Object> seenTargets = new HashSet<>();
		for (StorageConnectorEndpoint connector : candidates) {
			if (result.size() >= limit) {
				break;
			}

			if (Objects.equals(connector.getStorageNetworkKey(), networkKey)
					&& seenTargets.add(connector.getStorageIdentity())) {
				result.add(connector);
			}
		}

		return result;
	}

	public static synchronized void registerMovingTerminal(TerminalBlockEntity terminal) {
		Level level = terminal.getLevel();
		StorageNetworkKey networkKey = terminal.getStorageNetworkKey();
		BlockPos localPos = terminal.getMovingLocalPos();
		if (level == null || level.isClientSide || networkKey == null || localPos == null
				|| networkKey.kind() != StorageNetworkKey.Kind.MOVING) {
			return;
		}

		MOVING_TERMINALS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>())
				.put(new MovingTerminalId(networkKey, localPos), terminal);
	}

	public static synchronized void unregisterMovingTerminal(TerminalBlockEntity terminal) {
		Level level = terminal.getLevel();
		StorageNetworkKey networkKey = terminal.getStorageNetworkKey();
		BlockPos localPos = terminal.getMovingLocalPos();
		if (level == null || networkKey == null || localPos == null) {
			return;
		}

		Map<MovingTerminalId, TerminalBlockEntity> terminals = MOVING_TERMINALS_BY_LEVEL.get(level);
		if (terminals == null) {
			return;
		}

		terminals.remove(new MovingTerminalId(networkKey, localPos), terminal);
		if (terminals.isEmpty()) {
			MOVING_TERMINALS_BY_LEVEL.remove(level);
		}
	}

	public static synchronized TerminalBlockEntity findMovingTerminal(Level level, StorageNetworkKey networkKey,
			BlockPos localPos) {
		Map<MovingTerminalId, TerminalBlockEntity> terminals = MOVING_TERMINALS_BY_LEVEL.get(level);
		if (terminals == null) {
			return null;
		}

		MovingTerminalId id = new MovingTerminalId(networkKey, localPos);
		TerminalBlockEntity terminal = terminals.get(id);
		if (terminal != null && !terminal.isMovingEndpointAvailable(level.getGameTime())) {
			terminals.remove(id);
			terminal = null;
		}
		if (terminals.isEmpty()) {
			MOVING_TERMINALS_BY_LEVEL.remove(level);
		}
		return terminal;
	}

	private record MovingTerminalId(StorageNetworkKey networkKey, BlockPos localPos) {
	}
}
