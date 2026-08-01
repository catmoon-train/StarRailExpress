/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * 幽露「不请自来」锚点实体。
 *
 * <p>幽露使用锚点物品后生成：一颗贴地滑行的幽蓝小球，沿放置时的朝向匀速前进，
 * 受重力下落、撞墙后停在原地。仅对幽露本人渲染（见 {@code YouluAnchorRenderer}）。
 * 再次使用锚点物品时幽露会传送到本实体位置。
 *
 * <ul>
 *   <li>寿命耗尽 / 拥有者死亡或掉线 / 游戏结束时静默消散。</li>
 *   <li>无碰撞体积、不可被攻击、不推挤实体。</li>
 * </ul>
 */
public class YouluAnchorEntity extends Entity {

    /** 拥有者 UUID（同步给客户端用于"仅拥有者可见"的渲染判断）。 */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            YouluAnchorEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 每 tick 的重力加速度。 */
    private static final double GRAVITY = 0.06D;

    /** 初始朝向（沿此方向水平滑行）。 */
    private float forwardYaw = 0.0F;
    /** 滑行速度（格/tick）。 */
    private double speed = 0.25D;
    /** 撞墙后停止滑行。 */
    private boolean stopped = false;
    private int remainingLifetime = 60 * 20;

    public YouluAnchorEntity(EntityType<? extends YouluAnchorEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    /** 初始化：拥有者、朝向、速度与寿命。 */
    public void setup(UUID ownerUuid, float yaw, double speed, int lifetimeTicks) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
        this.forwardYaw = yaw;
        this.speed = speed;
        this.remainingLifetime = lifetimeTicks;
        this.setYRot(yaw);
    }

    public UUID getOwnerUuid() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    /** 是否对该玩家可见（仅拥有者）。 */
    public boolean isVisibleTo(Player viewer) {
        UUID owner = getOwnerUuid();
        return owner != null && owner.equals(viewer.getUUID());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (gameWorld == null || !gameWorld.isRunning()) {
            discard();
            return;
        }
        UUID owner = getOwnerUuid();
        Player ownerPlayer = owner != null ? level().getPlayerByUUID(owner) : null;
        if (ownerPlayer == null || !ownerPlayer.isAlive() || ownerPlayer.isSpectator()) {
            discard();
            return;
        }
        if (--remainingLifetime <= 0) {
            discard();
            return;
        }

        // 贴地滑行：水平匀速 + 重力下落，撞墙后沿墙面滑行（蹭墙）
        double vy = getDeltaMovement().y - GRAVITY;
        double dx = 0, dz = 0;
        if (!stopped) {
            double rad = Math.toRadians(forwardYaw);
            dx = -Math.sin(rad) * speed;
            dz = Math.cos(rad) * speed;
        }
        setDeltaMovement(dx, vy, dz);

        double oldX = getX();
        double oldZ = getZ();
        move(MoverType.SELF, getDeltaMovement());

        // 蹭墙逻辑：撞墙后检测被阻挡的轴向，沿未阻挡方向滑行
        if (horizontalCollision && !stopped) {
            double actualDx = getX() - oldX;
            double actualDz = getZ() - oldZ;

            boolean blockedX = Math.abs(actualDx) < Math.abs(dx) * 0.3 && Math.abs(dx) > 0.001;
            boolean blockedZ = Math.abs(actualDz) < Math.abs(dz) * 0.3 && Math.abs(dz) > 0.001;

            double slideDx = 0, slideDz = 0;
            if (blockedX && !blockedZ) {
                // X 轴被墙阻挡 → 沿 Z 方向滑行
                slideDz = dz > 0 ? speed : -speed;
                forwardYaw = dz > 0 ? 0 : 180;
            } else if (blockedZ && !blockedX) {
                // Z 轴被墙阻挡 → 沿 X 方向滑行
                slideDx = dx > 0 ? speed : -speed;
                forwardYaw = dx > 0 ? -90 : 90;
            }

            if (blockedX != blockedZ) {
                // 单轴被挡 → 同 tick 沿墙面滑行
                setYRot(forwardYaw);
                setDeltaMovement(slideDx, getDeltaMovement().y, slideDz);
                move(MoverType.SELF, getDeltaMovement());
            } else {
                // 双轴都挡（角落）→ 停止
                stopped = true;
            }
        }

        if (onGround()) {
            setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        forwardYaw = tag.getFloat("ForwardYaw");
        speed = tag.getDouble("Speed");
        stopped = tag.getBoolean("Stopped");
        remainingLifetime = tag.getInt("RemainingLifetime");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("OwnerUUID", owner);
        }
        tag.putFloat("ForwardYaw", forwardYaw);
        tag.putDouble("Speed", speed);
        tag.putBoolean("Stopped", stopped);
        tag.putInt("RemainingLifetime", remainingLifetime);
    }
}
