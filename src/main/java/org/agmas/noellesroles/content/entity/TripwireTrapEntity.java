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
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 设陷者「绊线」陷阱实体（重做版）。
 *
 * <p>贴墙放置：锚点在墙面上，绊线沿墙面法线（水平方向）向外延伸至对面墙壁
 * （或最大长度）。踩中绊线的玩家速度 -90%（缓慢 VI）持续 4s；绊线不因触发消失，
 * <b>只有被枪击落才会消失</b>（见 {@code TrapperTrapGunPayloadMixin}），近战无法拆除。
 *
 * <p>可见性（见 {@code TripwireTrapEntityRenderer}）：所有玩家只能看到墙面锚点的
 * 出发点小标记；激光线本体只有杀手阵营与偏狼中立阵营可见。
 */
public class TripwireTrapEntity extends Entity {

    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            TripwireTrapEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 绊线延伸方向（Direction 3D data value，水平四向）。 */
    private static final EntityDataAccessor<Byte> WIRE_DIRECTION = SynchedEntityData.defineId(
            TripwireTrapEntity.class, EntityDataSerializers.BYTE);
    /** 绊线长度（格）。 */
    private static final EntityDataAccessor<Float> WIRE_LENGTH = SynchedEntityData.defineId(
            TripwireTrapEntity.class, EntityDataSerializers.FLOAT);

    /** 触发后给予的缓慢等级：缓慢 VI = -90% 移速。 */
    public static final int SLOW_AMPLIFIER = 5;
    /** 缓慢持续时间（4s）。 */
    public static final int SLOW_DURATION = 4 * 20;
    /** 绊线判定盒半厚度（格）。 */
    public static final double WIRE_HALF_THICKNESS = 0.2;
    /**
     * 同一受害者的重触发节流（tick）＝效果时长：效果只在触发瞬间施加一次，
     * 不随接触每 tick 刷新——否则穿线期间 4s 倒计时被不断重置，玩家会被
     * 永远粘在线上动弹不得。
     */
    private static final int RETRIGGER_THROTTLE = SLOW_DURATION;

    /** 每个受害者的触发节流计时。 */
    private final Map<UUID, Integer> victimThrottle = new HashMap<>();
    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;

    public TripwireTrapEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(WIRE_DIRECTION, (byte) Direction.NORTH.get3DDataValue());
        builder.define(WIRE_LENGTH, 0.0F);
    }

    /** 设置绊线几何（锚点=实体位置，沿 direction 延伸 length 格）。 */
    public void setupWire(Player owner, Direction direction, double length) {
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.ownerCache = owner;
        this.entityData.set(WIRE_DIRECTION, (byte) direction.get3DDataValue());
        this.entityData.set(WIRE_LENGTH, (float) length);
        this.setBoundingBox(this.makeBoundingBox());
    }

    public Direction getWireDirection() {
        return Direction.from3DDataValue(this.entityData.get(WIRE_DIRECTION));
    }

    public double getWireLength() {
        return this.entityData.get(WIRE_LENGTH);
    }

    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }
        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent()) {
            ownerCache = level().getPlayerByUUID(ownerUuid.get());
            return ownerCache;
        }
        return null;
    }

    /** 包围盒沿绊线方向拉长（方向为轴对齐，故 AABB 恰好贴合），供触发判定与枪击射线拾取。 */
    @Override
    protected AABB makeBoundingBox() {
        Vec3 pos = position();
        double len = getWireLength();
        Direction dir = getWireDirection();
        if (len <= 0) {
            return new AABB(pos.x - 0.25, pos.y - 0.1, pos.z - 0.25, pos.x + 0.25, pos.y + 0.1, pos.z + 0.25);
        }
        Vec3 end = pos.add(dir.getStepX() * len, 0, dir.getStepZ() * len);
        return new AABB(
                Math.min(pos.x, end.x) - WIRE_HALF_THICKNESS,
                pos.y - WIRE_HALF_THICKNESS,
                Math.min(pos.z, end.z) - WIRE_HALF_THICKNESS,
                Math.max(pos.x, end.x) + WIRE_HALF_THICKNESS,
                pos.y + WIRE_HALF_THICKNESS,
                Math.max(pos.z, end.z) + WIRE_HALF_THICKNESS);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (WIRE_DIRECTION.equals(data) || WIRE_LENGTH.equals(data)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }

        Player owner = getOwner();
        if (owner == null) {
            this.discard();
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (!gameWorld.isRole(owner, ModRoles.TRAPPER)) {
            this.discard();
            return;
        }

        victimThrottle.replaceAll((uuid, ticks) -> ticks - 1);
        victimThrottle.values().removeIf(ticks -> ticks <= 0);
        checkTrigger();
    }

    /** 玩家碰到绊线：缓慢 VI（-90%）4s；绊线保留。 */
    private void checkTrigger() {
        Level world = level();
        AABB wireBox = getBoundingBox().inflate(0.1);
        List<Player> players = world.getEntitiesOfClass(Player.class, wireBox, player -> {
            Optional<UUID> ownerUuid = getOwnerUuid();
            if (ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get())) {
                return false;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return false;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld.isKillerTeam(player)) {
                return false;
            }
            // 被操纵师操控的玩家：弹回且不触发陷阱
            if (org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA.bounceBackIfControlled(player)) {
                return false;
            }
            return true;
        });
        for (Player victim : players) {
            triggerOn(victim);
        }
    }

    private void triggerOn(Player victim) {
        if (victimThrottle.containsKey(victim.getUUID())) {
            return;
        }
        victimThrottle.put(victim.getUUID(), RETRIGGER_THROTTLE);
        victim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_AMPLIFIER, false, true, true));

        // 记录陷阱触发事件（低频关键事件）
        Player owner = getOwner();
        if (owner != null) {
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordTrapTriggered(owner.getUUID(), victim.getUUID());
        }

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0f, 0.8f);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + 0.5, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        }
        if (victim instanceof ServerPlayer serverVictim) {
            serverVictim.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, SLOW_DURATION, SLOW_AMPLIFIER, false, true, true));
            serverVictim.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, SLOW_DURATION, SLOW_AMPLIFIER, false, true, true));
            serverVictim.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.tripwire_slowed")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        if (owner instanceof ServerPlayer serverOwner) {
            serverOwner.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.tripwire_hit_notify", victim.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
    }

    /** 被枪击落（由 {@code TrapperTrapGunPayloadMixin} 调用）：唯一的移除方式。 */
    public void shotDown(ServerPlayer shooter) {
        if (level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 mid = position().add(
                    getWireDirection().getStepX() * getWireLength() / 2, 0,
                    getWireDirection().getStepZ() * getWireLength() / 2);
            serverLevel.playSound(null, mid.x, mid.y, mid.z,
                    SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.PLAYERS, 1.0f, 1.4f);
            serverLevel.sendParticles(ParticleTypes.WAX_OFF, mid.x, mid.y, mid.z, 20,
                    Math.abs(getWireDirection().getStepX()) * getWireLength() / 2 + 0.1, 0.1,
                    Math.abs(getWireDirection().getStepZ()) * getWireLength() / 2 + 0.1, 0.0);
        }
        if (shooter != null) {
            shooter.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.tripwire_dismantled")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        Player owner = getOwner();
        if (owner instanceof ServerPlayer serverOwner && serverOwner != shooter) {
            serverOwner.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.tripwire_dismantled_notify",
                            shooter != null ? shooter.getName() : Component.literal("?"))
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true; // 允许枪械射线拾取
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** 近战/其他伤害无效：只有枪能击落（走 shotDown）。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("OwnerUUID")) {
            try {
                UUID uuid = UUID.fromString(nbt.getString("OwnerUUID"));
                this.entityData.set(OWNER_UUID, Optional.of(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.entityData.set(WIRE_DIRECTION, nbt.getByte("WireDirection"));
        this.entityData.set(WIRE_LENGTH, nbt.getFloat("WireLength"));
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putString("OwnerUUID", uuid.toString()));
        nbt.putByte("WireDirection", this.entityData.get(WIRE_DIRECTION));
        nbt.putFloat("WireLength", this.entityData.get(WIRE_LENGTH));
    }
}
