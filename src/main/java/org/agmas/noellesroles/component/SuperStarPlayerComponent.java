package org.agmas.noellesroles.component;

import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 明星组件
 *
 * 被动技能：无
 * 主动技能：使用技能让10格范围内的玩家视野都看向自己，并让自己发光2秒（30秒冷却）
 *
 * 明星为好人阵营（乘客阵营）
 */
public class SuperStarPlayerComponent implements RoleComponent, ServerTickingComponent {
    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<SuperStarPlayerComponent> KEY = ModComponents.STAR;

    // ==================== 常量定义 ====================

    /** 发光持续时间（2秒 = 40 tick） */
    public static final int GLOW_DURATION = 40;

    /** 主动技能冷却时间（60秒 = 1200 tick） */
    public static final int ABILITY_COOLDOWN = 1200;

    /** 技能范围（10格） */
    public static final double ABILITY_RANGE = 15.0;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 是否正在发光（主动技能触发） */
    public boolean isGlowing = false;

    /** 发光剩余时间（tick） */
    public int glowTicksRemaining = 0;

    /** 主动技能冷却时间（tick） */
    public int abilityCooldown = 0;

    /** 是否已激活（角色分配后） */
    public boolean isActive = false;

    /**
     * 构造函数
     */
    public SuperStarPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.isGlowing = false;
        this.glowTicksRemaining = 0;
        this.abilityCooldown = 0;
        this.isActive = true;
        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 清除所有状态
     */
    public void clearAll() {
        this.isGlowing = false;
        this.glowTicksRemaining = 0;
        this.abilityCooldown = 0;
        this.isActive = false;
        // 移除发光效果
        if (player != null) {
            player.removeEffect(MobEffects.GLOWING);
        }
        this.sync();
    }

    /**
     * 检查是否为激活的明星角色
     */
    public boolean isActiveStar() {
        if (!isActive || player == null || player.level().isClientSide())
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.SUPERSTAR);
    }

    /**
     * 检查主动技能是否可用
     * 注意：此方法在客户端和服务端都可以调用
     */
    public boolean canUseAbility() {
        // 客户端只检查冷却和激活状态，服务端安全检查在网络包处理器中进行
        return abilityCooldown <= 0 && isActive;
    }

    /**
     * 使用主动技能 - 让10格范围内的玩家视野都看向自己
     * 
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (!canUseAbility()) {
            return false;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        ServerLevel world = serverPlayer.serverLevel();
        int affectedCount = 0;

        // 遍历范围内的所有玩家
        for (Player target : world.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;

            double distance = target.distanceToSqr(player);
            if (distance > ABILITY_RANGE * ABILITY_RANGE)
                continue;

            // 让目标玩家看向明星
            if (target instanceof ServerPlayer serverTarget) {
                // 计算目标应该看向的方向
                double dx = player.getX() - target.getX();
                double dy = (player.getY() + player.getEyeHeight(player.getPose()))
                        - (target.getY() + target.getEyeHeight(target.getPose()));
                double dz = player.getZ() - target.getZ();

                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));

                // 设置玩家视角
                serverTarget.connection.teleport(
                        target.getX(), target.getY(), target.getZ(),
                        yaw, pitch);

                // 给目标玩家发送提示
                serverTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.star.attracted")
                                .withStyle(ChatFormatting.GOLD),
                        true);

                affectedCount++;
            }
        }

        // 设置冷却
        this.abilityCooldown = ABILITY_COOLDOWN;

        // 播放音效
        world.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 1.0F, 1.2F);

        // 给明星发光2秒以突出效果
        startGlowing();
        int balanceAwardCount = affectedCount * 10;
        if (balanceAwardCount >= 150) {
            balanceAwardCount = 150;
        }
        SREPlayerShopComponent.KEY.get(serverPlayer).addToBalance(balanceAwardCount);
        // 发送消息给明星玩家
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.star.ability_used", affectedCount)
                        .withStyle(ChatFormatting.GOLD),
                true);

        this.sync();
        return true;
    }

    /**
     * 开始发光
     */
    private void startGlowing() {
        if (player == null)
            return;

        this.isGlowing = true;
        this.glowTicksRemaining = GLOW_DURATION;

        // 添加发光效果
        player.addEffect(new MobEffectInstance(
                MobEffects.GLOWING,
                GLOW_DURATION + 5, // 稍微多一点以确保效果
                0,
                false, // ambient
                false, // showParticles
                true // showIcon
        ));

        this.sync();
    }

    /**
     * 停止发光
     */
    private void stopGlowing() {
        this.isGlowing = false;
        this.glowTicksRemaining = 0;

        if (player != null) {
            player.removeEffect(MobEffects.GLOWING);
        }

        this.sync();
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return abilityCooldown / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ModComponents.STAR.sync(this.player);
        }
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!isActiveStar())
            return;

        // 减少主动技能冷却时间
        if (this.abilityCooldown > 0) {
            this.abilityCooldown--;
            // 每秒同步一次，减少网络压力
            if (this.abilityCooldown % 20 == 0 || this.abilityCooldown == 0) {
                this.sync();
            }
        }

        // 处理主动技能触发的发光状态
        if (this.isGlowing) {
            this.glowTicksRemaining--;
            if (this.glowTicksRemaining <= 0) {
                stopGlowing();
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isGlowing", this.isGlowing);
        tag.putInt("glowTicksRemaining", this.glowTicksRemaining);
        tag.putInt("abilityCooldown", this.abilityCooldown);
        tag.putBoolean("isActive", this.isActive);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isGlowing = tag.contains("isGlowing") && tag.getBoolean("isGlowing");
        this.glowTicksRemaining = tag.contains("glowTicksRemaining") ? tag.getInt("glowTicksRemaining") : 0;
        this.abilityCooldown = tag.contains("abilityCooldown") ? tag.getInt("abilityCooldown") : 0;
        this.isActive = tag.contains("isActive") && tag.getBoolean("isActive");
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}