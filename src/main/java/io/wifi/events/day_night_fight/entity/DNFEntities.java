package io.wifi.events.day_night_fight.entity;

import dev.doctor4t.ratatouille.util.registrar.EntityTypeRegistrar;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public interface DNFEntities {
    EntityTypeRegistrar registrar = new EntityTypeRegistrar(SRE.TMM_MOD_ID);

    EntityType<UnderworldMonsterEntity> UNDERWORLD_MONSTER = registrar.create("underworld_monster", 
            EntityType.Builder.of(UnderworldMonsterEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(128)
    );
    EntityType<DNFTaskPointEntity> TASK_POINT = registrar.create("dnf_task_point",
            EntityType.Builder.of(DNFTaskPointEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(64)
    );

    static void initialize() {
        registrar.registerEntries();
        FabricDefaultAttributeRegistry.register(UNDERWORLD_MONSTER, UnderworldMonsterEntity.createAttributes());
    }
}
