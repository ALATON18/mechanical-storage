package com.mechanicalstorage.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Optional Sable integration for terminals assembled into Create Aeronautics
 * creations.
 *
 * <p>Sable keeps assembled blocks and block entities at sub-level-local plot
 * coordinates. This adapter validates the local block, then uses the supported
 * Companion distance API to apply the normal eight-block menu range in global
 * space. Companion falls back to ordinary distance when Sable is absent.</p>
 */
public final class SableTerminalCompat {
	private SableTerminalCompat() {
	}

	public static boolean isTerminalStillValid(Player player, BlockPos localPos, Block expectedBlock) {
		if (!player.level().getBlockState(localPos).is(expectedBlock)) {
			return false;
		}

		Vec3 terminalCentre = Vec3.atCenterOf(localPos);
		return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(
				player.level(), player.position(),
				terminalCentre.x, terminalCentre.y, terminalCentre.z) <= 64.0;
	}
}
