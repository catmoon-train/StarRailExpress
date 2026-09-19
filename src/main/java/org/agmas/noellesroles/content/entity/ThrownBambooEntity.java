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

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 投掷竹子：水平直线飞行，途中最多将 2 名玩家挂在竹子上；撞墙后钉在墙上不再位移，
 * 从发射起 12 秒后（含钉墙时间）统一消失。
 * <p>
 * 防卡墙：挂人位置 / 下竹位置都会做碰撞检测并检查与竹子本体之间无墙阻挡，
 * 找不到安全位就落地放人，绝不把人传送到墙对面。
 */
public class ThrownBambooEntity extends AbstractArrow {

    public static final int MAX_HANG = 2;
    /** 从发射到消失的总时长（含钉在墙上的时间）。 */
    public static final int LIFETIME_TICKS = 20 * 12;
    /** 发射速度。 */
    public static final float THROW_SPEED = 2.04F;

    /** 渲染用：竹杆枪尖相对实体原点向前的距离（需与 ThrownBambooRenderer 保持一致）。 */
    public static final float TIP_REACH = 1.75F;
    /** 渲染用：竹杆尾端相对实体原点向后的距离。 */
    public static final float TAIL_REACH = 0.45F;
    /** 钉墙时枪尖扎进墙体的深度。 */
    private static final float PIN_EMBED = 0.6F;

    private static final double HANG_FORWARD = 0.15;
    private static final double HANG_SIDE = 0.35;
    private static final double HANG_UP = 0.15;

    /** 挂人判定的整杆扫掠半径（比原版箭矢命中盒大得多，好命中）。 */
    private static final double HIT_INFLATE = 0.4;

    /** 渲染朝向：显式同步的水平 yaw，不依赖原版箭矢的旋转插值。 */
    private static final EntityDataAccessor<Float> FACING_YAW = SynchedEntityData.defineId(ThrownBambooEntity.class,
            EntityDataSerializers.FLOAT);

    private final List<UUID> hungPlayers = new ArrayList<>(2);

    /** 是否已钉在墙上。 */
    private boolean pinned;
    private Vec3 pinPos = Vec3.ZERO;
    private float pinYaw;

    /** 客户端平滑朝向。 */
    private float renderYaw;
    private float prevRenderYaw;

    public ThrownBambooEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setBaseDamage(0);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    public ThrownBambooEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity livingEntity, Level level,
            ItemStack itemStack) {
        super(entityType, livingEntity, level, itemStack, null);
        this.setNoGravity(true);
        this.setBaseDamage(0);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    /** 设置投掷方向：同步朝向并摆正实体旋转（只用于水平飞行）。 */
    public void setThrownDirection(Vec3 horizontalDirection) {
        float yaw = yawFrom(horizontalDirection);
        this.entityData.set(FACING_YAW, yaw);
        this.setYRot(yaw);
        this.setXRot(0.0F);
        this.yRotO = yaw;
        this.xRotO = 0.0F;
    }


    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FACING_YAW, 0.0F);
    }

    public float getFacingYaw() {
        return this.entityData.get(FACING_YAW);
    }

    /** 客户端插值后的渲染朝向。 */
    public float getRenderYaw(float partialTick) {
        return Mth.lerp(partialTick, this.prevRenderYaw, this.renderYaw);
    }

    // ------------------------------------------------------------------ 基础行为

    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }

    @Override
    protected float getWaterInertia() {
        return 1F;
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** 不让它被原版的 60 秒地面计时器清掉，存活时间完全由 LIFETIME_TICKS 决定。 */
    @Override
    protected void tickDespawn() {
    }

    // ------------------------------------------------------------------ 挂人

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < MAX_HANG && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = this.getPassengers().indexOf(passenger);
        double side = index <= 0 ? HANG_SIDE : -HANG_SIDE;
        Vec3 forward = this.forward();
        Vec3 right = rightOf(forward);
        // 钉墙后把人挂在竹杆后方（投掷者一侧），避免穿到墙对面。
        double along = this.pinned ? -0.95 : HANG_FORWARD;
        return forward.scale(along).add(right.scale(side)).add(0.0, HANG_UP, 0.0);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        Vec3 target = this.getPassengerRidingPosition(passenger).subtract(this.getVehicleAttachmentPoint(passenger));
        if (passenger instanceof Player) {
            Vec3 safe = this.findFreeSpot(passenger, target);
            if (safe != null) {
                target = safe;
            } else if (!this.pinned) {
                // 飞行中附近没空位：先贴着竹子本体，下一 tick 再检查；仍会卡墙就放下来。
                moveFunction.accept(passenger, this.getX(), this.getY(), this.getZ());
                return;
            } else {
                target = this.position();
            }
        }
        moveFunction.accept(passenger, target.x, target.y, target.z);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (this.pinned) {
            return false;
        }
        if (this.hungPlayers.size() >= MAX_HANG) {
            return false;
        }
        if (this.hungPlayers.contains(entity.getUUID())) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        if (this.level().isClientSide) {
            return;
        }
        if (entityHitResult.getEntity() instanceof ServerPlayer target) {
            this.hangPlayer(target, entityHitResult.getLocation());
        }
    }

    /** 把玩家挂到竹子上（去重 + 反馈）。 */
    private void hangPlayer(ServerPlayer target, Vec3 location) {
        if (this.getOwner() != null && target.getUUID().equals(this.getOwner().getUUID())) {
            return;
        }
        if (this.hungPlayers.contains(target.getUUID()) || this.hungPlayers.size() >= MAX_HANG) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        this.hungPlayers.add(target.getUUID());
        target.startRiding(this, true);

        ServerLevel serverLevel = target.serverLevel();
        serverLevel.sendParticles(ParticleTypes.CRIT, location.x, location.y + 1.0f, location.z, 10, 0.25, 0.25,
                0.25, 0.12);
        serverLevel.playSound(null, location.x, location.y, location.z, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.PLAYERS,
                1.0f, 0.9f);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 base = new Vec3(this.getX(), this.getBoundingBox().maxY + 0.05, this.getZ());
        Vec3 safe = this.findFreeSpot(passenger, base);
        if (safe != null) {
            return safe;
        }
        Vec3 ground = this.findGroundSpot(passenger, this.getX(), this.getY(), this.getZ());
        return ground == null ? base : ground;
    }

    // ------------------------------------------------------------------ 撞墙 / 钉墙

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        if (this.pinned) {
            this.applyPin();
            return;
        }
        super.onHitBlock(blockHitResult);
        this.pinTo(blockHitResult.getLocation());
        this.setDeltaMovement(Vec3.ZERO);
        this.applyPin();
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAMBOO_WOOD_HIT,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            if (this.level() instanceof ServerLevel serverLevel) {
                Vec3 tip = this.pinPos.add(this.forward().scale(TIP_REACH));
                serverLevel.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 8, 0.12, 0.12, 0.12, 0.08);
            }
        }
    }

    /** 计算钉墙位置：枪尖扎进墙体 PIN_EMBED，杆身留在墙外，保证挂人的位置不在方块里。 */
    private void pinTo(Vec3 hitLocation) {
        Vec3 dir = this.forward();
        float[] backoffs = { TIP_REACH - PIN_EMBED, TIP_REACH - PIN_EMBED - 0.5F, TIP_REACH * 0.5F, 0.25F };
        Vec3 chosen = hitLocation.subtract(dir.scale(TIP_REACH - PIN_EMBED));
        for (float backoff : backoffs) {
            if (backoff <= 0.05F) {
                break;
            }
            Vec3 candidate = hitLocation.subtract(dir.scale(backoff));
            AABB box = new AABB(candidate.x - 0.2, candidate.y - 0.2, candidate.z - 0.2, candidate.x + 0.2,
                    candidate.y + 0.2, candidate.z + 0.2);
            if (this.level().noCollision(box)) {
                chosen = candidate;
                break;
            }
        }
        this.pinPos = new Vec3(chosen.x, this.getY(), chosen.z);
        this.pinYaw = this.getFacingYaw();
        this.pinned = true;
    }

    /** 强制保持钉墙状态（抵消原版 startFalling / 位移 / 旋转抖动）。 */
    private void applyPin() {
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.pinPos.x, this.pinPos.y, this.pinPos.z);
        this.setYRot(this.pinYaw);
        this.setXRot(0.0F);
        this.yRotO = this.pinYaw;
        this.xRotO = 0.0F;
        if (this.getFacingYaw() != this.pinYaw) {
            this.entityData.set(FACING_YAW, this.pinYaw);
        }
        this.inGround = true;
        this.inGroundTime = 1;
    }

    // ------------------------------------------------------------------ 每 tick

    @Override
    public void tick() {
        if (!this.pinned) {
            // 保持水平直线飞行，速度恒定，朝向始终跟运动方向。
            Vec3 motion = this.getDeltaMovement();
            Vec3 horiz = new Vec3(motion.x, 0.0, motion.z);
            if (horiz.lengthSqr() > 1.0E-6) {
                horiz = horiz.normalize().scale(THROW_SPEED);
                this.setDeltaMovement(horiz.x, 0.0, horiz.z);
                this.entityData.set(FACING_YAW, yawFrom(horiz));
            }
            this.setXRot(0.0f);
            this.xRotO = 0.0f;
        }
        if (this.level().isClientSide) {
            this.prevRenderYaw = this.renderYaw;
            this.renderYaw = Mth.rotLerp(0.35F, this.renderYaw, this.getFacingYaw());
        }
        super.tick();
        if (this.tickCount > LIFETIME_TICKS) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }
        if (this.pinned) {
            this.applyPin();
        }
        if (this.level().isClientSide) {
            return;
        }
        if (!this.pinned) {
            this.sweepHang();
        }
        this.remountHungPlayers();
        this.unstickRiders();
    }

    /** 飞行中用整根竹杆扫掠挂人：命中判定覆盖杆头到杆尾，比原版箭矢判定大得多。 */
    private void sweepHang() {
        if (this.hungPlayers.size() >= MAX_HANG) {
            return;
        }
        Vec3 forward = this.forward();
        Vec3 start = this.position().add(forward.scale(-TAIL_REACH));
        Vec3 end = this.position().add(forward.scale(TIP_REACH));
        AABB poleBox = new AABB(start, end).inflate(HIT_INFLATE);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(this.level(), this, start, end, poleBox,
                entity -> entity instanceof ServerPlayer target
                        && target != this.getOwner()
                        && GameUtils.isPlayerAliveAndSurvival(target)
                        && !this.hungPlayers.contains(target.getUUID()),
                (float) (end.distanceToSqr(start) + 1.0D));
        if (hit != null && hit.getEntity() instanceof ServerPlayer target) {
            this.hangPlayer(target, hit.getLocation());
        }
    }

    private void remountHungPlayers() {
        hungPlayers.removeIf(uuid -> {
            Player player = this.level().getPlayerByUUID(uuid);
            if (!(player instanceof ServerPlayer serverPlayer) || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                if (player != null && player.getVehicle() == this) {
                    player.stopRiding();
                }
                return true;
            }
            if (serverPlayer.getVehicle() != this) {
                serverPlayer.startRiding(this, true);
            }
            return false;
        });
    }

    /** 兜底：万一玩家还是嵌在方块里，找安全位；找不到就下竹落地，绝不把人传送到墙对面。 */
    private void unstickRiders() {
        for (Entity passenger : this.getPassengers()) {
            if (!(passenger instanceof ServerPlayer player)) {
                continue;
            }
            if (this.level().noCollision(player.getBoundingBox())) {
                continue;
            }
            Vec3 safe = this.findFreeSpot(player, player.position());
            if (safe == null) {
                safe = this.findGroundSpot(player, this.getX(), this.getY(), this.getZ());
            }
            if (safe == null) {
                if (player.getVehicle() == this) {
                    player.stopRiding();
                }
                this.hungPlayers.remove(player.getUUID());
                continue;
            }
            player.teleportTo(player.serverLevel(), safe.x, safe.y, safe.z, Set.of(), player.getYRot(),
                    player.getXRot());
        }
    }

    /**
     * 在 base 附近找空位：水平面上沿飞行反方向后退，可略向下；
     * 必须能从竹子本体看到该点，避免把人放到墙对面。
     */
    private Vec3 findFreeSpot(Entity entity, Vec3 base) {
        AABB box = entity.getBoundingBox();
        Vec3 origin = entity.position();
        double lockedY = this.getY();
        Vec3 locked = new Vec3(base.x, lockedY, base.z);
        if (isOpenAndVisible(entity, box, origin, locked)) {
            return locked;
        }
        Vec3 back = this.forward().reverse();
        Vec3 right = rightOf(back);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (double backStep = 0.0; backStep <= 2.5; backStep += 0.5) {
            for (double sideStep = -1.0; sideStep <= 1.0; sideStep += 0.5) {
                for (double downStep = 0.0; downStep <= 1.5; downStep += 0.5) {
                    Vec3 candidate = new Vec3(
                            locked.x + back.x * backStep + right.x * sideStep,
                            lockedY - downStep,
                            locked.z + back.z * backStep + right.z * sideStep);
                    if (!isOpenAndVisible(entity, box, origin, candidate)) {
                        continue;
                    }
                    double distance = candidate.distanceToSqr(locked);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** 从竹子位置垂直向下找能站人的点（同侧，有视线）。 */
    private Vec3 findGroundSpot(Entity entity, double x, double y, double z) {
        AABB box = entity.getBoundingBox();
        Vec3 origin = entity.position();
        double lowest = Math.max(this.level().getMinBuildHeight() + 1.0, y - 4.0);
        for (double yy = y; yy >= lowest; yy -= 0.25) {
            Vec3 candidate = new Vec3(x, yy, z);
            if (isOpenAndVisible(entity, box, origin, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isOpenAndVisible(Entity entity, AABB box, Vec3 origin, Vec3 candidate) {
        if (!this.level().noCollision(box.move(candidate.subtract(origin)))) {
            return false;
        }
        Vec3 from = this.position();
        BlockHitResult clip = this.level().clip(new ClipContext(from, candidate, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity));
        return clip.getType() == HitResult.Type.MISS || clip.getLocation().distanceToSqr(candidate) < 0.09;
    }

    private Vec3 forward() {
        float yaw = this.getFacingYaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
        if (right.lengthSqr() < 1.0E-6) {
            return new Vec3(1.0, 0.0, 0.0);
        }
        return right.normalize();
    }

    private static float yawFrom(Vec3 dir) {
        Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
        if (flat.lengthSqr() < 1.0E-6) {
            return 0.0F;
        }
        return (float) (Mth.atan2(-flat.x, flat.z) * Mth.RAD_TO_DEG);
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public void remove(RemovalReason reason) {
        this.ejectPassengers();
        super.remove(reason);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return Items.BAMBOO.getDefaultInstance();
    }

    @Override
    public ItemStack getPickupItemStackOrigin() {
        return ModItems.BAMBOO.getDefaultInstance();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        ListTag list = new ListTag();
        for (UUID uuid : hungPlayers) {
            list.add(NbtUtils.createUUID(uuid));
        }
        compoundTag.put("HungPlayers", list);
        compoundTag.putBoolean("Pinned", this.pinned);
        if (this.pinned) {
            compoundTag.putDouble("PinX", this.pinPos.x);
            compoundTag.putDouble("PinY", this.pinPos.y);
            compoundTag.putDouble("PinZ", this.pinPos.z);
            compoundTag.putFloat("PinYaw", this.pinYaw);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        hungPlayers.clear();
        ListTag list = compoundTag.getList("HungPlayers", Tag.TAG_INT_ARRAY);
        for (Tag tag : list) {
            hungPlayers.add(NbtUtils.loadUUID(tag));
        }
        this.pinned = compoundTag.getBoolean("Pinned");
        if (this.pinned) {
            this.pinPos = new Vec3(compoundTag.getDouble("PinX"), compoundTag.getDouble("PinY"),
                    compoundTag.getDouble("PinZ"));
            this.pinYaw = compoundTag.getFloat("PinYaw");
        }
    }
}
