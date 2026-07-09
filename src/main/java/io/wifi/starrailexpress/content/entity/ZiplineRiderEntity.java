package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.content.block.ZiplineBlock;
import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ZiplineRiderEntity extends Entity {
    // 恒定线速度（格/tick），进度增量按段长换算，长短段体感速度一致
    private static final float BLOCKS_PER_TICK = 0.45f;
    // 单 tick 进度上限：极短的段至少 5 tick 走完，避免瞬移
    private static final float MAX_PROGRESS_STEP = 0.2f;
    private static final double MAX_DISTANCE = 8.0;
    private static final double RIDER_VERTICAL_OFFSET = -1.15;
    // 视为"直行延续"的方向点积阈值
    private static final double STRAIGHT_DOT = 0.9;

    @Nullable
    private BlockPos startPos;
    @Nullable
    private BlockPos endPos;
    @Nullable
    private UUID riderId;
    private float progress = 0f;
    private int rideTick = 0;

    // 客户端位置插值
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private int lerpSteps;

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
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = Math.max(1, steps);
    }

    private void tickLerp() {
        if (this.lerpSteps > 0) {
            this.setPos(
                    this.getX() + (this.lerpX - this.getX()) / this.lerpSteps,
                    this.getY() + (this.lerpY - this.getY()) / this.lerpSteps,
                    this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps);
            this.lerpSteps--;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            tickLerp();
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

        // 玩家被外力传送走等异常情况
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

        // 更新进度：按段长换算成恒定线速度
        progress += progressStep(startPos, endPos);
        if (progress >= 1.0f) {
            // 到达当前柱子：优先沿路线接续下一段，走不通才落地
            BlockPos next = findNextSegment(endPos, startPos, rider);
            if (next != null) {
                BlockPos reached = endPos;
                this.startPos = reached;
                this.endPos = next;
                this.progress = 0f;
                Vec3 jointPos = ZiplineBlock.ropePoint(reached, next, 0.0f);
                this.setPos(jointPos);
                Vec3 jointRiderPos = this.getPassengerRidingPosition(rider);
                rider.setPos(jointRiderPos.x, jointRiderPos.y, jointRiderPos.z);
                rideTick++;
                return;
            }

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

    private float progressStep(BlockPos start, BlockPos end) {
        double length = Vec3.atCenterOf(start).distanceTo(Vec3.atCenterOf(end));
        if (length < 1.0e-3) {
            return MAX_PROGRESS_STEP;
        }
        return Mth.clamp((float) (BLOCKS_PER_TICK / length), 1.0e-4f, MAX_PROGRESS_STEP);
    }

    /**
     * 到达柱子后寻找可接续的下一段：优先直行；拐角处若只有一条其他连接则跟随；
     * 岔路口跟随玩家视线，视线里没有任何向前的分支才停下。
     */
    @Nullable
    private BlockPos findNextSegment(BlockPos from, BlockPos cameFrom, Player rider) {
        if (!(this.level().getBlockState(from).getBlock() instanceof ZiplineBlock)) {
            return null;
        }
        if (!(this.level().getBlockEntity(from) instanceof ZiplineBlockEntity zbe)) {
            return null;
        }

        Vec3 travel = Vec3.atCenterOf(from).subtract(Vec3.atCenterOf(cameFrom));
        if (travel.lengthSqr() < 1.0e-6) {
            return null;
        }
        travel = travel.normalize();

        BlockPos straight = null;
        double bestDot = STRAIGHT_DOT;
        List<BlockPos> others = new ArrayList<>();
        for (BlockPos conn : zbe.getConnectedPositions()) {
            if (conn.equals(cameFrom)) {
                continue;
            }
            if (!(this.level().getBlockState(conn).getBlock() instanceof ZiplineBlock)) {
                continue;
            }
            others.add(conn);
            double dot = travel.dot(direction(from, conn));
            if (dot > bestDot) {
                bestDot = dot;
                straight = conn;
            }
        }
        if (straight != null) {
            return straight;
        }
        if (others.size() <= 1) {
            return others.isEmpty() ? null : others.get(0);
        }

        Vec3 look = rider.getLookAngle();
        BlockPos chosen = null;
        double bestLookDot = 0.0;
        for (BlockPos conn : others) {
            double dot = look.dot(direction(from, conn));
            if (dot > bestLookDot) {
                bestLookDot = dot;
                chosen = conn;
            }
        }
        return chosen;
    }

    private static Vec3 direction(BlockPos from, BlockPos to) {
        return Vec3.atCenterOf(to).subtract(Vec3.atCenterOf(from)).normalize();
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
