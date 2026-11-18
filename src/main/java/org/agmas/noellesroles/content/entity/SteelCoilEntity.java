package org.agmas.noellesroles.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * 钢卷实体：金属怪一技能的伪装形态，尺寸固定为 2×2×2 格。
 *
 * <p>本实体主要作为 {@code EntityDisguise} 的渲染载体：玩家本体碰撞箱保持不变（能过一格宽道路），
 * 只有外观换成钢卷。滚动角 {@link #getRoll()} 由客户端按玩家实际位移推进，
 * 滚动动画因此始终与金属怪的速度一致。
 */
public class SteelCoilEntity extends Entity {

    /** 钢卷的固定尺寸（格）。 */
    public static final float SIZE = 2.0F;

    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData
            .defineId(SteelCoilEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData
            .defineId(SteelCoilEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData
            .defineId(SteelCoilEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public SteelCoilEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_SPEED, 0.0F);
        builder.define(DATA_OWNER, Optional.empty());
    }

    /** 当前滚动角（度）。 */
    public float getRoll() {
        return this.entityData.get(DATA_ROLL);
    }

    public void setRoll(float roll) {
        this.entityData.set(DATA_ROLL, roll % 360.0F);
    }

    /** 当前滚动速度（格 / tick），仅用于渲染表现。 */
    public float getRollSpeed() {
        return this.entityData.get(DATA_SPEED);
    }

    public void setRollSpeed(float speed) {
        this.entityData.set(DATA_SPEED, Math.max(0.0F, speed));
    }

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID owner) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRoll(tag.getFloat("Roll"));
        setRollSpeed(tag.getFloat("Speed"));
        this.entityData.set(DATA_OWNER,
                tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Roll", getRoll());
        tag.putFloat("Speed", getRollSpeed());
        UUID owner = getOwnerId();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
