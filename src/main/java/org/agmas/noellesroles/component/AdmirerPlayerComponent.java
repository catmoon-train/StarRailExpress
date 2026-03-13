package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.game.GameFunctions;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;

import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 慕恋者组件
 *
 */
public class AdmirerPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<AdmirerPlayerComponent> KEY = ModComponents.ADMIRER;

    @Override
    public Player getPlayer() {
        return player;
    }
    // ==================== 常量定义 ====================

    /** 进阶所需能量 */
    public static final int MAX_ENERGY = 90;

    /** 窥视视野角度（度数） */
    public static final double GAZE_ANGLE = 65.0;

    /** 窥视最大距离（格） */
    public static final double GAZE_DISTANCE = 48.0;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前能量值 */
    public int energy = 0;

    /** 是否正在窥视 */
    public boolean isGazing = false;

    /** 当前窥视目标数量 */
    public int gazingTargetCount = 0;

    /** 是否已标记为慕恋者（用于在角色转换后仍能识别） */
    public boolean isAdmirerMarked = false;

    /** 是否已转化 */
    public boolean hasTransformed = false;

    private int energyTickCounter = 0;

    private UUID boundTargetUUID = null;

    public String boundTargetName = "";

    /**
     * 构造函数
     */
    public AdmirerPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void reset() {
        this.energy = 0;
        this.isGazing = false;
        this.gazingTargetCount = 0;
        this.isAdmirerMarked = true;
        this.hasTransformed = false;
        this.energyTickCounter = 0;
        this.boundTargetUUID = null;
        this.boundTargetName = "";

        bindRandomTarget();

        this.sync();
    }

    /**
     * 完全清除组件状态（游戏结束时调用）
     */
    public void clearAll() {
        this.energy = 0;
        this.isGazing = false;
        this.gazingTargetCount = 0;
        this.isAdmirerMarked = false;
        this.hasTransformed = false;
        this.energyTickCounter = 0;
        this.boundTargetUUID = null;
        this.boundTargetName = "";
        this.sync();
    }

    public void addEnergy(int amount) {
        this.energy += amount;
        if (this.energy >= MAX_ENERGY && !hasTransformed) {
            transform();
        }
        this.sync();
    }

    /**
     * 转化为操纵师角色
     */
    private void transform() {
        if (hasTransformed)
            return;
        hasTransformed = true;

        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 获取操纵师角色
        Role selectedRole = ModRoles.MANIPULATOR;

        // 清除慕恋者标记
        this.isAdmirerMarked = true;

        // 转换角色
        RoleUtils.changeRole(player, selectedRole);

        // 触发角色分配事件 - 这会调用 assignModdedRole 来初始化角色
        // 包括给予初始金币、初始物品、重置组件等

        // 为所有杀手阵营角色（canUseKiller = true）给予初始金币
        if (selectedRole.canUseKiller()) {
            io.wifi.starrailexpress.cca.StarPlayerShopComponent shopComponent = io.wifi.starrailexpress.cca.StarPlayerShopComponent.KEY
                    .get(player);
            shopComponent.addToBalance(200);
            shopComponent.sync();
        }

        // 原版杀手不需要额外给刀（因为 onRoleAssigned 中已经处理原版杀手）

        RoleUtils.sendWelcomeAnnouncement(serverPlayer);

        // 发送转化消息
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.admirer.transform",
                        Component.translatable("announcement.star.role." + selectedRole.identifier().getPath()))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                true);

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.5F);

        this.sync();
    }

    public void bindRandomTarget() {
        Level world = player.level();
        List<Player> candidates = new ArrayList<>();

        for (Player target : world.players()) {
            if (target.equals(player))
                continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(target))
                continue;
            candidates.add(target);
        }

        if (candidates.isEmpty()) {
            this.boundTargetUUID = null;
            this.boundTargetName = "";
            return;
        }

        Random random = new Random();
        Player selected = candidates.get(random.nextInt(candidates.size()));
        this.boundTargetUUID = selected.getUUID();
        this.boundTargetName = selected.getName().getString();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.admirer.bound", boundTargetName)
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    false);
        }

        this.sync();
    }

    public Player getBoundTarget() {
        if (boundTargetUUID == null)
            return null;
        Level world = player.level();
        for (Player target : world.players()) {
            if (target.getUUID().equals(boundTargetUUID)) {
                return target;
            }
        }
        return null;
    }

    private void checkBoundTarget() {
        if (player.level().getGameTime() % 20 == 0) {
            Player boundTarget = getBoundTarget();
            var gameCp = StarGameWorldComponent.KEY.get(this.player.level());
            if (gameCp != null && gameCp.isRunning()) {
                if (GameFunctions.isPlayerAliveAndSurvival(this.player)) {
                    if (boundTarget == null || !GameFunctions.isPlayerAliveAndSurvival(boundTarget)) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.displayClientMessage(
                                    Component.translatable("message.noellesroles.admirer.target_died")
                                            .withStyle(ChatFormatting.RED),
                                    false);
                        }

                        bindRandomTarget();
                    }
                }
            }
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

    public boolean isBoundTargetVisible() {
        Player boundTarget = getBoundTarget();
        if (boundTarget == null)
            return false;
        if (!GameFunctions.isPlayerAliveAndSurvival(boundTarget))
            return false;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 targetPos = boundTarget.getEyePosition();

        double distance = eyePos.distanceTo(targetPos);
        if (distance > GAZE_DISTANCE)
            return false;

        // 视野角度检查（90度扇形，半角45度）
        Vec3 toTarget = targetPos.subtract(eyePos).normalize();
        double dot = lookDir.dot(toTarget);
        if (dot < Math.cos(Math.toRadians(GAZE_ANGLE)))
            return false;

        // 射线检测
        Level world = player.level();
        ClipContext context = new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = world.clip(context);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getLocation().distanceTo(targetPos) < 1.0;
    }

    /**
     * 更新窥视状态
     */
    private void updateGazing() {
        boolean targetVisible = isBoundTargetVisible();
        gazingTargetCount = targetVisible ? 1 : 0;

        // 每秒获取能量
        energyTickCounter++;
        if (energyTickCounter >= 20) {
            energyTickCounter = 0;
            if (targetVisible) {
                addEnergy(1);
            }
        }
    }

    /**
     * 检查是否是活跃的慕恋者
     */
    public boolean isActiveAdmirer() {
        if (!StarGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ADMIRER))
            return false;
        return isAdmirerMarked && !hasTransformed;
    }

    /**
     * 获取能量百分比
     */
    public float getEnergyPercent() {
        return (float) energy / MAX_ENERGY;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.ADMIRER.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 只在慕恋者角色时处理
        if (!isActiveAdmirer())
            return;

        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(player))
            return;
        var gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            // player.displayClientMessage(
            //         Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        // 检查绑定对象状态
        checkBoundTarget();

        // 窥视技能处理
        if (isGazing) {
            updateGazing();
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("energy", this.energy);
        tag.putBoolean("isGazing", this.isGazing);
        tag.putInt("gazingTargetCount", this.gazingTargetCount);
        tag.putBoolean("isAdmirerMarked", this.isAdmirerMarked);
        tag.putBoolean("hasTransformed", this.hasTransformed);
        if (this.boundTargetUUID != null) {
            tag.putUUID("boundTargetUUID", this.boundTargetUUID);
        }
        tag.putString("boundTargetName", this.boundTargetName);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.energy = tag.contains("energy") ? tag.getInt("energy") : 0;
        this.isGazing = tag.contains("isGazing") && tag.getBoolean("isGazing");
        this.gazingTargetCount = tag.contains("gazingTargetCount") ? tag.getInt("gazingTargetCount") : 0;
        this.isAdmirerMarked = tag.contains("isAdmirerMarked") && tag.getBoolean("isAdmirerMarked");
        this.hasTransformed = tag.contains("hasTransformed") && tag.getBoolean("hasTransformed");
        this.boundTargetUUID = tag.hasUUID("boundTargetUUID") ? tag.getUUID("boundTargetUUID") : null;
        this.boundTargetName = tag.contains("boundTargetName") ? tag.getString("boundTargetName") : "";
    }

    @Override
    public void clear() {
        this.energy = 0;
        this.isGazing = false;
        this.gazingTargetCount = 0;
        this.isAdmirerMarked = false;
        this.hasTransformed = false;
        this.energyTickCounter = 0;
        this.boundTargetUUID = null;
        this.boundTargetName = "";
        this.sync();
    }
}