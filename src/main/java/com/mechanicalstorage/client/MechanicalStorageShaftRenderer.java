package com.mechanicalstorage.client;

import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class MechanicalStorageShaftRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T> {
	public MechanicalStorageShaftRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(T be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		if (!be.hasLevel()) {
			return;
		}

		Direction front = be.getBlockState().getValue(DirectionalMachineBlock.FACING);
		Direction shaftDirection = front.getOpposite();
		SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), shaftDirection);
		int rearLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(shaftDirection));
		KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, rearLight)
				.renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));
	}
}
