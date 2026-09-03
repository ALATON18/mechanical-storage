package com.mechanicalstorage.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Datapack condition used to remove recipes for disabled blocks entirely.
 */
public record BlockEnabledCondition(String block) implements ICondition {
	public static final MapCodec<BlockEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("block").forGetter(BlockEnabledCondition::block)
	).apply(instance, BlockEnabledCondition::new));

	@Override
	public boolean test(IContext context) {
		return MechanicalStorageConfig.isBlockEnabled(block);
	}

	@Override
	public MapCodec<? extends ICondition> codec() {
		return CODEC;
	}
}
