package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostItems;

import java.util.Optional;
import java.util.UUID;

/** 布袋鬼的活化草人：使用原生地面寻路，近身且视线畅通时才结算扑击。 */
public final class SackGhostEffigyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            SackGhostEffigyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final int LIFETIME = 30 * 20;
    private static final double SEARCH_RANGE = 15.0D;
    private int remainingTicks = LIFETIME;
    private int repathTicks;

    public SackGhostEffigyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(false);
            ground.setCanPassDoors(true);
        }
    }

    /** 注册略快于普通玩家疾跑的地面追逐属性，不使用穿墙位移。 */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, SEARCH_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    /** 设置主人，并同步其身份供客户端复用本人皮肤。 */
    public void setOwner(ServerPlayer owner) {
        entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
    }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER_UUID);
    }

    public Player getOwner() {
        return getOwnerUuid().map(uuid -> level().getPlayerByUUID(uuid)).orElse(null);
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
            return;
        }
        if (!(getOwner() instanceof ServerPlayer owner) || owner.hasDisconnected()
                || owner.server.getPlayerList().getPlayer(owner.getUUID()) != owner
                || !owner.isAlive() || !GameUtils.isPlayerAliveAndSurvival(owner)
                || owner.level() != level() || !SREGameWorldComponent.KEY.get(level()).isRunning()) {
            discard();
            return;
        }
        var data = SackGhostItems.data(owner);
        if (data == null || !data.isActiveMaChenXu()) {
            discard();
            return;
        }
        if (!data.combatClockRunning()) {
            getNavigation().stop();
            setTarget(null);
            setSpeed(0);
            setDeltaMovement(Vec3.ZERO);
            setNoAi(true);
            repathTicks = 0;
            // 不推进物理、寻路或寿命，避免暂停时残留速度仍推动草人。
            return;
        }
        setNoAi(false);
        if (--remainingTicks <= 0) {
            discard();
            return;
        }

        ServerPlayer nearest = null;
        double nearestDistance = SEARCH_RANGE * SEARCH_RANGE;
        for (ServerPlayer candidate : owner.serverLevel().players()) {
            if (candidate.hasDisconnected() || !data.isEnemy(candidate)) continue;
            double distance = distanceToSqr(candidate);
            if (distance <= nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        boolean changed = getTarget() != nearest;
        setTarget(nearest);
        if (nearest == null) {
            getNavigation().stop();
            setSpeed(0);
            repathTicks = 0;
        } else {
            getLookControl().setLookAt(nearest, 30.0F, 30.0F);
            if (changed || --repathTicks <= 0) {
                getNavigation().moveTo(nearest, 1.0D);
                repathTicks = 10;
            }
        }
        super.tick();
        if (isRemoved() || nearest == null || !data.isEnemy(nearest)) return;
        if (distanceToSqr(nearest) <= 2.25D
                && getBoundingBox().inflate(0.45D, 0.2D, 0.45D).intersects(nearest.getBoundingBox())
                && hasLineOfSight(nearest)) {
            // 数值、定身和表现由职业回调统一处理；无论是否被法器抵挡都消散。
            try {
                data.onEffigyHit(nearest);
            } finally {
                discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
