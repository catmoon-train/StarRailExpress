package org.agmas.noellesroles.entity;

import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

/**
 * 绊索陷阱实体
 * 
 * 可见陷阱，所有玩家均可看到并拆除：
 * - 目标走路通过时获得 3 秒缓慢 I
 * - 若目标在疾跑状态下触发，则会摔倒，获得 5 秒缓慢 IV 并清空体力值
 */
public class TripwireTrapEntity extends Entity {

    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            TripwireTrapEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 陷阱触发半径（格） */
    public static final double TRIGGER_RADIUS = 1.0;

    /** 陷阱存在时间上限（5分钟 = 6000 tick），防止无限存在 */
    public static final int MAX_LIFETIME = 6000;

    /** 走路触发缓慢效果持续时间（3秒 = 60 tick） */
    public static final int WALK_SLOW_DURATION = 60;

    /** 走路触发缓慢等级（Slowness I = amplifier 0） */
    public static final int WALK_SLOW_AMPLIFIER = 0;

    /** 疾跑触发缓慢效果持续时间（5秒 = 100 tick） */
    public static final int SPRINT_SLOW_DURATION = 100;

    /** 疾跑触发缓慢等级（Slowness IV = amplifier 3） */
    public static final int SPRINT_SLOW_AMPLIFIER = 3;

    /** 存活时间计数器 */
    private int lifetime = 0;

    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;

    public TripwireTrapEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.setInvisible(false); // 对所有人可见
        this.setNoGravity(true); // 无重力
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
    }

    /**
     * 设置所有者
     */
    public void setOwner(Player owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
        }
    }

    /**
     * 获取所有者 UUID
     */
    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    /**
     * 获取所有者玩家
     */
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

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide())
            return;

        // 增加存活时间
        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 检查所有者是否还是设陷者
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

        // 检测触发
        checkTrigger();
    }

    /**
     * 检测是否有玩家触发陷阱
     */
    private void checkTrigger() {
        Level world = level();
        Vec3 pos = this.position();

        // 创建检测区域
        AABB detectionBox = new AABB(
                pos.x - TRIGGER_RADIUS, pos.y - 0.5, pos.z - TRIGGER_RADIUS,
                pos.x + TRIGGER_RADIUS, pos.y + 2.0, pos.z + TRIGGER_RADIUS);

        // 获取区域内的所有玩家
        List<Player> players = world.getEntitiesOfClass(
                Player.class, detectionBox,
                player -> {
                    // 排除所有者
                    Optional<UUID> ownerUuid = getOwnerUuid();
                    if (ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get())) {
                        return false;
                    }

                    // 排除死亡或观察者模式的玩家
                    if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                        return false;
                    }

                    // 排除其他杀手阵营玩家（同阵营不触发）
                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
                    if (gameWorld.isKillerTeam(player)) {
                        return false;
                    }

                    return true;
                });

        // 如果有玩家触发
        if (!players.isEmpty()) {
            Player victim = players.getFirst();
            triggerTrap(victim);
        }
    }

    /**
     * 触发绊索陷阱
     */
    private void triggerTrap(Player victim) {
        Player owner = getOwner();
        if (owner == null) {
            this.discard();
            return;
        }

        boolean wasSprinting = victim.isSprinting();

        if (wasSprinting) {
            // 疾跑状态触发：摔倒，5秒缓慢 IV，清空体力值
            victim.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SPRINT_SLOW_DURATION, SPRINT_SLOW_AMPLIFIER,
                    false, true, true));

            // 停止疾跑
            victim.setSprinting(false);

            // 清空体力值
            if (victim instanceof PlayerStaminaGetter staminaGetter) {
                staminaGetter.starrailexpress$setStamina(0);
            }

            // 播放摔倒音效
            Level world = victim.level();
            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 通知受害者
            if (victim instanceof ServerPlayer serverVictim) {
                serverVictim.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.tripwire_sprint_triggered")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        true);
            }
        } else {
            // 走路状态触发：3秒缓慢 I
            victim.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, WALK_SLOW_DURATION, WALK_SLOW_AMPLIFIER,
                    false, true, true));

            // 播放触发音效
            Level world = victim.level();
            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 通知受害者
            if (victim instanceof ServerPlayer serverVictim) {
                serverVictim.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.tripwire_walk_triggered")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
        }

        // 通知设陷者
        if (owner instanceof ServerPlayer serverOwner) {
            String typeStr = wasSprinting ? "§c疾跑" : "§e走路";
            serverOwner.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.tripwire_triggered_notify",
                            victim.getName(), typeStr)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        // 陷阱触发后消失（一次性）
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true; // 可以被点击（用于拆除）
    }

    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }

    @Override
    public boolean canBeCollidedWith() {
        return false; // 无物理碰撞
    }

    /**
     * 当受到攻击时（玩家左键点击拆除）
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;

        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            // 所有者不能拆除自己的陷阱（通过攻击）
            Optional<UUID> ownerUuid = getOwnerUuid();
            if (ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get())) {
                return false;
            }

            // 杀手阵营不能拆除
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
            if (gameWorld.isKillerTeam(player)) {
                return false;
            }

            // 播放拆除音效
            level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.PLAYERS, 1.0f, 1.5f);

            // 通知拆除者
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.tripwire_dismantled")
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }

            // 通知设陷者
            Player owner = getOwner();
            if (owner instanceof ServerPlayer serverOwner) {
                serverOwner.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.tripwire_dismantled_notify",
                                player.getName())
                                .withStyle(ChatFormatting.RED),
                        true);
            }

            this.discard();
            return true;
        }
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
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putString("OwnerUUID", uuid.toString()));
        nbt.putInt("Lifetime", this.lifetime);
    }
}
