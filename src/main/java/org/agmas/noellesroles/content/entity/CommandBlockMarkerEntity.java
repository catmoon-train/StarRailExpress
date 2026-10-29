package org.agmas.noellesroles.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** 天上的命令方块。客户端画方块模型，避免方块展示的零碰撞箱和视距把模型裁掉。 */
public class CommandBlockMarkerEntity extends Entity {
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData
            .defineId(CommandBlockMarkerEntity.class, EntityDataSerializers.INT);

    public CommandBlockMarkerEntity(EntityType<? extends CommandBlockMarkerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STAGE, 0);
    }

    public void setStage(int stage) {
        this.entityData.set(STAGE, Math.max(0, Math.min(3, stage)));
    }

    public int getStage() {
        return this.entityData.get(STAGE);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    public int getTeamColor() {
        return 0xB44DFF;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(28.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0D * 1024.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Stage")) {
            setStage(tag.getInt("Stage"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Stage", getStage());
    }
}
