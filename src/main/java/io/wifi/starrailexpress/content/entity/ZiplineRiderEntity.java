package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.content.block.ZiplineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ZiplineRiderEntity extends Entity {
    private static final float RIDE_SPEED = 0.05f;
    private static final double MAX_DISTANCE = 4.0;
    private static final double RIDER_VERTICAL_OFFSET = -1.15;

    @Nullable
    private BlockPos startPos;
    @Nullable
    private BlockPos endPos;
    @Nullable
    private UUID riderId;
    private float progress = 0f;
    private int rideTick = 0;

    public ZiplineRiderEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        NbtUtils.readBlockPos(tag, "StartPos").ifPresent(pos -> this.startPos = pos);
        NbtUtils.readBlockPos(tag, "EndPos").ifPresent(pos -> this.endPos = pos);
        if (tag.hasUUID("Rider")) {
            this.riderId = tag.getUUID("Rider");
        }
        this.progress = tag.getFloat("Progress");
        this.rideTick = tag.getInt("RideTick");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (startPos != null) tag.put("StartPos", NbtUtils.writeBlockPos(startPos));
        if (endPos != null) tag.put("EndPos", NbtUtils.writeBlockPos(endPos));
        if (riderId != null) tag.putUUID("Rider", riderId);
        tag.putFloat("Progress", progress);
        tag.putInt("RideTick", rideTick);
    }

    public void setStartAndEnd(BlockPos start, BlockPos end, Player rider) {
        this.startPos = start;
        this.endPos = end;
        this.riderId = rider.getUUID();
        this.progress = 0f;
        this.setPos(ZiplineBlock.ropePoint(start, end, 0.0f));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (startPos == null || endPos == null || riderId == null) {
            this.discard();
            return;
        }

        // 玩家已下车
        if (!this.isVehicle()) {
            this.discard();
            return;
        }

        Vec3 ropePos = ZiplineBlock.ropePoint(startPos, endPos, progress);

        // 脱离检测
        Entity passenger = this.getFirstPassenger();
        if (!(passenger instanceof Player rider) || !rider.isAlive() || rider.isSpectator()
                || !rider.getUUID().equals(riderId)) {
            this.ejectPassengers();
            this.discard();
            return;
        }

        // 强制传送 / 距离过远
        if (rider.position().distanceTo(ropePos) > MAX_DISTANCE) {
            this.ejectPassengers();
            this.discard();
            return;
        }

        // 移动实体
        this.setPos(ropePos);

        // 手动同步乘客位置（setPos 不会自动触发 positionRider）
        Vec3 riderPos = this.getPassengerRidingPosition(rider);
        rider.setPos(riderPos.x, riderPos.y, riderPos.z);
        rider.setDeltaMovement(Vec3.ZERO);
        rider.fallDistance = 0f;

        // 音效
        if (rideTick % 5 == 0) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.3f, 1.2f);
        }

        // 更新进度
        progress += RIDE_SPEED;
        if (progress >= 1.0f) {
            progress = 1.0f;
            Vec3 endRopePos = ZiplineBlock.ropePoint(startPos, endPos, 1.0f);
            this.setPos(endRopePos);
            Vec3 endRiderPos = this.getPassengerRidingPosition(rider);
            rider.setPos(endRiderPos.x, endRiderPos.y, endRiderPos.z);
            this.ejectPassengers();
            this.discard();
            return;
        }

        // 朝向
        Vec3 endRopePos = ZiplineBlock.ropePoint(startPos, endPos, 1.0f);
        Vec3 lookTarget = endRopePos.subtract(ropePos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-lookTarget.x, lookTarget.z));
        float pitch = (float) Math.toDegrees(Math.asin(-lookTarget.y));
        rider.setYRot(yaw);
        rider.setXRot(pitch);

        rideTick++;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(0, RIDER_VERTICAL_OFFSET, 0);
    }

    @Override
    public Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0, RIDER_VERTICAL_OFFSET, 0);
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return false;
    }
}
