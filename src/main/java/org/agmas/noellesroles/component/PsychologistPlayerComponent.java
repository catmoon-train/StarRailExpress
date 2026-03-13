package org.agmas.noellesroles.component;

import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerMoodComponent;
import io.wifi.starrailexpress.cca.StarPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 心理学家组件
 *
 * 管理"心理治疗"技能：
 * - san满70的情况下，使用技能对准一个人
 * - 对方不动，超过10秒可以把对方san回复满
 * - 3分钟冷却
 */
public class PsychologistPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<PsychologistPlayerComponent> KEY = ModComponents.PSYCHOLOGIST;

    final static boolean isStopPsychoFuncEnable = false;

    @Override
    public Player getPlayer() {
        return player;
    }
    // ==================== 常量定义 ====================

    /** 治疗持续时间（10秒 = 200 tick） */
    public static final int HEALING_DURATION = 200;

    /** 技能冷却时间（3分钟 = 3600 tick） */
    public static final int ABILITY_COOLDOWN = 3600;

    /** 目标移动阈值（超过这个距离视为移动） */
    public static final double MOVEMENT_THRESHOLD = 0.1;

    /** 最大治疗距离 */
    public static final double MAX_HEALING_DISTANCE = 5.0;

    /** san满的阈值（1.0表示满，游戏中san值范围是0.0-1.0） */
    public static final float FULL_SANITY = 1.0f;

    /** san满的阈值容差（用于浮点数比较） */
    public static final float SANITY_THRESHOLD = 0.7f;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 技能冷却时间（tick） */
    public int cooldown = 0;

    /** 当前治疗目标的UUID */
    public UUID healingTarget = null;

    /** 治疗目标名称（用于显示） */
    public String healingTargetName = "";

    /** 已治疗时间（tick） */
    public int healingTicks = 0;

    /** 是否正在治疗 */
    public boolean isHealing = false;

    /** 目标上次记录的位置 */
    private Vec3 targetLastPos = null;

    /**
     * 构造函数
     */
    public PsychologistPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void reset() {
        this.cooldown = 0;
        this.healingTarget = null;
        this.healingTargetName = "";
        this.healingTicks = 0;
        this.isHealing = false;
        this.targetLastPos = null;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        if (cooldown > 0)
            return false;

        // 检查自己的san是否满（使用阈值比较，因为san值是0.0-1.0的浮点数）
        StarPlayerMoodComponent selfMood = StarPlayerMoodComponent.KEY.get(player);
        float currentSan = selfMood.getMood();
        return currentSan >= SANITY_THRESHOLD;
    }

    /**
     * 开始治疗目标
     * 
     * @param target 目标玩家
     * @return 是否成功开始
     */
    public boolean startHealing(Player target) {
        if (!canUseAbility()) {
            if (cooldown > 0) {
                player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.on_cooldown",
                        getCooldownSeconds()), true);
            } else {
                player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.not_full_san"),
                        true);
            }
            return false;
        }

        // 验证是心理学家
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PSYCHOLOGIST)) {
            return false;
        }

        // 不能治疗自己
        if (target.getUUID().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.cannot_heal_self"),
                    true);
            return true;
        }

        // 检查目标是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(target)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.invalid_target"),
                    true);
            return false;
        }

        // 检查距离
        if (player.distanceToSqr(target) > MAX_HEALING_DISTANCE * MAX_HEALING_DISTANCE) {
            player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.too_far"), true);
            return true;
        }

        // 开始治疗
        this.healingTarget = target.getUUID();
        this.healingTargetName = target.getName().getString();
        this.healingTicks = 0;
        this.isHealing = true;
        this.targetLastPos = target.position();

        // 发送开始消息
        player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.healing_started",
                healingTargetName), true);

        // 通知目标
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.displayClientMessage(Component.translatable("message.noellesroles.psychologist.being_healed",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), true);
            if (isStopPsychoFuncEnable) {
                StarPlayerPsychoComponent ppc = StarPlayerPsychoComponent.KEY.get(serverTarget);
                if (ppc.psychoTicks > 0) {
                    this.cooldown = 60 * 20;
                    ppc.stopPsycho();
                    this.completeHealing(serverTarget);
                    return true;
                }
            }
        }

        this.sync();
        return true;
    }

    /**
     * 停止治疗
     */
    public void stopHealing(String reason) {
        if (isHealing) {
            isHealing = false;
            healingTarget = null;
            healingTargetName = "";
            healingTicks = 0;
            targetLastPos = null;

            if (reason != null && player instanceof ServerPlayer) {
                player.displayClientMessage(Component.translatable(reason).withStyle(ChatFormatting.RED), true);
            }

            this.sync();
        }
    }

    /**
     * 完成治疗
     */
    private void completeHealing(Player target) {
        // 恢复目标的san值到满（san值范围是0.0-1.0）
        StarPlayerMoodComponent targetMood = StarPlayerMoodComponent.KEY.get(target);
        targetMood.setMood(FULL_SANITY); // 1.0f 表示满san
        targetMood.sync();

        // 播放治疗完成音效
        player.level().playSound(null, target.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.5F);

        // 发送完成消息给心理学家
        if (player instanceof ServerPlayer) {
            player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.healing_complete",
                    healingTargetName).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        }

        // 发送完成消息给目标
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.displayClientMessage(Component.translatable("message.noellesroles.psychologist.healed_by",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        }

        // 设置冷却
        this.cooldown = ABILITY_COOLDOWN;

        // 重置治疗状态
        this.isHealing = false;
        this.healingTarget = null;
        this.healingTargetName = "";
        this.healingTicks = 0;
        this.targetLastPos = null;

        this.sync();
    }

    /**
     * 获取冷却时间（秒）
     */
    public int getCooldownSeconds() {
        return (cooldown + 19) / 20;
    }

    /**
     * 获取已治疗时间（秒）
     */
    public float getHealingSeconds() {
        return healingTicks / 20.0f;
    }

    /**
     * 获取治疗剩余时间（秒）
     */
    public float getRemainingHealingSeconds() {
        return (HEALING_DURATION - healingTicks) / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.PSYCHOLOGIST.sync(this.player);
    }

    /**
     * 检查是否是活跃的心理学家
     */
    public boolean isActivePsychologist() {
        if (player.level().isClientSide()) {
            // 客户端通过isHealing或cooldown判断
            return isHealing || cooldown > 0;
        }
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.PSYCHOLOGIST);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 验证是心理学家
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PSYCHOLOGIST)) {
            return;
        }

        // 减少冷却时间
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每秒同步一次
            if (this.cooldown % 200 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 处理治疗逻辑
        if (this.isHealing && this.healingTarget != null) {
            Player target = player.level().getPlayerByUUID(healingTarget);

            // 检查目标是否还存在且存活
            if (target == null || !GameFunctions.isPlayerAliveAndSurvival(target)) {
                stopHealing("message.noellesroles.psychologist.target_lost");
                return;
            }

            // 检查距离
            if (player.distanceToSqr(target) > MAX_HEALING_DISTANCE * MAX_HEALING_DISTANCE) {
                stopHealing("message.noellesroles.psychologist.target_too_far");
                return;
            }

            // 检查目标是否移动
            if (targetLastPos != null) {
                double movedDistance = target.position().distanceTo(targetLastPos);
                if (movedDistance > MOVEMENT_THRESHOLD) {
                    stopHealing("message.noellesroles.psychologist.target_moved");
                    return;
                }
            }

            // 更新目标位置
            targetLastPos = target.position();

            // 增加治疗时间
            healingTicks++;

            // 每秒同步并显示进度
            if (healingTicks % 20 == 0) {
                int seconds = healingTicks / 20;
                player.displayClientMessage(Component.translatable("message.noellesroles.psychologist.healing_progress",
                        seconds, HEALING_DURATION / 20), true);
                this.sync();
            }

            // 检查是否完成治疗
            if (healingTicks >= HEALING_DURATION) {
                completeHealing(target);
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("healingTicks", this.healingTicks);
        tag.putBoolean("isHealing", this.isHealing);
        tag.putString("healingTargetName", this.healingTargetName);
        if (this.healingTarget != null) {
            tag.putUUID("healingTarget", this.healingTarget);
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.healingTicks = tag.contains("healingTicks") ? tag.getInt("healingTicks") : 0;
        this.isHealing = tag.contains("isHealing") && tag.getBoolean("isHealing");
        this.healingTargetName = tag.contains("healingTargetName") ? tag.getString("healingTargetName") : "";
        if (tag.hasUUID("healingTarget")) {
            this.healingTarget = tag.getUUID("healingTarget");
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }
}