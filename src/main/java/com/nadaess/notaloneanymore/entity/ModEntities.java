package com.nadaess.notaloneanymore.entity;

import com.nadaess.notaloneanymore.Notaloneanymore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class ModEntities {

    public static final Identifier COMPANION_ID = Identifier.fromNamespaceAndPath(Notaloneanymore.MOD_ID, "companion");
    public static final ResourceKey<EntityType<?>> COMPANION_KEY = ResourceKey.create(Registries.ENTITY_TYPE, COMPANION_ID);

    public static final EntityType<CompanionEntity> COMPANION = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            COMPANION_ID,
            EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.98F, 0.98F) // кубик как TNT-блок
                    .eyeHeight(0.85F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(COMPANION_KEY)
    );

    public static void register() {
        Notaloneanymore.LOGGER.info("Registering entity {}", COMPANION_ID);
        // Атрибуты — копия базовых моба
        AttributeSupplier.Builder builder = CompanionEntity.createAttributes();
        FabricDefaultAttributeRegistry.register(COMPANION, builder);
    }

    // Вызывается из CompanionEntity.createAttributes()
    static AttributeSupplier.Builder createDefaultAttributes() {
        return CompanionEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }
}
