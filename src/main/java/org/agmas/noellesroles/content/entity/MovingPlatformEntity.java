package org.agmas.noellesroles.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModSceneBlocks;

import java.util.List;

/**
 * 移动平台实体：在起点与终点之间往返，玩家可站在其上并被带动。可被实体碰撞（可站立）。
 */
public class MovingPlatformEntity extends Entity {

    private double homeX;
    private double homeY;
    private double homeZ;
    private double dirX;
    private double dirZ;
    private double distance = 5.0;
    private double speed = 0.08;
    private double progress = 0.0;
    private boolean forward = true;

    public MovingPlatformEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** 设置往返路径：从 home 沿 dir 方向移动 distance 格，速度 speed，碰撞箱尺寸 collisionSize。 */
    public void setup(Vec3 home, Direction dir, double distance, double speed, double collisionSize) {
        this.homeX = home.x;
        this.homeY = home.y;
        this.homeZ = home.z;
        this.dirX = dir.getStepX();
        this.dirZ = dir.getStepZ();
        this.distance = distance;
        this.speed = speed;
        double half = collisionSize / 2.0;
        this.setBoundingBox(new AABB(-half, 0, -half, half, 0.2, half));
        this.setPos(home.x, home.y, home.z);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.homeX = tag.getDouble("HomeX");
        this.homeY = tag.getDouble("HomeY");
        this.homeZ = tag.getDouble("HomeZ");
        this.dirX = tag.getDouble("DirX");
        this.dirZ = tag.getDouble("DirZ");
        this.distance = tag.getDouble("Distance");
        this.speed = tag.contains("Speed") ? tag.getDouble("Speed") : 0.08;
        this.progress = tag.getDouble("Progress");
        this.forward = tag.getBoolean("Forward");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("HomeX", this.homeX);
        tag.putDouble("HomeY", this.homeY);
        tag.putDouble("HomeZ", this.homeZ);
        tag.putDouble("DirX", this.dirX);
        tag.putDouble("DirZ", this.dirZ);
        tag.putDouble("Distance", this.distance);
        tag.putDouble("Speed", this.speed);
        tag.putDouble("Progress", this.progress);
        tag.putBoolean("Forward", this.forward);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (!hasBaseBlock()) {
            this.discard();
            return;
        }

        this.progress += this.speed * (this.forward ? 1 : -1);
        if (this.progress >= this.distance) {
            this.progress = this.distance;
            this.forward = false;
        } else if (this.progress <= 0) {
            this.progress = 0;
            this.forward = true;
        }

        double nx = this.homeX + this.dirX * this.progress;
        double nz = this.homeZ + this.dirZ * this.progress;
        Vec3 delta = new Vec3(nx - this.getX(), 0, nz - this.getZ());
        this.setPos(nx, this.homeY, nz);

        // 带动站在平台上的实体
        AABB top = this.getBoundingBox().expandTowards(0, 1.0, 0).inflate(0.05, 0, 0.05);
        List<Entity> riders = this.level().getEntities(this, top,
                e -> e != this && e.getY() >= this.getBoundingBox().maxY - 0.25);
        for (Entity rider : riders) {
            if (rider instanceof Player) {
                rider.push(delta.x, 0, delta.z);
                rider.hurtMarked = true;
            } else {
                rider.setPos(rider.getX() + delta.x, rider.getY(), rider.getZ() + delta.z);
            }
        }
    }

    private boolean hasBaseBlock() {
        BlockPos basePos = BlockPos.containing(this.homeX, this.homeY - 1.0, this.homeZ);
        return this.level().getBlockState(basePos).is(ModSceneBlocks.MOVING_PLATFORM);
    }
}
