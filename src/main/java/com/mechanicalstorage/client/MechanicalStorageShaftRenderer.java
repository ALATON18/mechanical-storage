package com.mechanicalstorage.client;

import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.mechanicalstorage.block.MechanicalStorageCogwheelConnectorBlock;
import com.mechanicalstorage.block.OrientedConnectorBlock;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

public class MechanicalStorageShaftRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T> {
	private static final int SCREEN_FRAME_COUNT = 6;
	private static final float RPM_PER_SCREEN_CYCLE = 16.0F;
	private static final float MAX_SCREEN_CYCLES_PER_SECOND = 8.0F;

	private final Map<TerminalBlockEntity, ScreenAnimationState> terminalScreenAnimations = new WeakHashMap<>();

	public MechanicalStorageShaftRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(T be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		if (!be.hasLevel()) {
			return;
		}

		BlockState state = be.getBlockState();
		Direction front = state.getBlock() instanceof OrientedConnectorBlock
				? state.getValue(OrientedConnectorBlock.FACING)
				: state.getValue(DirectionalMachineBlock.FACING);
		if (state.getBlock() instanceof MechanicalStorageCogwheelConnectorBlock) {
			Direction cogDirection = Direction.fromAxisAndDirection(front.getAxis(), AxisDirection.POSITIVE);
			SuperByteBuffer cogwheel = CachedBuffers.partialFacingVertical(AllPartialModels.SHAFTLESS_COGWHEEL, state, cogDirection);
			KineticBlockEntityRenderer.standardKineticRotationTransform(cogwheel, be, light)
					.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
			return;
		}

		Direction shaftDirection = front.getOpposite();
		SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, shaftDirection);
		int rearLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(shaftDirection));
		KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, rearLight)
				.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));

		if (be instanceof TerminalBlockEntity terminal) {
			renderTerminalScreen(terminal, state, front, poseStack, buffer);
		}
	}

	private void renderTerminalScreen(TerminalBlockEntity terminal, BlockState state, Direction front,
			PoseStack poseStack, MultiBufferSource buffer) {
		ScreenAnimationState animation = terminalScreenAnimations.computeIfAbsent(terminal,
				ignored -> new ScreenAnimationState());
		float renderTime = AnimationTickHolder.getRenderTime(terminal.getLevel());
		float elapsedTicks = animation.updateRenderTime(renderTime);

		if (terminal.isOnline() && elapsedTicks > 0) {
			float speed = terminal.getSpeed();
			float cyclesPerSecond = Mth.clamp(Math.abs(speed) / RPM_PER_SCREEN_CYCLE,
					0.0F, MAX_SCREEN_CYCLES_PER_SECOND);
			animation.phase += elapsedTicks * cyclesPerSecond / 20.0F * Math.signum(speed);
			animation.phase -= Mth.floor(animation.phase);
		}

		int frame = Mth.floor(animation.phase * SCREEN_FRAME_COUNT) % SCREEN_FRAME_COUNT;
		SuperByteBuffer screen = CachedBuffers.partialFacing(
				MechanicalStoragePartialModels.TERMINAL_SCREEN_FRAMES[frame], state, front);
		screen.light(LightTexture.FULL_BRIGHT)
				.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private static final class ScreenAnimationState {
		private float phase;
		private float lastRenderTime = Float.NaN;

		private float updateRenderTime(float renderTime) {
			if (Float.isNaN(lastRenderTime)) {
				lastRenderTime = renderTime;
				return 0;
			}

			float elapsed = Mth.clamp(renderTime - lastRenderTime, 0.0F, 1.0F);
			lastRenderTime = renderTime;
			return elapsed;
		}
	}
}

