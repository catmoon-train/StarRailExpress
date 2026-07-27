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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Optional;
import java.util.UUID;

/**
 * 幽露「球烟」实体：G 键技能在自由摄像机位置生成的黑色烟雾球。
 *
 * <p>悬浮在生成位置（无重力），持续 12s（可配置）。内部的存活玩家（拥有者除外）
 * 每 0.5s 刷新一次视野迷雾 1 级（{@link ModEffects#VISION_FOG}）；处于球内的玩家
 * 客户端雾色会被渲染为黑色（见 {@code YouluFogColorMixin}）。
 * 渲染为自制的半透明暗色球体（见 {@code YouluSmokeBallRenderer}）。
 */
public class YouluSmokeBallEntity extends Entity {

    /** 球半径（同步给客户端用于渲染与"是否在球内"的判定）。 */
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(
            YouluSmokeBallEntity.class, EntityDataSerializers.FLOAT);
    /** 拥有者 UUID（拥有者不受球内效果影响）。 */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            YouluSmokeBallEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 最大生命周期（tick，同步给客户端用于渲染消散进度）。 */
    private static final EntityDataAccessor<Float> MAX_LIFETIME = SynchedEntityData.defineId(
            YouluSmokeBallEntity.class, EntityDataSerializers.FLOAT);

    /** 效果刷新间隔（tick）。 */
    private static final int EFFECT_INTERVAL = 10;
    /** 每次刷新给予的效果时长（tick），略大于间隔避免闪烁。 */
    private static final int EFFECT_DURATION = 30;

    private int remainingLifetime = 15 * 20;

    public YouluSmokeBallEntity(EntityType<? extends YouluSmokeBallEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public void setup(UUID ownerUuid, float radius, int lifetimeTicks) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
        this.entityData.set(RADIUS, radius);
        this.entityData.set(MAX_LIFETIME, (float) lifetimeTicks);
        this.remainingLifetime = lifetimeTicks;
    }

    public float getRadius() {
        return this.entityData.get(RADIUS);
    }

    public UUID getOwnerUuid() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public float getMaxLifetime() {
        return this.entityData.get(MAX_LIFETIME);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 6.0F);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(MAX_LIFETIME, 240.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            // 客户端：球内飘散少量烟雾粒子
            spawnClientParticles();
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (gameWorld == null || !gameWorld.isRunning()) {
            discard();
            return;
        }
        if (--remainingLifetime <= 0) {
            discard();
            return;
        }

        if (tickCount % EFFECT_INTERVAL == 1) {
            applyFogToPlayersInside(serverLevel);
        }
    }

    /** 对球内玩家（拥有者除外）施加视野迷雾 1 级。 */
    private void applyFogToPlayersInside(ServerLevel serverLevel) {
        double r = getRadius();
        UUID owner = getOwnerUuid();
        AABB box = new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
        for (ServerPlayer p : serverLevel.getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival)) {
            if (owner != null && owner.equals(p.getUUID())) continue;
            if (p.getEyePosition().distanceTo(position()) > r) continue;
            p.addEffect(new MobEffectInstance(ModEffects.VISION_FOG, EFFECT_DURATION, 0,
                    true, false, false));
        }
    }

    private void spawnClientParticles() {
        double r = getRadius();
        for (int i = 0; i < 3; i++) {
            double ox = (random.nextDouble() * 2 - 1) * r * 0.8;
            double oy = (random.nextDouble() * 2 - 1) * r * 0.8;
            double oz = (random.nextDouble() * 2 - 1) * r * 0.8;
            level().addParticle(ParticleTypes.SMOKE,
                    getX() + ox, getY() + oy, getZ() + oz, 0, 0.01, 0);
        }
    }

    /** 判断某位置是否处于球内（客户端雾色判定亦使用）。 */
    public boolean contains(net.minecraft.world.phys.Vec3 pos) {
        return pos.distanceTo(position()) <= getRadius();
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        this.entityData.set(RADIUS, tag.getFloat("Radius"));
        remainingLifetime = tag.getInt("RemainingLifetime");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("OwnerUUID", owner);
        }
        tag.putFloat("Radius", getRadius());
        tag.putInt("RemainingLifetime", remainingLifetime);
    }
}
