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
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 幽露「遮天闭目」烟雾波实体。
 *
 * <p>使用烟雾物品后生成：一团向前匀速推进的烟雾（{@code noPhysics}，可穿墙），
 * 推进途中持续判定——半径内的存活玩家（拥有者除外）陷入失明+黑暗；
 * <b>命中后不会消失</b>，继续前进直到走完总距离。
 * 视觉上仅在波前位置产生少量烟雾粒子（客户端 tick）。
 */
public class YouluSmokeWaveEntity extends Entity {

    /** 拥有者 UUID（不受烟雾影响）。 */
    private UUID ownerUuid = null;
    /** 球烟渲染半径（同步给客户端）。 */
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(
            YouluSmokeWaveEntity.class, EntityDataSerializers.FLOAT);
    /** 最大推进距离（同步给客户端用于渲染消散进度）。 */
    private static final EntityDataAccessor<Float> MAX_DISTANCE = SynchedEntityData.defineId(
            YouluSmokeWaveEntity.class, EntityDataSerializers.FLOAT);
    /** 剩余可推进距离（同步给客户端用于渲染消散进度）。 */
    private static final EntityDataAccessor<Float> REMAINING_DISTANCE = SynchedEntityData.defineId(
            YouluSmokeWaveEntity.class, EntityDataSerializers.FLOAT);
    /** 前进方向（单位向量，可含 Y 轴分量）。 */
    private Vec3 direction = new Vec3(0, 0, 1);
    /** 推进速度（格/tick）。 */
    private double speed = 0.5D;
    /** 命中半径（格）。 */
    private double hitRadius = 4.0D;
    /** 失明+黑暗时长（tick）。 */
    private int blindTicks = 8 * 20;
    /** 剩余可推进距离（格）。 */
    private double remainingDistance = 12.0D;

    public YouluSmokeWaveEntity(EntityType<? extends YouluSmokeWaveEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public void setup(UUID ownerUuid, Vec3 direction, double speed, double rangeBlocks,
            double hitRadius, int blindTicks, float renderRadius) {
        this.ownerUuid = ownerUuid;
        this.direction = direction.normalize();
        this.speed = speed;
        this.remainingDistance = rangeBlocks;
        this.hitRadius = hitRadius;
        this.blindTicks = blindTicks;
        this.entityData.set(RADIUS, renderRadius);
        this.entityData.set(MAX_DISTANCE, (float) rangeBlocks);
        this.entityData.set(REMAINING_DISTANCE, (float) remainingDistance);
    }

    public float getRadius() {
        return this.entityData.get(RADIUS);
    }

    public float getMaxDistance() {
        return this.entityData.get(MAX_DISTANCE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 3.6F);
        builder.define(MAX_DISTANCE, 30.0F);
        builder.define(REMAINING_DISTANCE, 30.0F);
    }

    /** 剩余可推进距离（同步给客户端用于渲染消散进度）。 */
    public double getRemainingDistance() {
        return this.entityData.get(REMAINING_DISTANCE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            // 客户端：波前少量烟雾粒子
            spawnClientParticles();
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (gameWorld == null || !gameWorld.isRunning()) {
            discard();
            return;
        }

        // 匀速直线推进（noPhysics 穿墙）
        double step = Math.min(speed, remainingDistance);
        setPos(getX() + direction.x * step, getY() + direction.y * step, getZ() + direction.z * step);
        remainingDistance -= step;
        this.entityData.set(REMAINING_DISTANCE, (float) remainingDistance);

        applyToPlayersInRange(serverLevel);

        if (remainingDistance <= 1.0e-4) {
            discard();
        }
    }

    /** 半径内的存活玩家（拥有者除外）持续获得失明+黑暗；波不因命中而消失。 */
    private void applyToPlayersInRange(ServerLevel serverLevel) {
        double r = hitRadius;
        AABB box = new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
        for (ServerPlayer p : serverLevel.getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival)) {
            if (ownerUuid != null && ownerUuid.equals(p.getUUID())) continue;
            if (p.getEyePosition().distanceTo(position()) > r
                    && p.position().distanceTo(position()) > r) continue;
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindTicks, 0, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, blindTicks, 0, false, false, true));
        }
    }

    private void spawnClientParticles() {
        // 少量粒子：每 tick 2 个烟雾 + 偶尔一个大烟雾
        for (int i = 0; i < 2; i++) {
            double ox = (random.nextDouble() * 2 - 1) * 1.2;
            double oy = (random.nextDouble() * 2 - 1) * 0.8;
            double oz = (random.nextDouble() * 2 - 1) * 1.2;
            level().addParticle(ParticleTypes.SMOKE,
                    getX() + ox, getY() + oy, getZ() + oz, 0, 0.02, 0);
        }
        if (tickCount % 5 == 0) {
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) ownerUuid = tag.getUUID("OwnerUUID");
        direction = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ"));
        speed = tag.getDouble("Speed");
        hitRadius = tag.getDouble("HitRadius");
        blindTicks = tag.getInt("BlindTicks");
        remainingDistance = tag.getDouble("RemainingDistance");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) tag.putUUID("OwnerUUID", ownerUuid);
        tag.putDouble("DirX", direction.x);
        tag.putDouble("DirY", direction.y);
        tag.putDouble("DirZ", direction.z);
        tag.putDouble("Speed", speed);
        tag.putDouble("HitRadius", hitRadius);
        tag.putInt("BlindTicks", blindTicks);
        tag.putDouble("RemainingDistance", remainingDistance);
    }
}
