package com.mechanicalstorage.client;

import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

public class MechanicalStorageShaftRenderer<T extends KineticBlockEntity> extends KineticBlockEntityRenderer<T> {
	public MechanicalStorageShaftRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(T be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		if (VisualizationManager.supportsVisualization(be.getLevel())) {
			return;
		}

		Direction front = be.getBlockState().getValue(DirectionalMachineBlock.FACING);
		Direction shaftDirection = front.getOpposite();
		Axis shaftAxis = shaftDirection.getAxis();
		BlockPos pos = be.getBlockPos();

		SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), shaftDirection);
		float time = AnimationTickHolder.getRenderTime(be.getLevel());
		float angle = (time * be.getSpeed() * 3f / 10) % 360;
		angle += getRotationOffsetForPosition(be, pos, shaftAxis);
		angle = angle / 180f * (float) Math.PI;

		kineticRotationTransform(shaft, be, shaftAxis, angle, light);
		shaft.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
	}
}
