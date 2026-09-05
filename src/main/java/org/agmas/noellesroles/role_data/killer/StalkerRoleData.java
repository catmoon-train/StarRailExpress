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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.content.item.StalkerKnifeItem;
import org.agmas.noellesroles.gunfx.StalkerDashTrails;
import org.agmas.noellesroles.gunfx.StalkerPierceFx;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

public class StalkerRoleData extends SimpleRoleData {

    /** 组件键 - 用于从玩家获取此组件 */


    // ==================== 常量定义 ====================

    /** 刺客形态持续时间（30秒） */
    public static final int ASSASSIN_FORM_DURATION = 30 * 20;

    /** 刺客形态购买冷却（2分30秒） */
    public static final int ASSASSIN_FORM_COOLDOWN = 150 * 20;

    /** 攻击冲刺回充时间（5秒） */
    public static final int ATTACK_DASH_RECHARGE_TIME = 5 * 20;

    public static final int MAX_NORMAL_DASH_CHARGES = 1;
    public static final int MAX_ATTACK_DASH_CHARGES = 1;

    /** 攻击蓄力释放后，技能冷却（3.5 秒） */
    public static final int ATTACK_DASH_SKILL_COOLDOWN = 70;

    /** 潜行者匕首固定攻击冷却（5秒，仅刺客形态命中时写入刀冷却） */
    public static final int KNIFE_ATTACK_COOLDOWN = 5 * 20;

    private static final double NORMAL_DASH_SPEED = 1.15;
    private static final int NORMAL_DASH_TICKS = 7;
    private static final int MAX_ATTACK_DASH_HITS = 2;

    /** 窥视视野角度（度数） */
    public static final double GAZE_ANGLE = 80.0;

    /** 窥视最大距离（格） */
    public static final double GAZE_DISTANCE = 48.0;

    /** 最小蓄力时间（0.5 秒） */
    public static final int MIN_CHARGE_TIME = 10;

    /** 最大蓄力时间（3秒 = 60 tick） */
    public static final int MAX_CHARGE_TIME = 60;

    /** 基础突进距离（格）- 缩短距离 */
    public static final double BASE_DASH_DISTANCE = 8.0;

    /** 每秒蓄力增加的突进距离（格）- 缩短距离 */
    public static final double DASH_DISTANCE_PER_SECOND = 6.0;

    public static final ToIntFunction<Player> MAX_SPRINT_TIME_IntSupplier = (player) -> {
        if (player == null)
            return Integer.MAX_VALUE;
        var spc = RoleData.getNullable(StalkerRoleData.class, player);
        if (!RoleData.isAttached(spc))
            return Integer.MAX_VALUE;
        if (spc.isAssassinFormActive()) {
            return Integer.MAX_VALUE;
        }
        if (spc.phase >= 2) {
            return 0;
        } else {
            return Integer.MAX_VALUE;
        }
    };

    // ==================== 状态变量 ====================


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

    /** 向量冲刺的速度与剩余 tick，仅服务端使用。 */
    private double dashSpeed = 0;
    private int dashTicksLeft = 0;
    private Vec3 lastDashPos = Vec3.ZERO;
    private boolean dashHasTraveled = false;

    /** 是否已标记为跟踪者（用于在角色转换后仍能识别） */
    public boolean isStalkerMarked = false;

    /** 能量获取计时器（每秒获取一次） */
    private int energyTickCounter = 0;

    /** 三阶段突进冷却计时器（tick） */
    public int dashCooldown = 0;

    /** 刺客形态再次购买的剩余冷却 */
    public int assassinFormCooldown = 0;

    /** 普通冲刺储量；攻击冲刺击倒玩家时补充 */
    public int normalDashCharges = 0;

    /** 攻击冲刺储量；最多一层 */
    public int attackDashCharges = 0;

    /** 攻击冲刺下一次回充的剩余时间 */
    public int attackDashRechargeTimer = 0;

    /** 当前冲刺是否为会造成伤害的攻击冲刺 */
    public boolean attackDashActive = false;

    /** 当前攻击冲刺已命中的敌人数 */
    public int attackDashHitCount = 0;

    /** 是否正按住潜行键挂在墙上 */
    public boolean wallHanging = false;

    /** 墙面朝向玩家的法线，用于让视角随墙面方向对齐 */
    public Vec3 wallNormal = Vec3.ZERO;

    /** 服务端一次攻击冲刺内已经命中的玩家，防止重复命中。 */
    private final Set<UUID> dashHitPlayers = new HashSet<>();

    /**
     * 构造函数
     */
    /** 一阶段进阶所需能量（基础值，实际值 = 游戏人数 × 20） */
    public int ph1_energy_need = 500;

    /** 二阶段进阶所需能量（基础值，实际值 = 游戏人数 × 2） */
    public int ph2_energy_need = 30;

    /** 二阶段进阶所需击杀数（基础值，实际值 = 游戏人数 ÷ 6，向上取整，最小为1） */
    public int ph2_kill_need = 2;

    public StalkerRoleData(RoleDataContext context) {
        super(context);
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
        this.dashSpeed = 0;
        this.dashTicksLeft = 0;
        this.lastDashPos = Vec3.ZERO;
        this.dashHasTraveled = false;
        this.isStalkerMarked = true;
        this.energyTickCounter = 0;
        this.dashCooldown = 0;
        this.assassinFormCooldown = 0;
        this.normalDashCharges = 0;
        this.attackDashCharges = 0;
        this.attackDashRechargeTimer = 0;
        this.attackDashActive = false;
        this.attackDashHitCount = 0;
        this.wallHanging = false;
        this.wallNormal = Vec3.ZERO;
        this.dashHitPlayers.clear();
        final var playerCount = getPlayerCount();
        int kills = (int) Math.ceil(playerCount / 6.0);
        this.ph2_kill_need = Math.max(1, (int) ((float) kills / 1.5));
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
        this.dashSpeed = 0;
        this.dashTicksLeft = 0;
        this.lastDashPos = Vec3.ZERO;
        this.dashHasTraveled = false;
        this.isStalkerMarked = false;
        this.energyTickCounter = 0;
        this.dashCooldown = 0;
        this.assassinFormCooldown = 0;
        this.normalDashCharges = 0;
        this.attackDashCharges = 0;
        this.attackDashRechargeTimer = 0;
        this.attackDashActive = false;
        this.attackDashHitCount = 0;
        this.wallHanging = false;
        this.wallNormal = Vec3.ZERO;
        this.dashHitPlayers.clear();
        if (player != null) {
            player.setNoGravity(false);
        }
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
        ConfigWorldComponent.onPlayerUsedSkill( (ServerPlayer) player);
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
    public boolean activateAssassinForm() {
        if (!canActivateAssassinForm()) {
            return false;
        }
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (!psycho.startPsycho_time(ASSASSIN_FORM_DURATION, GameConstants.getPsychoModeArmour(), true)) {
            return false;
        }
        this.phase = 3;
        this.phase3Timer = psycho.getPsychoTicks();
        this.assassinFormCooldown = ASSASSIN_FORM_COOLDOWN;
        this.dashModeActive = true;
        this.normalDashCharges = 0;
        this.attackDashCharges = MAX_ATTACK_DASH_CHARGES;
        this.attackDashRechargeTimer = 0;
        this.isCharging = false;
        this.chargeTime = 0;
        stopDash();
        refreshNoCollide();

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
        return true;
    }

    public boolean canActivateAssassinForm() {
        return isActiveStalker() && phase >= 2 && phase != 3 && assassinFormCooldown <= 0
                && !SREPlayerPsychoComponent.KEY.get(player).inPsycho();
    }

    public boolean isAssassinFormActive() {
        return phase == 3 && dashModeActive && phase3Timer > 0;
    }

    public float getAssassinFormCooldownSeconds() {
        return assassinFormCooldown / 20.0F;
    }

    public float getAttackDashRechargeSeconds() {
        return attackDashRechargeTimer / 20.0F;
    }

    /** 刺客形态结束时补回主手猎刀，避免 Psycho 回收把二阶段武器清掉。 */
    public void ensureMainHuntingKnife() {
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (player.getMainHandItem().is(ModItems.STALKER_KNIFE)
                || player.getOffhandItem().is(ModItems.STALKER_KNIFE)) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.STALKER_KNIFE)) {
                return;
            }
        }
        player.addItem(ModItems.STALKER_KNIFE.getDefaultInstance());
    }

    /**
     * 退回二阶段
     */
    public void regressToPhase2() {
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.inPsycho()) {
            psycho.stopPsychoAndSync();
        }
        this.phase = 2;
        this.dashModeActive = false;
        // 保留能量
        this.phase2Kills = 0; // 不保留击杀数
        this.phase3Timer = 0;
        this.isCharging = false;
        this.chargeTime = 0;
        this.normalDashCharges = 0;
        this.attackDashCharges = 0;
        this.attackDashRechargeTimer = 0;
        stopDash();
        setWallHanging(false, Vec3.ZERO);
        if (player != null) {
            player.removeEffect(ModEffects.NO_COLLIDE);
        }
        ensureMainHuntingKnife();

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
            if (isAssassinFormActive() && attackDashActive
                    && normalDashCharges < MAX_NORMAL_DASH_CHARGES) {
                normalDashCharges++;
            }

            // 播放击杀音效
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);

            checkPhaseAdvance();
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
     * 获取突进冷却秒数
     */
    public float getDashCooldownSeconds() {
        return dashCooldown / 20.0f;
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

    public boolean canStartAttackDashCharge() {
        return isAssassinFormActive() && !isDashing && !isCharging && hasReadyHuntingKnife()
                && !isAttackDashSkillOnCooldown();
    }

    /** 右键 usingItem 开始：进入攻击冲刺蓄力。 */
    public boolean startAttackDashCharge() {
        if (!canStartAttackDashCharge()) {
            return false;
        }
        setWallHanging(false, Vec3.ZERO);
        this.isCharging = true;
        this.chargeTime = 0;
        sync();
        return true;
    }

    /** 松开 usingItem：蓄力足够则向量冲刺，并让技能进入 3.5 秒冷却。 */
    public boolean releaseAttackDash() {
        int used = player != null && player.isUsingItem() ? player.getTicksUsingItem() : chargeTime;
        return releaseAttackDash(used);
    }

    public boolean releaseAttackDash(int usedTicks) {
        if (!isAssassinFormActive() || isDashing) {
            this.isCharging = false;
            this.chargeTime = 0;
            return false;
        }
        this.chargeTime = usedTicks;
        if (usedTicks < MIN_CHARGE_TIME || !hasReadyHuntingKnife() || isAttackDashSkillOnCooldown()) {
            this.isCharging = false;
            this.chargeTime = 0;
            sync();
            return false;
        }
        float charge = Mth.clamp(usedTicks / (float) MAX_CHARGE_TIME, 0.35F, 1.0F);
        double speed = 1.20D + 0.55D * charge;
        int ticks = 7 + Math.round(5.0F * charge);
        beginDash(true, speed, ticks);
        SRERole.getAbilityComponent(player).setCooldown(ATTACK_DASH_SKILL_COOLDOWN);
        sync();
        return true;
    }

    private boolean isAttackDashSkillOnCooldown() {
        return player != null && SRERole.getAbilityComponent(player).hasCooldown();
    }

    /** Q键释放普通冲刺，储量来自攻击冲刺造成的真实击倒。 */
    public boolean tryStartNormalDash() {
        if (!isAssassinFormActive() || isDashing || normalDashCharges <= 0 || isAttackDashSkillOnCooldown()) {
            return false;
        }
        this.isCharging = false;
        this.chargeTime = 0;
        normalDashCharges--;
        beginDash(false, NORMAL_DASH_SPEED, NORMAL_DASH_TICKS);
        sync();
        return true;
    }

    private void beginDash(boolean attack, double speed, int ticks) {
        setWallHanging(false, Vec3.ZERO);
        Vec3 look = player.getViewVector(1.0f);
        if (look.lengthSqr() < 1.0E-4) {
            return;
        }
        this.isCharging = false;
        this.chargeTime = 0;
        this.isDashing = true;
        this.attackDashActive = attack;
        this.attackDashHitCount = 0;
        this.dashHitPlayers.clear();
        this.dashDirection = look.normalize();
        this.dashSpeed = speed;
        this.dashTicksLeft = ticks;
        this.lastDashPos = player.position();
        this.dashHasTraveled = false;
        applyDashVelocity();
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, attack ? 0.7F : 1.2F);
    }

    private void stopDash() {
        this.isDashing = false;
        this.attackDashActive = false;
        this.attackDashHitCount = 0;
        this.dashDistanceRemaining = 0;
        this.dashDirection = Vec3.ZERO;
        this.dashSpeed = 0;
        this.dashTicksLeft = 0;
        this.lastDashPos = Vec3.ZERO;
        this.dashHasTraveled = false;
        this.dashHitPlayers.clear();
    }

    /** 主手持猎刀，或主手空着且副手持猎刀时，可以蓄力冲刺。 */
    public static boolean isHoldingHuntingKnife(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getMainHandItem().getItem() instanceof StalkerKnifeItem) {
            return true;
        }
        return player.getMainHandItem().isEmpty()
                && player.getOffhandItem().getItem() instanceof StalkerKnifeItem;
    }

    public boolean hasReadyHuntingKnife() {
        return isEquippedKnifeReady(player.getMainHandItem())
                || isEquippedKnifeReady(player.getOffhandItem())
                || isHuntingKnifeReady(ModItems.STALKER_KNIFE)
                || isHuntingKnifeReady(ModItems.STALKER_KNIFE_OFFHAND);
    }

    private boolean isEquippedKnifeReady(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof StalkerKnifeItem
                && !player.getCooldowns().isOnCooldown(stack.getItem());
    }

    private boolean isHuntingKnifeReady(Item item) {
        return player.getInventory().countItem(item) > 0 && !player.getCooldowns().isOnCooldown(item);
    }

    /** 命中时把一把尚未冷却的猎刀打上 5 秒冷却。 */
    public boolean consumeReadyKnifeForAttack() {
        if (isEquippedKnifeReady(player.getMainHandItem())) {
            player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), KNIFE_ATTACK_COOLDOWN);
            return true;
        }
        if (isEquippedKnifeReady(player.getOffhandItem())) {
            player.getCooldowns().addCooldown(player.getOffhandItem().getItem(), KNIFE_ATTACK_COOLDOWN);
            return true;
        }
        if (isHuntingKnifeReady(ModItems.STALKER_KNIFE)) {
            player.getCooldowns().addCooldown(ModItems.STALKER_KNIFE, KNIFE_ATTACK_COOLDOWN);
            return true;
        }
        if (isHuntingKnifeReady(ModItems.STALKER_KNIFE_OFFHAND)) {
            player.getCooldowns().addCooldown(ModItems.STALKER_KNIFE_OFFHAND, KNIFE_ATTACK_COOLDOWN);
            return true;
        }
        return false;
    }

    private void refreshNoCollide() {
        if (player == null || player.level().isClientSide) {
            return;
        }
        int duration = Math.max(phase3Timer + 5, 20);
        var existing = player.getEffect(ModEffects.NO_COLLIDE);
        if (existing == null || existing.getDuration() < 15) {
            player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, duration, 0, false, false, false));
        }
    }

    private void applyDashVelocity() {
        if (dashDirection.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 velocity = dashDirection.scale(dashSpeed);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer.getId(), velocity));
        }
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
     * 执行突进：只维持速度向量，交给原版位移，不再每 tick 传送或强制 move。
     */
    private void performDash() {
        if (!isDashing || dashTicksLeft <= 0) {
            stopDash();
            sync();
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            stopDash();
            return;
        }

        Vec3 currentPos = player.position();
        if (lastDashPos == Vec3.ZERO) {
            lastDashPos = currentPos;
        }
        Vec3 moved = currentPos.subtract(lastDashPos);
        boolean movedThisTick = moved.lengthSqr() > 0.0025D;

        Vec3 lookAhead = dashDirection.scale(Math.max(dashSpeed, 0.35D));
        BlockHitResult wallHit = player.level().clip(new ClipContext(
                currentPos.add(0, 0.5, 0), currentPos.add(lookAhead).add(0, 0.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wallHit.getType() != HitResult.Type.MISS
                && wallHit.getLocation().distanceToSqr(currentPos.add(0, 0.5, 0)) < dashSpeed * dashSpeed) {
            stopDash();
            player.setDeltaMovement(Vec3.ZERO);
            sync();
            return;
        }

        if (dashHasTraveled && !movedThisTick) {
            stopDash();
            player.setDeltaMovement(Vec3.ZERO);
            sync();
            return;
        }

        if (movedThisTick) {
            dashHasTraveled = true;
            if (attackDashActive) {
                var sweptBox = player.getBoundingBox()
                        .expandTowards(-moved.x, -moved.y, -moved.z)
                        .inflate(0.75);
                var targets = player.level().players().stream()
                        .filter(target -> !target.equals(player))
                        .filter(GameUtils::isPlayerAliveAndSurvival)
                        .filter(target -> !dashHitPlayers.contains(target.getUUID()))
                        .filter(target -> sweptBox.intersects(target.getBoundingBox()))
                        .sorted((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                        .toList();
                for (Player target : targets) {
                    if (!consumeReadyKnifeForAttack()) {
                        break;
                    }
                    dashHitPlayers.add(target.getUUID());
                    attackDashHitCount++;
                    StalkerPierceFx.broadcast(serverPlayer, target, dashDirection);
                    executePlayer(target);
                    if (attackDashHitCount >= MAX_ATTACK_DASH_HITS) {
                        break;
                    }
                }
            }
            StalkerDashTrails.broadcast(serverPlayer, lastDashPos, currentPos, attackDashActive);
        }

        applyDashVelocity();
        lastDashPos = currentPos;
        dashTicksLeft--;

        if (dashTicksLeft <= 0 || (attackDashActive && attackDashHitCount >= MAX_ATTACK_DASH_HITS)) {
            stopDash();
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

        // 护盾或其它死亡否决生效时只算命中，不算击倒。
        if (GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }

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
     * 刺客形态按住 Shift 时寻找相邻墙面并挂住；松开 Shift 立即恢复重力。
     * 墙面命中面的法线同时用于把玩家视角调整为背靠墙面的朝向。
     */
    private void updateWallHang() {
        if (!isAssassinFormActive() || isDashing || !player.isShiftKeyDown()) {
            setWallHanging(false, Vec3.ZERO);
            return;
        }

        Vec3 normal = findAdjacentWallNormal();
        if (normal == null) {
            setWallHanging(false, Vec3.ZERO);
            return;
        }

        boolean changedWall = !wallHanging || wallNormal.distanceToSqr(normal) > 0.01;
        setWallHanging(true, normal);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;

        if (changedWall && player instanceof ServerPlayer serverPlayer) {
            float yaw = (float) Math.toDegrees(Math.atan2(-normal.x, normal.z));
            serverPlayer.teleportTo(serverPlayer.serverLevel(), player.getX(), player.getY(), player.getZ(),
                    yaw, player.getXRot());
        }
    }

    private Vec3 findAdjacentWallNormal() {
        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 end = origin.add(direction.getStepX() * 0.8, 0, direction.getStepZ() * 0.8);
            BlockHitResult hit = player.level().clip(new ClipContext(origin, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis().isHorizontal()) {
                Direction face = hit.getDirection();
                return new Vec3(face.getStepX(), 0, face.getStepZ());
            }
        }
        return null;
    }

    private void setWallHanging(boolean hanging, Vec3 normal) {
        boolean changed = this.wallHanging != hanging || this.wallNormal.distanceToSqr(normal) > 0.01;
        this.wallHanging = hanging;
        this.wallNormal = normal;
        player.setNoGravity(hanging);
        if (!hanging) {
            player.fallDistance = 0;
        }
        if (changed && !player.level().isClientSide) {
            sync();
        }
    }

    /**
     * 检查是否是活跃的跟踪者
     */
    public boolean isActiveStalker() {
        if (player == null)
            return false;
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

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 只在跟踪者角色时处理
        if (!isActiveStalker())
            return;

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        // 二阶段禁止奔跑；刺客形态可以跑
        if (phase >= 2 && !isAssassinFormActive() && player.isSprinting()) {
            player.setSprinting(false);
        }

        if (assassinFormCooldown > 0) {
            assassinFormCooldown--;
            if (assassinFormCooldown == 0 || assassinFormCooldown % 200 == 0) {
                sync();
            }
        }

        // 窥视技能处理（一阶段或二阶段未完成击杀时）
        if (isGazing && phase <= 2) {
            updateGazing();
        }

        // 刺客形态倒计时与攻击冲刺回充
        if (isAssassinFormActive()) {
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
            if (!psycho.inPsycho()) {
                regressToPhase2();
                return;
            }
            int psychoTicks = psycho.getPsychoTicks();
            if (phase3Timer != psychoTicks) {
                phase3Timer = psychoTicks;
                if (phase3Timer % 200 == 0) {
                    sync();
                }
            }

            refreshNoCollide();

            if (player.isUsingItem() && player.getUseItem().getItem() instanceof StalkerKnifeItem) {
                chargeTime = player.getTicksUsingItem();
                isCharging = true;
                if (chargeTime >= MAX_CHARGE_TIME) {
                    player.releaseUsingItem();
                } else if (chargeTime % 4 == 0) {
                    sync();
                }
            }

            updateWallHang();
            if (wallHanging && (isCharging || player.isUsingItem())) {
                if (player.isUsingItem()) {
                    player.stopUsingItem();
                }
                isCharging = false;
                chargeTime = 0;
                sync();
            }

            if (phase3Timer <= 0) {
                regressToPhase2();
                return;
            }
        } else if (wallHanging) {
            setWallHanging(false, Vec3.ZERO);
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
        // 不能因为游戏尚未 isRunning() 就跳过同步。
        // 跟踪者被赋予角色时（onInit -> init()）通常早于游戏进入 ACTIVE 状态，
        // 若此处提前 return，客户端就收不到 phase / isStalkerMarked，导致 HUD 在
        // 玩家尚未通过技能获取能量前完全不显示，只能等首次 addEnergy 同步后才出现。
        // HUD 自身的 isActiveStalker() 已用 phase>0 && isStalkerMarked 做显示闸门，
        // 因此这里无条件同步核心状态即可，无需依赖 isRunning()。
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
        tag.putDouble("dashSpeed", this.dashSpeed);
        tag.putDouble("dashDistanceRemaining", this.dashDistanceRemaining);
        tag.putDouble("dashDirX", this.dashDirection.x);
        tag.putDouble("dashDirY", this.dashDirection.y);
        tag.putDouble("dashDirZ", this.dashDirection.z);
        tag.putBoolean("isStalkerMarked", this.isStalkerMarked);
        tag.putInt("dashCooldown", this.dashCooldown);
        tag.putInt("assassinFormCooldown", this.assassinFormCooldown);
        tag.putInt("normalDashCharges", this.normalDashCharges);
        tag.putInt("attackDashCharges", this.attackDashCharges);
        tag.putInt("attackDashRechargeTimer", this.attackDashRechargeTimer);
        tag.putBoolean("attackDashActive", this.attackDashActive);
        tag.putInt("attackDashHitCount", this.attackDashHitCount);
        tag.putBoolean("wallHanging", this.wallHanging);
        tag.putDouble("wallNormalX", this.wallNormal.x);
        tag.putDouble("wallNormalZ", this.wallNormal.z);
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
        this.dashSpeed = tag.contains("dashSpeed") ? tag.getDouble("dashSpeed") : 0;
        this.dashDistanceRemaining = tag.contains("dashDistanceRemaining") ? tag.getDouble("dashDistanceRemaining") : 0;
        double dirX = tag.contains("dashDirX") ? tag.getDouble("dashDirX") : 0;
        double dirY = tag.contains("dashDirY") ? tag.getDouble("dashDirY") : 0;
        double dirZ = tag.contains("dashDirZ") ? tag.getDouble("dashDirZ") : 0;
        this.dashDirection = new Vec3(dirX, dirY, dirZ);
        this.isStalkerMarked = tag.contains("isStalkerMarked") && tag.getBoolean("isStalkerMarked");
        this.dashCooldown = tag.contains("dashCooldown") ? tag.getInt("dashCooldown") : 0;
        this.assassinFormCooldown = tag.contains("assassinFormCooldown") ? tag.getInt("assassinFormCooldown") : 0;
        this.normalDashCharges = tag.contains("normalDashCharges") ? tag.getInt("normalDashCharges") : 0;
        this.attackDashCharges = tag.contains("attackDashCharges") ? tag.getInt("attackDashCharges") : 0;
        this.attackDashRechargeTimer = tag.contains("attackDashRechargeTimer")
                ? tag.getInt("attackDashRechargeTimer") : 0;
        this.attackDashActive = tag.contains("attackDashActive") && tag.getBoolean("attackDashActive");
        this.attackDashHitCount = tag.contains("attackDashHitCount") ? tag.getInt("attackDashHitCount") : 0;
        this.wallHanging = tag.contains("wallHanging") && tag.getBoolean("wallHanging");
        this.wallNormal = new Vec3(tag.contains("wallNormalX") ? tag.getDouble("wallNormalX") : 0,
                0, tag.contains("wallNormalZ") ? tag.getDouble("wallNormalZ") : 0);
        this.ph1_energy_need = tag.contains("ph1_energy_need") ? tag.getInt("ph1_energy_need") : 500;
        this.ph2_energy_need = tag.contains("ph2_energy_need") ? tag.getInt("ph2_energy_need") : 30;
        this.ph2_kill_need = tag.contains("ph2_kill_need") ? tag.getInt("ph2_kill_need") : 2;
    }

    @Override
    public void writeToRewindNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 回溯快照：绕过 writeToSyncNbt 的 phase<=0 守卫，始终写入完整状态
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
        tag.putDouble("dashSpeed", this.dashSpeed);
        tag.putDouble("dashDistanceRemaining", this.dashDistanceRemaining);
        tag.putDouble("dashDirX", this.dashDirection.x);
        tag.putDouble("dashDirY", this.dashDirection.y);
        tag.putDouble("dashDirZ", this.dashDirection.z);
        tag.putBoolean("isStalkerMarked", this.isStalkerMarked);
        tag.putInt("dashCooldown", this.dashCooldown);
        tag.putInt("assassinFormCooldown", this.assassinFormCooldown);
        tag.putInt("normalDashCharges", this.normalDashCharges);
        tag.putInt("attackDashCharges", this.attackDashCharges);
        tag.putInt("attackDashRechargeTimer", this.attackDashRechargeTimer);
        tag.putBoolean("attackDashActive", this.attackDashActive);
        tag.putInt("attackDashHitCount", this.attackDashHitCount);
        tag.putBoolean("wallHanging", this.wallHanging);
        tag.putDouble("wallNormalX", this.wallNormal.x);
        tag.putDouble("wallNormalZ", this.wallNormal.z);
        tag.putInt("ph1_energy_need", this.ph1_energy_need);
        tag.putInt("ph2_energy_need", this.ph2_energy_need);
        tag.putInt("ph2_kill_need", this.ph2_kill_need);
    }

    @Override
    public void readFromRewindNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 回溯恢复：纯回填字段，不触发 sync()
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
        this.dashSpeed = tag.contains("dashSpeed") ? tag.getDouble("dashSpeed") : 0;
        this.dashDistanceRemaining = tag.contains("dashDistanceRemaining") ? tag.getDouble("dashDistanceRemaining") : 0;
        double dirX = tag.contains("dashDirX") ? tag.getDouble("dashDirX") : 0;
        double dirY = tag.contains("dashDirY") ? tag.getDouble("dashDirY") : 0;
        double dirZ = tag.contains("dashDirZ") ? tag.getDouble("dashDirZ") : 0;
        this.dashDirection = new Vec3(dirX, dirY, dirZ);
        this.isStalkerMarked = tag.contains("isStalkerMarked") && tag.getBoolean("isStalkerMarked");
        this.dashCooldown = tag.contains("dashCooldown") ? tag.getInt("dashCooldown") : 0;
        this.assassinFormCooldown = tag.contains("assassinFormCooldown") ? tag.getInt("assassinFormCooldown") : 0;
        this.normalDashCharges = tag.contains("normalDashCharges") ? tag.getInt("normalDashCharges") : 0;
        this.attackDashCharges = tag.contains("attackDashCharges") ? tag.getInt("attackDashCharges") : 0;
        this.attackDashRechargeTimer = tag.contains("attackDashRechargeTimer")
                ? tag.getInt("attackDashRechargeTimer") : 0;
        this.attackDashActive = tag.contains("attackDashActive") && tag.getBoolean("attackDashActive");
        this.attackDashHitCount = tag.contains("attackDashHitCount") ? tag.getInt("attackDashHitCount") : 0;
        this.wallHanging = tag.contains("wallHanging") && tag.getBoolean("wallHanging");
        this.wallNormal = new Vec3(tag.contains("wallNormalX") ? tag.getDouble("wallNormalX") : 0,
                0, tag.contains("wallNormalZ") ? tag.getDouble("wallNormalZ") : 0);
        this.ph1_energy_need = tag.contains("ph1_energy_need") ? tag.getInt("ph1_energy_need") : 500;
        this.ph2_energy_need = tag.contains("ph2_energy_need") ? tag.getInt("ph2_energy_need") : 30;
        this.ph2_kill_need = tag.contains("ph2_kill_need") ? tag.getInt("ph2_kill_need") : 2;
    }

    @Override
    public void clientTick() {
        // 二阶段禁止奔跑；刺客形态可以跑
        if (phase >= 2 && !isAssassinFormActive() && player.isSprinting()) {
            player.setSprinting(false);
        }
        if (assassinFormCooldown > 1) {
            assassinFormCooldown--;
        }
        if (isAssassinFormActive()) {
            if (phase3Timer > 1) {
                phase3Timer--;
            }
            if (player.isUsingItem() && player.getUseItem().getItem() instanceof StalkerKnifeItem) {
                chargeTime = player.getTicksUsingItem();
                isCharging = true;
            } else if (isCharging && chargeTime < MAX_CHARGE_TIME) {
                chargeTime++;
            }
            if (isDashing && dashDirection.lengthSqr() > 1.0E-4D) {
                double speed = dashSpeed > 0 ? dashSpeed : 1.15D;
                player.setDeltaMovement(dashDirection.scale(speed));
                player.fallDistance = 0;
            }
            if (wallHanging) {
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
            }
        }
    }

    public static void registerEvents() {
        // 监听死亡确认后的事件（OnPlayerDeathWithKiller 在所有 allowDeath / 护盾拦截判定通过后才触发），
        // 确保只有真正击杀玩家才会充能，刀到免疫/护盾玩家（未真正死亡）时不会计入击杀。
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return;
            if (victim == null)
                return;

            // 检查是否是刀击杀
            if (!deathReason.equals(GameConstants.DeathReasons.KNIFE))
                return;

            // 获取跟踪者组件
            StalkerRoleData stalkerComp = RoleData.getNullable(StalkerRoleData.class, killer);
            if (stalkerComp == null)
                return;

            // 检查是否是活跃的跟踪者且处于二阶段或以上
            if (stalkerComp.isActiveStalker() && stalkerComp.phase >= 2) {
                stalkerComp.addKill();
            }
        });
    }


}
