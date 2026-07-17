package net.exmo.sre.sixtyseconds.init;

import net.exmo.sre.sixtyseconds.entity.OceanCreatureEntity;
import net.exmo.sre.sixtyseconds.entity.OceanSeaMonsterEntity;
import net.exmo.sre.sixtyseconds.entity.OceanSharkEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import org.agmas.noellesroles.Noellesroles;

/**
 * 海洋生物实体类型注册表（由 {@code org.agmas.noellesroles.init.ModEntities} 引用）。
 */
public final class ModOceanEntities {

    @SuppressWarnings("deprecation")
    public static final EntityType<OceanSharkEntity> OCEAN_SHARK = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("ocean_shark"),
            net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder
                    .<OceanSharkEntity>create(MobCategory.WATER_CREATURE, OceanSharkEntity::new)
                    .dimensions(EntityDimensions.fixed(1.2F, 1.2F))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(2)
                    .build());

    @SuppressWarnings("deprecation")
    public static final EntityType<OceanSeaMonsterEntity> OCEAN_SEA_MONSTER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("ocean_sea_monster"),
            net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder
                    .<OceanSeaMonsterEntity>create(MobCategory.WATER_CREATURE,
                            OceanSeaMonsterEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0F, 2.5F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /** 注册属性（由 ModEntities.init 调用）。 */
    public static void initAttributes() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
                .register(OCEAN_SHARK, OceanSharkEntity.createMobAttributes()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 40.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.30)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 8.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 32.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.3)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.SCALE, 2.5));
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
                .register(OCEAN_SEA_MONSTER, OceanSeaMonsterEntity.createMobAttributes()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 600.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.16)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 35.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 48.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.SCALE, 10.0));
    }

    private ModOceanEntities() {}
}
