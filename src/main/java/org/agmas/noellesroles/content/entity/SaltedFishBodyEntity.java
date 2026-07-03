package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class SaltedFishBodyEntity extends PlayerBodyEntity {
    public SaltedFishBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }
}
