package com.valhalla.command.entity;

import com.valhalla.command.ValhallaCommand;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
	public static final ResourceKey<EntityType<?>> VIKING_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "viking"));

	public static final EntityType<VikingEntity> VIKING = Registry.register(BuiltInRegistries.ENTITY_TYPE, VIKING_KEY,
			EntityType.Builder.<VikingEntity>of(VikingEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(10)
					.build(VIKING_KEY));

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(VIKING, VikingEntity.createAttributes());
	}
}
