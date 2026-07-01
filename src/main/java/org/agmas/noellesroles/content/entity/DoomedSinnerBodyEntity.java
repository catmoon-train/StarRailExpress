package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class DoomedSinnerBodyEntity extends PlayerBodyEntity {
    public DoomedSinnerBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    public static boolean isDoomedSinnerBody(Object entity) {
        return entity instanceof DoomedSinnerBodyEntity;
    }
}
