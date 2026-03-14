package org.agmas.noellesroles.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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

    /** 傀儡存活时间（20秒 = 400 tick） */
    private static final int PUPPET_LIFETIME = 20 * 20;

    /** 剩余存活时间 */
    private int remainingLifetime = PUPPET_LIFETIME;

    /** 召唤者UUID */
    private UUID ownerUuid;

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
        this.ownerUuid = owner.getUUID();
        this.ownerName = owner.getName().getString();

        // 复制召唤者的外观（这里简化处理，实际可能需要更复杂的皮肤复制）
        // 在实际实现中，可能需要使用 GameProfile 和皮肤系统
    }

    /**
     * 获取召唤者
     */
    public Player getOwner() {
        if (ownerUuid == null)
            return null;
        if (level() instanceof ServerLevel serverLevel) {
            return serverLevel.getPlayerByUUID(ownerUuid);
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

        // 每秒检查一次召唤者是否还存活
        if (remainingLifetime % 20 == 0) {
            Player owner = getOwner();
            if (owner == null || !owner.isAlive()) {
                disappear();
                return;
            }
        }
    }

    /**
     * 随机移动
     */
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

        // 生成粒子效果（如果需要的话）
        if (level() instanceof ServerLevel serverLevel) {
            // 可以在这里添加粒子效果
        }

        // 移除实体
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("RemainingLifetime", remainingLifetime);
        compound.putInt("MoveTimer", moveTimer);
        compound.putString("OwnerName", ownerName);
        if (ownerUuid != null) {
            compound.putUUID("OwnerUUID", ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        remainingLifetime = compound.getInt("RemainingLifetime");
        moveTimer = compound.getInt("MoveTimer");
        ownerName = compound.getString("OwnerName");
        if (compound.hasUUID("OwnerUUID")) {
            ownerUuid = compound.getUUID("OwnerUUID");
        }
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