package org.agmas.noellesroles.content.block_entity.scene;

import java.util.UUID;

import org.agmas.noellesroles.content.block.scene.MovingPlatformBlock;
import org.agmas.noellesroles.content.entity.MovingPlatformEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 移动方块底座方块实体：确保其管理的移动平台实体始终存在（按 UUID 跟踪），底座移除时清除平台。
 * Distance NBT 字段控制移动距离（1-50格，默认5格）。
 * Speed NBT 字段控制移动速度（0.01-1.0，默认0.08）。
 * CollisionSize NBT 字段控制碰撞箱大小（0.5-3.0，默认1.0）。
 */
public class MovingPlatformBlockEntity extends BlockEntity {

    private UUID childUuid;
    private int distance = MovingPlatformBlock.DEFAULT_DISTANCE;
    private double speed = 0.08;
    private double collisionSize = 1.0;

    public MovingPlatformBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.MOVING_PLATFORM_ENTITY, pos, state);
    }

    public int getDistance() {
        return Math.clamp(distance, 1, MovingPlatformBlock.MAX_DISTANCE);
    }
    public void setDistance(int dist) {
        this.distance = Math.clamp(dist, 1, MovingPlatformBlock.MAX_DISTANCE);
        setChanged();
    }
    public double getSpeed() { return Math.clamp(speed, 0.01, 1.0); }
    public void setSpeed(double s) { this.speed = Math.clamp(s, 0.01, 1.0); setChanged(); }
    public double getCollisionSize() { return Math.clamp(collisionSize, 0.5, 3.0); }
    public void setCollisionSize(double s) { this.collisionSize = Math.clamp(s, 0.5, 3.0); setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (childUuid != null) {
            tag.putUUID("Child", childUuid);
        }
        tag.putInt("Distance", distance);
        tag.putDouble("Speed", speed);
        tag.putDouble("CollisionSize", collisionSize);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Child")) {
            childUuid = tag.getUUID("Child");
        }
        if (tag.contains("Distance")) distance = tag.getInt("Distance");
        if (tag.contains("Speed")) speed = tag.getDouble("Speed");
        if (tag.contains("CollisionSize")) collisionSize = tag.getDouble("CollisionSize");
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel && childUuid != null) {
            Entity child = serverLevel.getEntity(childUuid);
            if (child != null) {
                child.discard();
            }
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MovingPlatformBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % 20 != 0) {
            return;
        }
        Entity child = be.childUuid == null ? null : serverLevel.getEntity(be.childUuid);
        if (child == null || !child.isAlive()) {
            Direction dir = state.getValue(MovingPlatformBlock.FACING);
            MovingPlatformEntity platform = new MovingPlatformEntity(ModEntities.MOVING_PLATFORM, serverLevel);
            Vec3 home = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            platform.setup(home, dir, be.getDistance(), be.getSpeed(), be.getCollisionSize());
            serverLevel.addFreshEntity(platform);
            be.childUuid = platform.getUUID();
            be.setChanged();
        }
    }
}
