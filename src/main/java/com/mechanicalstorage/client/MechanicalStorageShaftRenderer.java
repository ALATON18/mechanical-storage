package com.mechanicalstorage.client;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.mechanicalstorage.block.MechanicalStorageCogwheelConnectorBlock;
import com.mechanicalstorage.block.OrientedConnectorBlock;
import com.mechanicalstorage.blockentity.TerminalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

public class MechanicalStorageShaftRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T> {
	private static final int SCREEN_WIDTH = 8;
	private static final int SCREEN_HEIGHT = 6;
	private static final float RPM_PER_SCREEN_CYCLE = 16.0F;
	private static final float MAX_SCREEN_CYCLES_PER_SECOND = 8.0F;
	private static final double PHASE_WRAP_CYCLES = 96.0;
	private static final double TWO_PI = Math.PI * 2.0;

	/*
	 * All three carriers run at every speed. Nothing below selects an image from
	 * the terminal RPM. At the intended speeds, monitor sampling aliases one
	 * carrier into a slow visible pulse while the other two average into the
	 * grey CRT background. The frequencies target both 60 and 120 FPS.
	 */
	private static final double CREEPER_CARRIER_HARMONIC = 15223.0 / 48.0;
	private static final double COG_CARRIER_HARMONIC = 116.0;
	private static final double HIGH_SPEED_CARRIER_HARMONIC = 18451.0 / 96.0;

	private static final int[] CRT_ROW_BRIGHTNESS = { 84, 92, 100, 108, 100, 92 };
	private static final int CRT_IMAGE_AMPLITUDE = 112;
	private static final ResourceLocation CRT_PIXEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
			MechanicalStorage.MODID, "textures/block/terminal_crt_pixel.png");

	// Patterns are written top-to-bottom; the model-space Y coordinate is inverted below.
	private static final String[] CREEPER_PATTERN = {
			"........",
			"..##.##.",
			"..##.##.",
			"...##...",
			"..####..",
			"..#..#.."
	};
	private static final String[] COG_PATTERN = {
			".##..##.",
			"########",
			"##....##",
			"##....##",
			"########",
			".##..##."
	};
	private static final String[] HIGH_SPEED_PATTERN = {
			".#....#.",
			"..#..#..",
			".######.",
			"##.##.##",
			"########",
			".#....#."
	};

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
			renderTerminalScreen(terminal, front, poseStack, buffer);
		}
	}

	private void renderTerminalScreen(TerminalBlockEntity terminal, Direction front, PoseStack poseStack,
			MultiBufferSource buffer) {
		ScreenAnimationState animation = terminalScreenAnimations.computeIfAbsent(terminal,
				ignored -> new ScreenAnimationState());
		float renderTime = AnimationTickHolder.getRenderTime(terminal.getLevel());
		float elapsedTicks = animation.updateRenderTime(renderTime);

		if (terminal.isOnline() && elapsedTicks > 0) {
			float speed = terminal.getSpeed();
			float cyclesPerSecond = Mth.clamp(Math.abs(speed) / RPM_PER_SCREEN_CYCLE,
					0.0F, MAX_SCREEN_CYCLES_PER_SECOND);
			animation.phase += elapsedTicks * cyclesPerSecond / 20.0 * Math.signum(speed);
			animation.wrapPhase();
			animation.hasRun = true;
		}

		double creeperPulse = animation.hasRun ? temporalWave(animation.phase * CREEPER_CARRIER_HARMONIC) : 0.0;
		double cogPulse = animation.hasRun ? temporalWave(animation.phase * COG_CARRIER_HARMONIC) : 0.0;
		double highSpeedPulse = animation.hasRun ? temporalWave(animation.phase * HIGH_SPEED_CARRIER_HARMONIC) : 0.0;
		int rowOffset = Math.floorMod((int) Math.floor(animation.phase * SCREEN_HEIGHT), SCREEN_HEIGHT);

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
		poseStack.translate(-0.5, -0.5, -0.5);

		VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(CRT_PIXEL_TEXTURE));
		for (int sourceRow = 0; sourceRow < SCREEN_HEIGHT; sourceRow++) {
			int screenRow = Math.floorMod(sourceRow - rowOffset, SCREEN_HEIGHT);
			for (int column = 0; column < SCREEN_WIDTH; column++) {
				double signal = 0.0;
				int carrierCount = 0;
				if (isPatternPixel(CREEPER_PATTERN, column, screenRow)) {
					signal += creeperPulse;
					carrierCount++;
				}
				if (isPatternPixel(COG_PATTERN, column, screenRow)) {
					signal += cogPulse;
					carrierCount++;
				}
				if (isPatternPixel(HIGH_SPEED_PATTERN, column, screenRow)) {
					signal += highSpeedPulse;
					carrierCount++;
				}

				int brightness = CRT_ROW_BRIGHTNESS[sourceRow];
				if (carrierCount > 0) {
					brightness += (int) Math.round(CRT_IMAGE_AMPLITUDE * signal / carrierCount);
				}
				brightness = Mth.clamp(brightness, 8, 235);
				renderCrtPixel(poseStack, consumer, column, screenRow, brightness);
			}
		}
		poseStack.popPose();
	}

	private static boolean isPatternPixel(String[] pattern, int column, int modelRow) {
		return pattern[SCREEN_HEIGHT - 1 - modelRow].charAt(column) == '#';
	}

	private static double temporalWave(double carrierCycles) {
		return Math.cos(TWO_PI * carrierCycles);
	}

	private static void renderCrtPixel(PoseStack poseStack, VertexConsumer consumer, int column, int row,
			int brightness) {
		float x0 = (4.0F + column) / 16.0F;
		float x1 = (5.0F + column) / 16.0F;
		float y0 = (5.0F + row) / 16.0F;
		float y1 = (5.78F + row) / 16.0F;
		float z = 15.116F / 16.0F;
		float shade = brightness / 255.0F;
		PoseStack.Pose pose = poseStack.last();

		addCrtVertex(pose, consumer, x0, y1, z, 0.0F, 0.0F, shade);
		addCrtVertex(pose, consumer, x0, y0, z, 0.0F, 1.0F, shade);
		addCrtVertex(pose, consumer, x1, y0, z, 1.0F, 1.0F, shade);
		addCrtVertex(pose, consumer, x1, y1, z, 1.0F, 0.0F, shade);
	}

	private static void addCrtVertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, float shade) {
		consumer.addVertex(pose, x, y, z)
				.setColor(shade, shade, shade, 1.0F)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	private static final class ScreenAnimationState {
		private double phase;
		private boolean hasRun;
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

		private void wrapPhase() {
			phase %= PHASE_WRAP_CYCLES;
			if (phase < 0) {
				phase += PHASE_WRAP_CYCLES;
			}
		}
	}
}
