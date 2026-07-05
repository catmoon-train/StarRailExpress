package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.EntityTypeRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.content.entity.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public interface TMMEntities {
    EntityTypeRegistrar registrar = new EntityTypeRegistrar(SRE.TMM_MOD_ID);

    EntityType<SeatEntity> SEAT = registrar.create("seat", EntityType.Builder.of(SeatEntity::new, MobCategory.MISC)
            .sized(1f, 1f)
            .clientTrackingRange(128)
            .noSummon()
    );

    EntityType<PlayerBodyEntity> PLAYER_BODY = registrar.create("player_body", EntityType.Builder.of(PlayerBodyEntity::new, MobCategory.MISC)
            .sized(1f, 0.25f)
            .clientTrackingRange(128)
    );
    EntityType<FirecrackerEntity> FIRECRACKER = registrar.create("firecracker", EntityType.Builder.of(FirecrackerEntity::new, MobCategory.MISC)
            .sized(.2f, .2f)
            .clientTrackingRange(128)
    );
    EntityType<GrenadeEntity> GRENADE = registrar.create("grenade", EntityType.Builder.of(GrenadeEntity::new, MobCategory.MISC)
            .sized(.2f, .2f)
            .clientTrackingRange(128)
    );
    EntityType<NoteEntity> NOTE = registrar.create("note", EntityType.Builder.of(NoteEntity::new, MobCategory.MISC)
            .sized(.45f, .45f)
            .clientTrackingRange(128)
    );
    EntityType<StickyGrenadeEntity> STICKY_GRENADE = registrar.create("sticky_grenade",
            EntityType.Builder.of(StickyGrenadeEntity::new, MobCategory.MISC)
                    .sized(.2f, .2f)
                    .clientTrackingRange(128)
    );
    EntityType<TimedGrenadeEntity> TIMED_GRENADE = registrar.create("timed_grenade",
            EntityType.Builder.of(TimedGrenadeEntity::new, MobCategory.MISC)
                    .sized(.2f, .2f)
                    .clientTrackingRange(128)
    );

    EntityType<ZiplineRiderEntity> ZIPLINE_RIDER = registrar.create("zipline_rider",
            EntityType.Builder.of(ZiplineRiderEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .noSummon()
    );

    static void initialize() {
        registrar.registerEntries();

        FabricDefaultAttributeRegistry.register(PLAYER_BODY, PlayerBodyEntity.createAttributes());
    }
}
