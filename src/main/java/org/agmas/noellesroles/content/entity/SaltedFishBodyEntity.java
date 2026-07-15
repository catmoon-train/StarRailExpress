package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 咸鱼「晒咸鱼」时的假尸体。
 *
 * <p>位置每 tick 由 {@code SaltedFishPlayerComponent} 同步到本体，因此这里关闭重力、
 * 且不可被推动，避免假尸体因自身重力下落或被其他实体挤开而与本体分离。
 */
public class SaltedFishBodyEntity extends PlayerBodyEntity {
    public SaltedFishBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(true);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // 不推挤其他实体，也不被推挤（配合 isPushable=false 保持与本体位置一致）
    }
}
