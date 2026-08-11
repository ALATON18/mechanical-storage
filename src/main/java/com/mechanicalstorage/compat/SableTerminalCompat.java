package com.mechanicalstorage.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Optional Sable integration for terminals assembled into Create Aeronautics
 * creations.
 *
 * <p>Sable keeps assembled blocks and block entities at sub-level-local plot
 * coordinates. Vanilla menu validation therefore sees the player as being far
 * away even though they are standing beside the rendered terminal. This
 * adapter validates the local block, then transforms its centre through the
 * sub-level's live logical pose before applying the normal eight-block range.</p>
 */
public final class SableTerminalCompat {
	private SableTerminalCompat() {
	}

	public static boolean isTerminalStillValid(Player player, BlockPos localPos, Block expectedBlock) {
		if (!player.level().getBlockState(localPos).is(expectedBlock)) {
			return false;
		}

		Vec3 localCentre = Vec3.atCenterOf(localPos);
		SubLevel subLevel = Sable.HELPER.getContaining(player.level(), localCentre);
		if (subLevel == null) {
			return false;
		}

		Vec3 worldCentre = subLevel.logicalPose().transformPosition(localCentre);
		return player.distanceToSqr(worldCentre.x, worldCentre.y, worldCentre.z) <= 64.0;
	}
}
