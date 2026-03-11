package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

/**
 * 诱饵弹实体
 * - 落地时不会产生粒子效果
 * - 在落地处发生5声左轮手枪射击的声音（时间间隔不一）
 */
public class DecoyGrenadeEntity extends ThrowableItemProjectile {

    // 射击次数：5次
    private static final int SHOOT_COUNT = 5;
    // 最小间隔：0.4秒（8 ticks）
    private static final int MIN_DELAY = 8;
    // 最大间隔：1.2秒（24 ticks）
    private static final int MAX_DELAY = 24;

    private int shootTimer = 0;
    private int shotsFired = 0;
    private int nextShootDelay = 0;

    public DecoyGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DECOY_GRENADE;
    }

    @Override
    protected void onHit(net.minecraft.world.phys.HitResult hitResult) {
        // 调用父类方法以处理碰撞，使实体停止
        super.onHit(hitResult);

        if (!this.level().isClientSide) {
            // 设置第一次射击延迟
            nextShootDelay = MIN_DELAY + this.random.nextInt(MAX_DELAY - MIN_DELAY);
            shootTimer = nextShootDelay;
            shotsFired = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 只在服务端处理射击逻辑
        if (!this.level().isClientSide && shotsFired < SHOOT_COUNT) {
            shootTimer--;

            if (shootTimer <= 0) {
                // 播放左轮手枪射击声
                this.level().playSound(null, this.blockPosition(),
                        TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS,
                        1.0f, 1.0f);

                shotsFired++;

                // 如果还没达到射击次数，设置下一次射击延迟
                if (shotsFired < SHOOT_COUNT) {
                    nextShootDelay = MIN_DELAY + this.random.nextInt(MAX_DELAY - MIN_DELAY);
                    shootTimer = nextShootDelay;
                } else {
                    // 所有射击完成后移除实体
                    this.discard();
                }
            }
        } else if (shotsFired >= SHOOT_COUNT && !this.level().isClientSide) {
            // 确保实体被移除
            this.discard();
        }
    }
}
