package org.agmas.noellesroles.entity;

import java.util.List;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 场地道具实体 - 继承 ItemEntity，使用原版物品作为展示。
 * 轮椅接触后根据类型产生不同效果（加速、修复、跳跃加强），类似赛车中的道具。
 */
public class WheelchairFieldItemEntity extends ItemEntity {

    /**
     * 道具效果类型
     */
    public enum EffectType {
        SPEED_BOOST(0),
        REPAIR(1),
        JUMP_BOOST(2);

        public final int id;

        EffectType(int id) {
            this.id = id;
        }

        public static EffectType fromId(int id) {
            for (EffectType type : values()) {
                if (type.id == id) return type;
            }
            return SPEED_BOOST;
        }

        public ItemStack getDisplayItem() {
            return switch (this) {
                case SPEED_BOOST -> new ItemStack(Items.SUGAR);
                case REPAIR -> new ItemStack(Items.IRON_INGOT);
                case JUMP_BOOST -> new ItemStack(Items.FEATHER);
            };
        }
    }

    private static final EntityDataAccessor<Integer> DATA_EFFECT_TYPE =
            SynchedEntityData.defineId(WheelchairFieldItemEntity.class, EntityDataSerializers.INT);

    private boolean consumed = false;
    private int respawnTimer = 0;
    private static final int RESPAWN_DELAY = 200; // 10 秒后重新出现

    public WheelchairFieldItemEntity(EntityType<? extends ItemEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setNeverPickUp();
        this.setUnlimitedLifetime();
        this.setItem(EffectType.SPEED_BOOST.getDisplayItem());
    }

    /**
     * 创建指定类型的场地道具
     */
    public WheelchairFieldItemEntity(EntityType<? extends ItemEntity> entityType, Level level,
                                     double x, double y, double z, EffectType effectType) {
        super(entityType, level);
        this.setPos(x, y, z);
        this.setNoGravity(true);
        this.setNeverPickUp();
        this.setUnlimitedLifetime();
        this.entityData.set(DATA_EFFECT_TYPE, effectType.id);
        this.setItem(effectType.getDisplayItem());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EFFECT_TYPE, 0);
    }

    public EffectType getEffectType() {
        return EffectType.fromId(this.entityData.get(DATA_EFFECT_TYPE));
    }

    public void setEffectType(EffectType type) {
        this.entityData.set(DATA_EFFECT_TYPE, type.id);
        this.setItem(type.getDisplayItem());
    }

    @Override
    public void tick() {
        // 不调用 super.tick() 来避免 ItemEntity 默认的拾取/合并/物理行为
        this.baseTick();

        if (this.level().isClientSide) {
            return;
        }

        // 已被消耗，等待重生
        if (consumed) {
            respawnTimer--;
            if (respawnTimer <= 0) {
                consumed = false;
                this.setInvisible(false);
            }
            return;
        }

        // 检测轮椅碰撞
        List<WheelchairEntity> wheelchairs = this.level().getEntitiesOfClass(
                WheelchairEntity.class, this.getBoundingBox().inflate(0.5));
        for (WheelchairEntity wheelchair : wheelchairs) {
            if (wheelchair.getControllingPassenger() instanceof Player) {
                applyEffect(wheelchair);
                consumed = true;
                respawnTimer = RESPAWN_DELAY;
                this.setInvisible(true);
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                break;
            }
        }
    }

    /**
     * 对轮椅施加道具效果
     */
    private void applyEffect(WheelchairEntity wheelchair) {
        EffectType type = getEffectType();
        switch (type) {
            case SPEED_BOOST -> wheelchair.boost();
            case REPAIR -> wheelchair.durability = Math.min(wheelchair.durability + 20, 60);
            case JUMP_BOOST -> wheelchair.boost();
        }
    }

    @Override
    public void playerTouch(Player player) {
        // 禁止玩家直接拾取
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }
}
