package org.agmas.noellesroles.game.command_block;

import java.util.UUID;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;

/** 固定在施法位置的雾区；实体自身同步位置，不依赖施法者的追踪范围。 */
public class FogGuestZoneEntity extends Entity {
    public static final double RADIUS = FogZoneGeometry.RADIUS;
    public static final double HEIGHT = FogZoneGeometry.HEIGHT;
    public static final int DURATION = 22 * 20;
    private UUID owner;
    private long expiresAt;

    public FogGuestZoneEntity(EntityType<? extends FogGuestZoneEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public void start(ServerPlayer player) {
        owner = player.getUUID();
        expiresAt = level().getGameTime() + DURATION;
        setPos(player.position());
    }

    public boolean contains(Vec3 point) {
        return FogZoneGeometry.contains(point.x - getX(), point.y - getY(), point.z - getZ());
    }

    public static boolean isInside(Player player) {
        return !player.level().getEntitiesOfClass(FogGuestZoneEntity.class,
                player.getBoundingBox().inflate(RADIUS, HEIGHT, RADIUS), zone -> zone.contains(player.position())).isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        ServerPlayer caster = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (!SREGameWorldComponent.KEY.get(level).isRunning() || level.getGameTime() >= expiresAt
                || caster == null || caster.level() != level
                || !GameUtils.isPlayerAliveAndSurvival(caster)
                || !SREGameWorldComponent.KEY.get(level).isRole(caster, ModRoles.FOG_GUEST)) {
            discard();
            return;
        }
        // 每五 tick 刷新短效果；离开雾区后至多残留半秒，不留下整段持续时间的黑暗。
        if (tickCount % 5 != 0) return;
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox(),
                target -> GameUtils.isPlayerAliveAndSurvival(target) && contains(target.position()))) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 10, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 0, false, false, true));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        expiresAt = tag.getLong("ExpiresAt");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putLong("ExpiresAt", expiresAt);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }
}
