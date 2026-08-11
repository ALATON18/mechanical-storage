package com.mechanicalstorage.contraption;

import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mechanicalstorage.menu.TerminalMenu;
import com.mechanicalstorage.network.StorageNetworkKey;
import com.mechanicalstorage.network.StorageNetworkRegistry;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** Opens the same Terminal menu from a block owned by a moving contraption. */
public class TerminalMovingInteraction extends MovingInteractionBehaviour {
	@Override
	public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
			AbstractContraptionEntity contraptionEntity) {
		if (player.level().isClientSide) {
			return true;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		StorageNetworkKey networkKey = StorageNetworkKey.moving(contraptionEntity.getUUID());
		TerminalBlockEntity terminal = StorageNetworkRegistry.findMovingTerminal(player.level(), networkKey, localPos);
		if (terminal == null) {
			return false;
		}

		serverPlayer.openMenu(terminal, buffer -> TerminalMenu.writeOpeningData(buffer, terminal));
		return true;
	}
}
