package com.mechanicalstorage.blockentity;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.MechanicalStorageLogisticsBlock;
import com.mechanicalstorage.block.OrientedConnectorBlock;
import com.mechanicalstorage.config.MechanicalStorageConfig;
import com.mechanicalstorage.network.StorageConnectorEndpoint;
import com.mechanicalstorage.network.StorageNetworkKey;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Bridges one Mechanical Storage kinetic network to Create's logistics system.
 * Monitor instances report stock but never extract it. Dispatch instances expose
 * extraction to one Packager mounted against their front face but do not report
 * stock themselves, preventing the network from being counted twice.
 */
public class MechanicalStorageLogisticsBlockEntity extends FixedStressKineticBlockEntity {
	public static final int MAX_CONNECTORS = 64;
	public static final float FIXED_STRESS_UNITS = 128.0F;
	private static final Map<Level, RequestReservationWindow> REQUEST_RESERVATIONS = new WeakHashMap<>();

	private final NetworkItemHandler dispatchInventory = new NetworkItemHandler(this);
	public MechanicalStorageLogisticsBehaviour logistics;

	private long slotSnapshotTick = Long.MIN_VALUE;
	private StorageNetworkKey slotSnapshotNetwork;
	private List<ItemStack> slotSnapshot = List.of();
	private long authorizationTick = Long.MIN_VALUE;
	private StorageNetworkKey authorizationNetwork;
	private final List<DispatchAuthorization> dispatchAuthorizations = new ArrayList<>();
	private long summaryCacheTick = Long.MIN_VALUE;
	private StorageNetworkKey summaryCacheNetwork;
	private InventorySummary summaryCache;

	public MechanicalStorageLogisticsBlockEntity(
			BlockEntityType<? extends MechanicalStorageLogisticsBlockEntity> type,
			BlockPos pos, BlockState blockState) {
		super(type, pos, blockState, FIXED_STRESS_UNITS);
		setLazyTickRate(10);
	}

	public MechanicalStorageLogisticsBlockEntity(BlockPos pos, BlockState blockState) {
		this(MechanicalStorage.LOGISTICS_BLOCK_ENTITY.get(), pos, blockState);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(logistics = new MechanicalStorageLogisticsBehaviour(this));
	}

	public boolean isOnline() {
		return MechanicalStorageConfig.isBlockEnabled(getBlockState().getBlock())
				&& isSpeedRequirementFulfilled() && hasNetwork();
	}

	public boolean isMonitor() {
		return getRole() == MechanicalStorageLogisticsBlock.Role.MONITOR;
	}

	public boolean isDispatch() {
		return getRole() == MechanicalStorageLogisticsBlock.Role.DISPATCH;
	}

	@Nullable
	public StorageNetworkKey getStorageNetworkKey() {
		return network == null ? null : StorageNetworkKey.kinetic(network);
	}

	/**
	 * The capability is stable for the block entity's lifetime. Its methods check
	 * current speed, network and config state before exposing or extracting items.
	 */
	@Nullable
	public IItemHandler getDispatchInventory(@Nullable Direction side) {
		if (!isDispatch()) {
			return null;
		}
		Direction front = getBlockState().getValue(OrientedConnectorBlock.FACING);
		return side == null || side == front ? dispatchInventory : null;
	}

	private MechanicalStorageLogisticsBlock.Role getRole() {
		return getBlockState().getBlock() instanceof MechanicalStorageLogisticsBlock logisticsBlock
				? logisticsBlock.role()
				: MechanicalStorageLogisticsBlock.Role.MONITOR;
	}

	private List<StorageConnectorEndpoint> findNetworkConnectors() {
		Level level = getLevel();
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || !isOnline() || networkKey == null) {
			return List.of();
		}
		return StorageNetworkRegistry.findConnectors(level, networkKey, MAX_CONNECTORS);
	}

	private InventorySummary createNetworkSummary() {
		Level level = getLevel();
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || level.isClientSide || !isOnline() || networkKey == null) {
			return InventorySummary.EMPTY;
		}

		long gameTime = level.getGameTime();
		boolean cacheSummary = isMonitor();
		if (cacheSummary && summaryCache != null && summaryCacheTick == gameTime
				&& networkKey.equals(summaryCacheNetwork)) {
			return summaryCache;
		}

		InventorySummary summary = new InventorySummary();
		for (StorageConnectorEndpoint connector : findNetworkConnectors()) {
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack stack = handler.getStackInSlot(slot);
				if (!stack.isEmpty()) {
					summary.add(stack, stack.getCount());
				}
			}
		}

		if (cacheSummary) {
			summaryCacheTick = gameTime;
			summaryCacheNetwork = networkKey;
			summaryCache = summary;
		}
		return summary;
	}

	private List<ItemStack> createSlotSnapshot() {
		Level level = getLevel();
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || level.isClientSide || !isDispatch() || !isOnline() || networkKey == null) {
			return List.of();
		}

		refreshAuthorizationWindow(level, networkKey);
		long gameTime = level.getGameTime();
		if (slotSnapshotTick == gameTime && networkKey.equals(slotSnapshotNetwork)) {
			return slotSnapshot;
		}

		List<ItemStack> slots = new ArrayList<>();
		for (DispatchAuthorization authorization : dispatchAuthorizations) {
			if (authorization.remaining > 0) {
				slots.add(authorization.item.copyWithCount(
						Math.min(authorization.remaining, authorization.item.getMaxStackSize())));
			}
		}

		slotSnapshotTick = gameTime;
		slotSnapshotNetwork = networkKey;
		slotSnapshot = List.copyOf(slots);
		return slotSnapshot;
	}

	private void authorizeDispatch(ItemStack stack, int amount) {
		Level level = getLevel();
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || networkKey == null || amount <= 0) {
			return;
		}
		refreshAuthorizationWindow(level, networkKey);
		for (DispatchAuthorization authorization : dispatchAuthorizations) {
			if (ItemStack.isSameItemSameComponents(authorization.item, stack)) {
				authorization.remaining = saturatedAdd(authorization.remaining, amount);
				slotSnapshotTick = Long.MIN_VALUE;
				return;
			}
		}
		dispatchAuthorizations.add(new DispatchAuthorization(stack.copyWithCount(1), amount));
		slotSnapshotTick = Long.MIN_VALUE;
	}

	private int getAuthorizedAmount(ItemStack stack) {
		Level level = getLevel();
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || networkKey == null) {
			return 0;
		}
		refreshAuthorizationWindow(level, networkKey);
		for (DispatchAuthorization authorization : dispatchAuthorizations) {
			if (ItemStack.isSameItemSameComponents(authorization.item, stack)) {
				return authorization.remaining;
			}
		}
		return 0;
	}

	private void consumeAuthorization(ItemStack stack, int amount) {
		if (amount <= 0) {
			return;
		}
		Level level = getLevel();
		StorageNetworkKey networkKey = getStorageNetworkKey();
		if (level == null || networkKey == null) {
			return;
		}
		refreshAuthorizationWindow(level, networkKey);
		for (DispatchAuthorization authorization : dispatchAuthorizations) {
			if (ItemStack.isSameItemSameComponents(authorization.item, stack)) {
				authorization.remaining = Math.max(0, authorization.remaining - amount);
				return;
			}
		}
	}

	private void refreshAuthorizationWindow(Level level, StorageNetworkKey networkKey) {
		long gameTime = level.getGameTime();
		if (authorizationTick == gameTime && Objects.equals(authorizationNetwork, networkKey)) {
			return;
		}
		authorizationTick = gameTime;
		authorizationNetwork = networkKey;
		dispatchAuthorizations.clear();
		slotSnapshotTick = Long.MIN_VALUE;
	}

	private ItemStack extractMatching(ItemStack template, int amount, boolean simulate) {
		if (template.isEmpty() || amount <= 0 || !isOnline()) {
			return ItemStack.EMPTY;
		}

		int remaining = Math.min(amount, template.getMaxStackSize());
		ItemStack extractedTotal = ItemStack.EMPTY;
		for (StorageConnectorEndpoint connector : findNetworkConnectors()) {
			IItemHandler handler = connector.getTargetItemHandler();
			if (handler == null) {
				continue;
			}
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack available = handler.getStackInSlot(slot);
				if (available.isEmpty() || !ItemStack.isSameItemSameComponents(available, template)) {
					continue;
				}

				ItemStack extracted = handler.extractItem(slot, remaining, simulate);
				if (extracted.isEmpty() || !ItemStack.isSameItemSameComponents(extracted, template)) {
					continue;
				}
				if (extractedTotal.isEmpty()) {
					extractedTotal = extracted.copy();
				} else {
					extractedTotal.grow(extracted.getCount());
				}
				remaining -= extracted.getCount();
				if (remaining <= 0) {
					return finishExtraction(extractedTotal, simulate);
				}
			}
		}
		return finishExtraction(extractedTotal, simulate);
	}

	private ItemStack finishExtraction(ItemStack extracted, boolean simulate) {
		if (simulate || extracted.isEmpty()) {
			return extracted;
		}
		summaryCache = null;
		if (logistics != null) {
			ItemStackHandler deduction = new ItemStackHandler(1);
			deduction.setStackInSlot(0, extracted.copy());
			logistics.deductFromAccurateSummary(deduction);
		}
		return extracted;
	}

	private static int saturatedAdd(int first, int second) {
		long total = (long) first + second;
		return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
	}

	private boolean isPrimaryMonitor() {
		if (!isMonitor() || !isOnline()) {
			return false;
		}
		StorageNetworkKey key = getStorageNetworkKey();
		Level level = getLevel();
		if (key == null || level == null) {
			return false;
		}

		for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(logistics.freqId, false)) {
			if (!(link instanceof MechanicalStorageLogisticsBehaviour candidate)) {
				continue;
			}
			MechanicalStorageLogisticsBlockEntity other = candidate.owner;
			if (other == this || !other.isMonitor() || !other.isOnline() || other.getLevel() != level
					|| !key.equals(other.getStorageNetworkKey())) {
				continue;
			}
			if (other.getBlockPos().asLong() < getBlockPos().asLong()) {
				return false;
			}
		}
		return true;
	}

	private boolean ignoredInventoryIsSameNetwork(@Nullable IdentifiedInventory ignored) {
		if (ignored == null || !(ignored.handler() instanceof NetworkItemHandler handler)) {
			return false;
		}
		StorageNetworkKey key = getStorageNetworkKey();
		return key != null && handler.owner.getLevel() == getLevel()
				&& key.equals(handler.owner.getStorageNetworkKey());
	}

	private int reserveForRequest(ItemStack stack, int requested, int available, int orderId) {
		Level level = getLevel();
		StorageNetworkKey key = getStorageNetworkKey();
		if (level == null || key == null || requested <= 0 || available <= 0) {
			return 0;
		}

		synchronized (REQUEST_RESERVATIONS) {
			RequestReservationWindow window = REQUEST_RESERVATIONS.computeIfAbsent(level,
					ignored -> new RequestReservationWindow());
			long gameTime = level.getGameTime();
			if (window.gameTime != gameTime) {
				window.gameTime = gameTime;
				window.entries.clear();
			}

			int reserved = 0;
			int matchingIndex = -1;
			for (int index = 0; index < window.entries.size(); index++) {
				RequestReservation reservation = window.entries.get(index);
				if (reservation.orderId() == orderId && reservation.frequency().equals(logistics.freqId)
						&& reservation.network().equals(key)
						&& ItemStack.isSameItemSameComponents(reservation.item(), stack)) {
					reserved = reservation.count();
					matchingIndex = index;
					break;
				}
			}

			int toReserve = Math.min(requested, Math.max(0, available - reserved));
			if (toReserve <= 0) {
				return 0;
			}
			RequestReservation updated = new RequestReservation(logistics.freqId, key, orderId,
					stack.copyWithCount(1), reserved + toReserve);
			if (matchingIndex >= 0) {
				window.entries.set(matchingIndex, updated);
			} else {
				window.entries.add(updated);
			}
			return toReserve;
		}
	}

	@Nullable
	private PackagerBlockEntity getAttachedPackager() {
		if (!isDispatch() || !isOnline() || level == null) {
			return null;
		}
		Direction front = getBlockState().getValue(OrientedConnectorBlock.FACING);
		BlockPos packagerPos = worldPosition.relative(front);
		BlockEntity blockEntity = level.getBlockEntity(packagerPos);
		if (!(blockEntity instanceof PackagerBlockEntity packager)
				|| packager instanceof RepackagerBlockEntity
				|| packager.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(null) != front
				|| packager.targetInventory == null
				|| packager.targetInventory.getInventory() != dispatchInventory) {
			return null;
		}
		return packager;
	}

	public static final class MechanicalStorageLogisticsBehaviour extends LogisticallyLinkedBehaviour {
		private final MechanicalStorageLogisticsBlockEntity owner;

		private MechanicalStorageLogisticsBehaviour(MechanicalStorageLogisticsBlockEntity owner) {
			// Live registration is sufficient for Create logistics requests. Avoid adding a
			// second persistent owner entry to Create's global Stock Link manager.
			super(owner, false);
			this.owner = owner;
		}

		@Override
		public InventorySummary getSummary(@Nullable IdentifiedInventory ignoredHandler) {
			if (!owner.isPrimaryMonitor() || owner.ignoredInventoryIsSameNetwork(ignoredHandler)) {
				return InventorySummary.EMPTY;
			}
			return owner.createNetworkSummary();
		}

		@Override
		@Nullable
		public Pair<PackagerBlockEntity, PackagingRequest> processRequest(ItemStack stack, int amount,
				String address, int linkIndex, MutableBoolean finalLink, int orderId,
				@Nullable PackageOrderWithCrafts context, @Nullable IdentifiedInventory ignoredHandler) {
			if (!owner.isDispatch() || !owner.isOnline() || owner.ignoredInventoryIsSameNetwork(ignoredHandler)) {
				return null;
			}
			PackagerBlockEntity packager = owner.getAttachedPackager();
			if (packager == null || packager.isTargetingSameInventory(ignoredHandler)) {
				return null;
			}

			int available = owner.createNetworkSummary().getCountOf(stack);
			if (available <= 0) {
				return null;
			}
			int toWithdraw = owner.reserveForRequest(stack, amount, available, orderId);
			if (toWithdraw <= 0) {
				return null;
			}
			owner.authorizeDispatch(stack, toWithdraw);
			return Pair.of(packager, PackagingRequest.create(stack, toWithdraw, address, linkIndex,
					finalLink, 0, orderId, context));
		}
	}

	private static final class NetworkItemHandler implements IItemHandler {
		private final MechanicalStorageLogisticsBlockEntity owner;

		private NetworkItemHandler(MechanicalStorageLogisticsBlockEntity owner) {
			this.owner = owner;
		}

		@Override
		public int getSlots() {
			return owner.createSlotSnapshot().size();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			List<ItemStack> slots = owner.createSlotSnapshot();
			return slot >= 0 && slot < slots.size() ? slots.get(slot).copy() : ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			List<ItemStack> slots = owner.createSlotSnapshot();
			if (slot < 0 || slot >= slots.size()) {
				return ItemStack.EMPTY;
			}
			ItemStack template = slots.get(slot);
			int authorized = owner.getAuthorizedAmount(template);
			if (authorized <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack extracted = owner.extractMatching(template, Math.min(amount, authorized), simulate);
			if (!simulate && !extracted.isEmpty()) {
				owner.consumeAuthorization(template, extracted.getCount());
			}
			return extracted;
		}

		@Override
		public int getSlotLimit(int slot) {
			ItemStack stack = getStackInSlot(slot);
			return stack.isEmpty() ? 64 : stack.getMaxStackSize();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}
	}

	private static final class RequestReservationWindow {
		private long gameTime = Long.MIN_VALUE;
		private final List<RequestReservation> entries = new ArrayList<>();
	}

	private static final class DispatchAuthorization {
		private final ItemStack item;
		private int remaining;

		private DispatchAuthorization(ItemStack item, int remaining) {
			this.item = item;
			this.remaining = remaining;
		}
	}

	private record RequestReservation(UUID frequency, StorageNetworkKey network, int orderId,
			ItemStack item, int count) {
	}
}
