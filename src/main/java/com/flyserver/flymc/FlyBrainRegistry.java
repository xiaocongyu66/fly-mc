package com.flyserver.flymc;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Registry: the fly-brain android entity + its spawn egg. */
public final class FlyBrainRegistry {
    private FlyBrainRegistry() {}

    public static final EntityType<FlyBrainEntity> ANDROID = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("flybrain", "android"),
            EntityType.Builder.of(FlyBrainEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("android"));

    public static final Item ANDROID_SPAWN_EGG = Registry.register(
            BuiltInRegistries.ITEM,
            Identifier.fromNamespaceAndPath("flybrain", "android_spawn_egg"),
            new SpawnEggItem(ANDROID, 0x2b6d8c, 0xd7b377, new Item.Properties()));

    public static void init() {
        FabricDefaultAttributeRegistry.register(ANDROID, FlyBrainEntity.createLivingAttributes());
    }
}
