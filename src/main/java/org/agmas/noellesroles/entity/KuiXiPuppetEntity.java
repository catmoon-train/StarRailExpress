package org.agmas.noellesroles.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 傀戏傀儡实体
 * 
 * 特性：
 * - 完全复制召唤者的外观和手持物品
 * - 自主随机行走
 * - 受到任意伤害立即消散
 * - 持续20秒后自动消散
 */
public class KuiXiPuppetEntity extends PathfinderMob {
    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            KuiXiPuppetEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FOLLOW_RANGE, 16.0) // ← 必须加这个！
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    /** 皮肤 GameProfile（用于渲染玩家皮肤） */
    private GameProfile skinProfile = null;

    /** 傀儡存活时间（20秒 = 400 tick） */
    private static final int PUPPET_LIFETIME = 20 * 20;

    /** 剩余存活时间 */
    private int remainingLifetime = PUPPET_LIFETIME;

    /** 召唤者名称 */
    private String ownerName = "";

    /** 随机移动计时器 */
    private int moveTimer = 0;

    public KuiXiPuppetEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setHealth(1.0F); // 1点血，任何伤害都会死亡
    }

    @Override
    protected void registerGoals() {
        // 添加随机游荡AI
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /**
     * 设置召唤者
     */
    public void setOwner(Player owner) {
        this.ownerName = owner.getName().getString();
        // 设置皮肤（获取玩家的 GameProfile）
        if (owner instanceof ServerPlayer serverPlayer) {
            this.skinProfile = serverPlayer.getGameProfile();
        }
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        // 复制召唤者的外观（这里简化处理，实际可能需要更复杂的皮肤复制）
        // 在实际实现中，可能需要使用 GameProfile 和皮肤系统
    }

    /**
     * 获取所有者 UUID
     */
    public UUID getOwnerUuid() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    /**
     * 获取所有者玩家
     */
    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }

        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            ownerCache = level().getPlayerByUUID(ownerUuid);
            return ownerCache;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        // 倒计时
        remainingLifetime--;

        // 时间到了，消散
        if (remainingLifetime <= 0) {
            disappear();
            return;
        }

        // 每5秒随机改变移动方向
        moveTimer++;
        if (moveTimer >= 100) { // 5秒
            moveTimer = 0;
            randomMove();
        }

        if (this.level() instanceof ServerLevel sl) {
            // 每秒检查一次召唤者是否还存活
            if (remainingLifetime % 20 == 0) {
                UUID ownerUuid = getOwnerUuid();
                Player owner = sl.getPlayerByUUID(ownerUuid);
                if (owner == null || !owner.isAlive()) {
                    disappear();
                    return;
                }
            }
        }
    }

    /**
     * 随机移动
     */
    @SuppressWarnings("deprecation")
    private void randomMove() {
        if (level().isClientSide)
            return;

        Random random = new Random();

        // 30%概率不移动
        if (random.nextFloat() < 0.3f) {
            return;
        }

        // 在周围8格范围内随机选择一个目标点
        BlockPos currentPos = this.blockPosition();
        int x = currentPos.getX() + random.nextInt(17) - 8; // -8 到 +8
        int z = currentPos.getZ() + random.nextInt(17) - 8;
        int y = currentPos.getY();

        // 寻找合适的Y坐标
        BlockPos targetPos = new BlockPos(x, y, z);
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos testPos = targetPos.offset(0, dy, 0);
            if (level().getBlockState(testPos).isAir() &&
                    level().getBlockState(testPos.below()).isSolid()) {
                targetPos = testPos;
                break;
            }
        }

        // 设置移动目标
        this.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.6D);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // 受到任意伤害立即消散
        disappear();
        return true;
    }

    /**
     * 消散效果
     */
    private void disappear() {
        if (level().isClientSide)
            return;

        // 播放消散音效
        level().playSound(null, this.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE,
                0.5F, 1.5F);

        // 移除实体
        this.discard();
    }

    /**
     * 获取皮肤 GameProfile（用于客户端渲染）
     */
    public GameProfile getSkinProfile() {
        return skinProfile;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null)
            compound.putUUID("OwnerUUID", ownerUuid);
        compound.putInt("RemainingLifetime", remainingLifetime);
        compound.putInt("MoveTimer", moveTimer);
        compound.putString("OwnerName", ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(compound.getUUID("OwnerUUID")));
        }
        remainingLifetime = compound.getInt("RemainingLifetime");
        moveTimer = compound.getInt("MoveTimer");
        ownerName = compound.getString("OwnerName");
    }

    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity entity) {
        return false; // 不能骑乘
    }

    /**
     * 获取剩余存活时间（秒）
     */
    public float getRemainingLifetimeSeconds() {
        return remainingLifetime / 20.0f;
    }

    /**
     * 获取召唤者名称
     */
    public String getOwnerName() {
        return ownerName;
    }
}