package com.mechanicalstorage.block;

import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MechanicalStorageTerminalBlock extends DirectionalMachineBlock implements EntityBlock {
	public MechanicalStorageTerminalBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TerminalBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("Terminal: placeholder status. Network scan and GUI are next."));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		useWithoutItem(state, level, pos, player, hitResult);
		return ItemInteractionResult.SUCCESS;
	}
}
