package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.content.block.ZiplineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ZiplineRiderEntity extends Entity {
    private static final float RIDE_SPEED = 0.05f; // 滑动速度（每 tick 的比例，0~1）
    private static final double MAX_DISTANCE = 4.0; // 脱离距离阈值
    private static final double RIDER_VERTICAL_OFFSET = -1.15;

    @Nullable
    private BlockPos startPos;
    @Nullable
    private BlockPos endPos;
    @Nullable
    private UUID riderId;
    private float progress = 0f; // 0~1，滑动进度
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

        Player rider = this.level().getPlayerByUUID(riderId);
        if (rider == null || !rider.isAlive() || rider.isSpectator()) {
            this.discard();
            return;
        }

        Vec3 currentRopePos = ZiplineBlock.ropePoint(startPos, endPos, progress);
        this.setPos(currentRopePos);
        if (rider.position().distanceTo(currentRopePos) > MAX_DISTANCE) {
            this.discard();
            return;
        }

        // 音效 - 每 5 tick 播放一次滑动音效
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
            moveRider(rider, endRopePos);
            this.discard();
            return;
        }

        Vec3 endRopePos = ZiplineBlock.ropePoint(startPos, endPos, 1.0f);
        Vec3 nextRopePos = ZiplineBlock.ropePoint(startPos, endPos, progress);
        this.setPos(nextRopePos);
        moveRider(rider, nextRopePos);

        Vec3 lookTarget = endRopePos.subtract(nextRopePos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-lookTarget.x, lookTarget.z));
        float pitch = (float) Math.toDegrees(Math.asin(-lookTarget.y));
        rider.setYRot(yaw);
        rider.setXRot(pitch);

        rideTick++;
    }

    private void moveRider(Player rider, Vec3 ropePos) {
        Vec3 riderPos = ropePos.add(0, RIDER_VERTICAL_OFFSET, 0);
        rider.setDeltaMovement(Vec3.ZERO);
        rider.setPos(riderPos.x, riderPos.y, riderPos.z);
        rider.hurtMarked = true;
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return false;
    }
}
