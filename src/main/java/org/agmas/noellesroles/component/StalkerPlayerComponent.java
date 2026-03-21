package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayerIdentifier;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 跟踪者组件
 *
 * 管理跟踪者的三阶段机制：
 * - 一阶段（潜伏者）：群体窥视积累能量
 * - 二阶段（觉醒猎手）：杀手阵营，有刀和一次免疫
 * - 三阶段（狂暴追击者）：蓄力突进处决
 */
public class StalkerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<StalkerPlayerComponent> KEY = ModComponents.STALKER;

    // ==================== 常量定义 ====================

    /** 三阶段初始时间（180秒 = 3600 tick） */
    public static final int PHASE_3_TIME = 180 * 20;

    /** 处决减少时间（60秒 = 1200 tick） */
    public static final int EXECUTION_REDUCTION = 60 * 20;

    /** 窥视视野角度（度数） */
    public static final double GAZE_ANGLE = 80.0;

    /** 窥视最大距离（格） */
    public static final double GAZE_DISTANCE = 48.0;

    /** 最小蓄力时间（1秒 = 20 tick） */
    public static final int MIN_CHARGE_TIME = 10;

    /** 最大蓄力时间（3秒 = 60 tick） */
    public static final int MAX_CHARGE_TIME = 60;

    /** 基础突进距离（格）- 缩短距离 */
    public static final double BASE_DASH_DISTANCE = 8.0;

    /** 每秒蓄力增加的突进距离（格）- 缩短距离 */
    public static final double DASH_DISTANCE_PER_SECOND = 6.0;

    /** 二阶段攻击冷却（10秒 = 200 tick） */
    public static final int PHASE_2_ATTACK_COOLDOWN = 200;

    /** 三阶段突进冷却（2秒 = 40 tick） */
    public static final int DASH_COOLDOWN = 20;

    public static final ToIntFunction<Player> MAX_SPRINT_TIME_IntSupplier = (player) -> {
        if (player == null)
            return Integer.MAX_VALUE;
        var spc = StalkerPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (spc == null)
            return Integer.MAX_VALUE;
        if (spc.phase >= 2) {
            return 0;
        } else {
            return Integer.MAX_VALUE;
        }
    };

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前阶段（1、2、3） */
    public int phase = 0;

    /** 当前能量值 */
    public int energy = 0;

    /** 二阶段击杀数 */
    public int phase2Kills = 0;

    /** 免疫是否已使用 */
    public boolean immunityUsed = false;

    /** 三阶段倒计时（tick） */
    public int phase3Timer = 0;

    /** 是否正在窥视 */
    public boolean isGazing = false;

    /** 当前窥视目标数量 */
    public int gazingTargetCount = 0;

    /** 三阶段突进模式是否激活 */
    public boolean dashModeActive = false;

    /** 是否正在蓄力 */
    public boolean isCharging = false;

    /** 蓄力时间（tick） */
    public int chargeTime = 0;

    /** 是否正在突进 */
    public boolean isDashing = false;

    /** 突进剩余距离 */
    public double dashDistanceRemaining = 0;

    /** 突进方向 */
    public Vec3 dashDirection = Vec3.ZERO;

    /** 是否已标记为跟踪者（用于在角色转换后仍能识别） */
    public boolean isStalkerMarked = false;

    /** 能量获取计时器（每秒获取一次） */
    private int energyTickCounter = 0;

    /** 二阶段攻击冷却计时器（tick） */
    public int attackCooldown = 0;

    /** 三阶段突进冷却计时器（tick） */
    public int dashCooldown = 0;

    /**
     * 构造函数
     */
    /** 一阶段进阶所需能量（基础值，实际值 = 游戏人数 × 20） */
    public int ph1_energy_need = 500;

    /** 二阶段进阶所需能量（基础值，实际值 = 游戏人数 × 2） */
    public int ph2_energy_need = 30;

    /** 二阶段进阶所需击杀数（基础值，实际值 = 游戏人数 ÷ 6，向上取整，最小为1） */
    public int ph2_kill_need = 2;

    public StalkerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.phase = 1;
        this.energy = 0;
        this.phase2Kills = 0;
        this.immunityUsed = false;
        this.phase3Timer = 0;
        this.isGazing = false;
        this.gazingTargetCount = 0;
        this.dashModeActive = false;
        this.isCharging = false;
        this.chargeTime = 0;
        this.isDashing = false;
        this.dashDistanceRemaining = 0;
        this.dashDirection = Vec3.ZERO;
        this.isStalkerMarked = true;
        this.energyTickCounter = 0;
        this.attackCooldown = 0;
        this.dashCooldown = 0;
        final var playerCount = getPlayerCount();
        int kills = (int) Math.ceil(playerCount / 6.0);
        this.ph2_kill_need = Math.max(1, kills);
        this.ph1_energy_need = playerCount * 15;
        this.ph2_energy_need = playerCount * 2;

        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 完全清除组件状态（游戏结束时调用）
     */
    public void clearAll() {
        this.phase = 0;
        this.energy = 0;
        this.phase2Kills = 0;
        this.immunityUsed = false;
        this.phase3Timer = 0;
        this.isGazing = false;
        this.gazingTargetCount = 0;
        this.dashModeActive = false;
        this.isCharging = false;
        this.chargeTime = 0;
        this.isDashing = false;
        this.dashDistanceRemaining = 0;
        this.dashDirection = Vec3.ZERO;
        this.isStalkerMarked = false;
        this.energyTickCounter = 0;
        this.attackCooldown = 0;
        this.dashCooldown = 0;
        this.sync();
    }

    /**
     * 获取当前游戏玩家人数
     */
    private int getPlayerCount() {
        if (player.level().isClientSide()) {
            return 8; // 客户端默认值
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getServer().getPlayerList().getPlayerCount();
        }
        return 8; // 默认值
    }

    /**
     * 获取一阶段进阶所需能量（游戏人数 × 20）
     */
    public int getPhase1EnergyRequired() {
        return ph1_energy_need;
    }

    /**
     * 获取二阶段进阶所需能量（游戏人数 × 2）
     */
    public int getPhase2EnergyRequired() {
        return ph2_energy_need;
    }

    /**
     * 获取二阶段进阶所需击杀数（游戏人数 ÷ 6，向上取整，最小为1）
     */
    public int getPhase2KillsRequired() {
        return ph2_kill_need;
    }

    /**
     * 添加能量
     */
    public void addEnergy(int amount) {
        this.energy += amount;
        checkPhaseAdvance();
        this.sync();
    }

    /**
     * 检查阶段进阶
     */
    public void checkPhaseAdvance() {
        if (phase == 1 && energy >= getPhase1EnergyRequired()) {
            advanceToPhase2();
        } else if (phase == 2 && energy >= getPhase2EnergyRequired() && phase2Kills >= getPhase2KillsRequired()) {
            advanceToPhase3();
        }
    }

    /**
     * 进入二阶段
     * 跟踪者一开始就是杀手阵营，二阶段只是获得刀和其他能力
     * 不需要 addRole，避免双职业问题
     * 进入二阶段后盾牌消失
     */
    public void advanceToPhase2() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            // player.displayClientMessage(
            // Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED),
            // true);
            return;
        }
        this.phase = 2;
        this.energy = 0; // 重置能量，从0开始积累30
        this.immunityUsed = true; // 进入二阶段后盾牌消失

        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 跟踪者一开始就是杀手阵营，不需要 addRole
        // 只需要给予刀
        player.addItem(ModItems.STALKER_KNIFE.getDefaultInstance());

        // 发送阶段转换消息
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.stalker.phase2_advance")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                false);

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.5F);

        this.sync();
    }

    /**
     * 进入三阶段
     */
    public void advanceToPhase3() {
        this.phase = 3;
        this.phase3Timer = PHASE_3_TIME;
        this.dashModeActive = true;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.stalker.phase3_advance")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    false);

            // 播放音效
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        this.sync();
    }

    /**
     * 退回二阶段
     */
    public void regressToPhase2() {
        this.phase = 2;
        this.dashModeActive = false;
        // 保留能量
        this.phase2Kills = 0; // 不保留击杀数
        this.phase3Timer = 0;
        this.isCharging = false;
        this.chargeTime = 0;
        this.isDashing = false;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.stalker.phase_regress")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }

        this.sync();
    }

    /**
     * 增加击杀数（二阶段用刀击杀时调用）
     */
    public void addKill() {
        if (phase >= 2) {
            this.phase2Kills++;
            // 设置攻击冷却
            this.attackCooldown = PHASE_2_ATTACK_COOLDOWN;

            // 播放击杀音效
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);

            checkPhaseAdvance();
            this.sync();
        }
    }

    /**
     * 检查二阶段攻击是否在冷却中
     */
    public boolean isAttackOnCooldown() {
        return phase >= 2 && attackCooldown > 0;
    }

    /**
     * 触发攻击冷却（刀攻击时调用）
     */
    public void triggerAttackCooldown() {
        if (phase >= 2) {
            this.attackCooldown = PHASE_2_ATTACK_COOLDOWN;
            this.sync();
        }
    }

    /**
     * 检查突进是否在冷却中
     */
    public boolean isDashOnCooldown() {
        return dashCooldown > 0;
    }

    /**
     * 获取攻击冷却秒数
     */
    public float getAttackCooldownSeconds() {
        return attackCooldown / 20.0f;
    }

    /**
     * 获取突进冷却秒数
     */
    public float getDashCooldownSeconds() {
        return dashCooldown / 20.0f;
    }

    /**
     * 三阶段处决成功时调用
     */
    public void onExecution() {
        if (phase == 3) {
            this.phase3Timer -= EXECUTION_REDUCTION;
            if (this.phase3Timer < 0) {
                this.phase3Timer = 0;
            }
            this.sync();
        }
    }

    /**
     * 开始窥视
     */
    public void startGazing() {
        this.isGazing = true;
        this.sync();
    }

    /**
     * 停止窥视
     */
    public void stopGazing() {
        this.isGazing = false;
        this.gazingTargetCount = 0;
        this.sync();
    }

    /**
     * 开始蓄力（三阶段）
     */
    public void startCharging() {
        if (phase != 3 || !dashModeActive)
            return;
        if (isDashing)
            return;
        if (dashCooldown > 0)
            return; // 突进冷却中

        this.isCharging = true;
        this.chargeTime = 0;
        this.sync();
    }

    /**
     * 停止蓄力并释放突进
     */
    public void releaseCharge() {
        if (!isCharging)
            return;

        // 检查最小蓄力时间
        if (chargeTime < MIN_CHARGE_TIME) {
            this.isCharging = false;
            this.chargeTime = 0;
            this.sync();
            return;
        }

        // 计算突进距离
        double chargeSeconds = Math.min(chargeTime, MAX_CHARGE_TIME) / 20.0;
        double dashDistance = BASE_DASH_DISTANCE + (chargeSeconds - 1.0) * DASH_DISTANCE_PER_SECOND;

        // 开始突进
        this.isCharging = false;
        this.chargeTime = 0;
        this.isDashing = true;
        this.dashDistanceRemaining = dashDistance;
        this.dashCooldown = DASH_COOLDOWN; // 设置突进冷却

        // 获取水平方向（忽略Y分量，防止穿入地板）
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 horizontalDir = new Vec3(lookDir.x, 0, lookDir.z).normalize();
        // 如果玩家正好垂直看，使用前方向
        if (horizontalDir.lengthSqr() < 0.001) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            horizontalDir = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        this.dashDirection = horizontalDir;

        // 播放突进音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, 0.5F);

        this.sync();
    }

    /**
     * 获取可见的玩家列表（用于窥视技能）
     */
    public List<Player> getVisiblePlayers() {
        List<Player> visible = new ArrayList<>();
        Level world = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);

        for (Player target : world.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;

            Vec3 targetPos = target.getEyePosition();
            double distance = eyePos.distanceTo(targetPos);
            if (distance > GAZE_DISTANCE)
                continue;

            // 视野角度检查（90度扇形，半角45度）
            Vec3 toTarget = targetPos.subtract(eyePos).normalize();
            double dot = lookDir.dot(toTarget);
            if (dot < Math.cos(Math.toRadians(GAZE_ANGLE)))
                continue;

            // 射线检测
            ClipContext context = new ClipContext(
                    eyePos, targetPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player);
            BlockHitResult hit = world.clip(context);
            if (hit.getType() == HitResult.Type.MISS ||
                    hit.getLocation().distanceTo(targetPos) < 1.0) {
                visible.add(target);
            }
        }
        return visible;
    }

    /**
     * 更新窥视状态
     */
    private void updateGazing() {
        List<Player> visible = getVisiblePlayers();
        gazingTargetCount = visible.size();

        // 每秒获取能量
        energyTickCounter++;
        if (energyTickCounter >= 20) {
            energyTickCounter = 0;
            if (gazingTargetCount > 0) {
                addEnergy(gazingTargetCount);
            }
        }
    }

    /**
     * 执行突进
     */
    private void performDash() {
        if (!isDashing || dashDistanceRemaining <= 0) {
            isDashing = false;
            dashDistanceRemaining = 0;
            sync();
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            isDashing = false;
            return;
        }

        // 每 tick 移动一定距离
        double movePerTick = 2.0; // 每 tick 移动2.0格（更快的突进速度）
        double actualMove = Math.min(movePerTick, dashDistanceRemaining);

        Vec3 currentPos = player.position();
        Vec3 newPos = currentPos.add(dashDirection.scale(actualMove));

        // 检查是否撞到方块
        ClipContext context = new ClipContext(
                currentPos.add(0, 0.5, 0), newPos.add(0, 0.5, 0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = player.level().clip(context);

        if (hit.getType() != HitResult.Type.MISS) {
            // 撞到方块，停止突进
            isDashing = false;
            dashDistanceRemaining = 0;
            sync();
            return;
        }

        // 检查是否穿过玩家
        for (Player target : player.level().players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;

            // 检查目标是否在突进路径上
            double distToTarget = currentPos.distanceTo(target.position());
            if (distToTarget <= 2.5) {
                // 命中玩家，执行处决
                executePlayer(target);
                isDashing = false;
                dashDistanceRemaining = 0;
                sync();
                return;
            }
        }

        // 使用 teleport 移动玩家（正确同步到客户端）
        serverPlayer.teleportTo(
                serverPlayer.serverLevel(),
                newPos.x, newPos.y, newPos.z,
                serverPlayer.getYRot(), serverPlayer.getXRot());

        dashDistanceRemaining -= actualMove;

        if (dashDistanceRemaining <= 0) {
            isDashing = false;
            sync();
        }
    }

    /**
     * 处决玩家
     */
    private void executePlayer(Player target) {
        if (!(player instanceof ServerPlayer))
            return;

        // 使用刀刺死因
        GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);

        // 减少三阶段倒计时
        onExecution();

        // 发送消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.stalker.execution_success", target.getName())
                            .withStyle(ChatFormatting.RED),
                    true);
        }

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    /**
     * 检查是否是活跃的跟踪者
     */
    public boolean isActiveStalker() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return false;
        if (!gameWorldComponent.isRole(player, ModRoles.STALKER))
            return false;
        return isStalkerMarked && phase > 0;
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getPhase3TimerSeconds() {
        return phase3Timer / 20.0f;
    }

    /**
     * 获取蓄力时间（秒）
     */
    public float getChargeSeconds() {
        return chargeTime / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.STALKER.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 只在跟踪者角色时处理
        if (!isActiveStalker())
            return;

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        // 二阶段及以上禁止冲刺
        if (phase >= 2 && player.isSprinting()) {
            player.setSprinting(false);
        }

        // 处理攻击冷却倒计时
        if (attackCooldown > 0) {
            attackCooldown--;
            if (attackCooldown % 20 == 0) {
                sync();
            }
        }

        // 处理突进冷却倒计时
        if (dashCooldown > 0) {
            dashCooldown--;
            if (dashCooldown == 0) {
                sync();
            }
        }

        // 窥视技能处理（一阶段或二阶段未完成击杀时）
        if (isGazing && phase <= 2) {
            updateGazing();
        }

        // 三阶段倒计时
        if (phase == 3) {
            if (phase3Timer > 0) {
                phase3Timer--;
                // 每秒同步一次
                if (phase3Timer % 20 == 0) {
                    sync();
                }
            }
            // 检查是否需要退回（分开检查，确保触发）
            if (phase3Timer <= 0 && dashModeActive) {
                regressToPhase2();
                return; // 退回后立即返回，避免后续逻辑冲突
            }
        }

        // 蓄力处理
        if (isCharging) {
            chargeTime++;
            // 限制最大蓄力时间
            if (chargeTime > MAX_CHARGE_TIME) {
                chargeTime = MAX_CHARGE_TIME;
            }
            // 蓄力时减速
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false, false));
        }

        // 突进处理
        if (isDashing) {
            performDash();
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.phase <= 0)
            return;
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning()) {
            return;
        }
        tag.putInt("phase", this.phase);
        tag.putInt("energy", this.energy);
        tag.putInt("phase2Kills", this.phase2Kills);
        tag.putBoolean("immunityUsed", this.immunityUsed);
        tag.putInt("phase3Timer", this.phase3Timer);
        tag.putBoolean("isGazing", this.isGazing);
        tag.putInt("gazingTargetCount", this.gazingTargetCount);
        tag.putBoolean("dashModeActive", this.dashModeActive);
        tag.putBoolean("isCharging", this.isCharging);
        tag.putInt("chargeTime", this.chargeTime);
        tag.putBoolean("isDashing", this.isDashing);
        tag.putDouble("dashDistanceRemaining", this.dashDistanceRemaining);
        tag.putDouble("dashDirX", this.dashDirection.x);
        tag.putDouble("dashDirY", this.dashDirection.y);
        tag.putDouble("dashDirZ", this.dashDirection.z);
        tag.putBoolean("isStalkerMarked", this.isStalkerMarked);
        tag.putInt("attackCooldown", this.attackCooldown);
        tag.putInt("dashCooldown", this.dashCooldown);
        tag.putInt("ph1_energy_need", this.ph1_energy_need);
        tag.putInt("ph2_energy_need", this.ph2_energy_need);
        tag.putInt("ph2_kill_need", this.ph2_kill_need);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.phase = tag.contains("phase") ? tag.getInt("phase") : 0;
        this.energy = tag.contains("energy") ? tag.getInt("energy") : 0;
        this.phase2Kills = tag.contains("phase2Kills") ? tag.getInt("phase2Kills") : 0;
        this.immunityUsed = tag.contains("immunityUsed") && tag.getBoolean("immunityUsed");
        this.phase3Timer = tag.contains("phase3Timer") ? tag.getInt("phase3Timer") : 0;
        this.isGazing = tag.contains("isGazing") && tag.getBoolean("isGazing");
        this.gazingTargetCount = tag.contains("gazingTargetCount") ? tag.getInt("gazingTargetCount") : 0;
        this.dashModeActive = tag.contains("dashModeActive") && tag.getBoolean("dashModeActive");
        this.isCharging = tag.contains("isCharging") && tag.getBoolean("isCharging");
        this.chargeTime = tag.contains("chargeTime") ? tag.getInt("chargeTime") : 0;
        this.isDashing = tag.contains("isDashing") && tag.getBoolean("isDashing");
        this.dashDistanceRemaining = tag.contains("dashDistanceRemaining") ? tag.getDouble("dashDistanceRemaining") : 0;
        double dirX = tag.contains("dashDirX") ? tag.getDouble("dashDirX") : 0;
        double dirY = tag.contains("dashDirY") ? tag.getDouble("dashDirY") : 0;
        double dirZ = tag.contains("dashDirZ") ? tag.getDouble("dashDirZ") : 0;
        this.dashDirection = new Vec3(dirX, dirY, dirZ);
        this.isStalkerMarked = tag.contains("isStalkerMarked") && tag.getBoolean("isStalkerMarked");
        this.attackCooldown = tag.contains("attackCooldown") ? tag.getInt("attackCooldown") : 0;
        this.dashCooldown = tag.contains("dashCooldown") ? tag.getInt("dashCooldown") : 0;
        this.ph1_energy_need = tag.contains("ph1_energy_need") ? tag.getInt("ph1_energy_need") : 500;
        this.ph2_energy_need = tag.contains("ph2_energy_need") ? tag.getInt("ph2_energy_need") : 30;
        this.ph2_kill_need = tag.contains("ph2_kill_need") ? tag.getInt("ph2_kill_need") : 2;
    }

    @Override
    public void clientTick() {
        // 二阶段及以上禁止冲刺
        if (phase >= 2 && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    public static void registerEvents() {
        OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return;
            if (victim == null)
                return;

            // 检查是否是刀击杀
            if (!deathReason.equals(GameConstants.DeathReasons.KNIFE))
                return;

            // 获取跟踪者组件
            StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(killer);

            // 检查是否是活跃的跟踪者且处于二阶段或以上
            if (stalkerComp.isActiveStalker() && stalkerComp.phase >= 2) {
                stalkerComp.addKill();
            }
        });
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}