package org.agmas.noellesroles.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 私家侦探组件
 *
 * 管理私家侦探的技能状态：
 * - 审查技能冷却时间（60秒）
 * - 当前正在查看的目标玩家
 * - 目标玩家移动检测
 */
public class DetectivePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<DetectivePlayerComponent> KEY = ModComponents.DETECTIVE;

    // 审查技能冷却时间（单位：tick，20 tick = 1秒）
    public static final int INSPECT_COOLDOWN = 60 * 20; // 60秒

    // 审查费用（金币）
    public static final int INSPECT_COST = 200;

    // 持有该组件的玩家
    private final Player player;

    public int conspiratorInstinctTime = 0;
    // 技能冷却时间（tick）
    public int cooldown = 0;

    // 当前正在查看的目标玩家UUID（null表示没有查看任何人）
    private ServerPlayer inspectingTarget = null;

    // 目标玩家的上一次位置（用于检测移动）
    private double lastTargetX = 0;
    private double lastTargetY = 0;
    private double lastTargetZ = 0;

    public void triggerConspiratorInstinct(int time){
        this.conspiratorInstinctTime = time;
        this.sync();
    }
    /**
     * 构造函数
     */
    public DetectivePlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void reset() {
        this.cooldown = 0;
        this.inspectingTarget = null;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    /**
     * 开始审查目标玩家
     * 
     * @param target 目标玩家
     */
    public void startInspecting(ServerPlayer target) {
        this.inspectingTarget = target;
        this.lastTargetX = target.getX();
        this.lastTargetY = target.getY();
        this.lastTargetZ = target.getZ();
    }

    /**
     * 停止审查
     */
    public void stopInspecting() {
        this.inspectingTarget = null;
    }

    /**
     * 获取当前正在查看的目标
     */
    public ServerPlayer getInspectingTarget() {
        return this.inspectingTarget;
    }

    /**
     * 检查是否正在审查某个玩家
     */
    public boolean isInspecting() {
        return this.inspectingTarget != null;
    }

    /**
     * 设置冷却时间
     */
    public void setCooldown(int ticks) {
        this.cooldown = ticks;
        this.sync();
    }

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        return cooldown <= 0;
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.DETECTIVE.sync(this.player);
    }

    @Override
    public void serverTick() {
        // 减少冷却时间
        if (this.conspiratorInstinctTime > 0) {
            this.conspiratorInstinctTime--;
            if (this.conspiratorInstinctTime % 60 == 0 || this.conspiratorInstinctTime == 0) {
                this.sync();
            }
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每秒同步一次
            if (this.cooldown % 60 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 检查目标是否移动
        if (this.inspectingTarget != null) {
            // 如果目标玩家已离线或死亡
            if (this.inspectingTarget.hasDisconnected() || this.inspectingTarget.isDeadOrDying()) {
                closeInspectScreen();
                return;
            }

            // 检测目标是否移动（允许小幅度的抖动，阈值0.1格）
            double dx = Math.abs(this.inspectingTarget.getX() - this.lastTargetX);
            double dy = Math.abs(this.inspectingTarget.getY() - this.lastTargetY);
            double dz = Math.abs(this.inspectingTarget.getZ() - this.lastTargetZ);

            if (dx > 0.5 || dy > 0.5 || dz > 0.5) {
                // 目标移动了，关闭界面
                closeInspectScreen();
            }
        }
    }

    /**
     * 关闭审查界面
     */
    private void closeInspectScreen() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            // 关闭当前打开的界面
            serverPlayer.closeContainer();
        }
        this.stopInspecting();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("conspiratorInstinctTime", this.conspiratorInstinctTime);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.conspiratorInstinctTime = tag.contains("conspiratorInstinctTime") ? tag.getInt("conspiratorInstinctTime")
                : 0;
    }

    @Override
    public void clientTick() {
        // 减少冷却时间
        if (this.conspiratorInstinctTime > 1) {
            this.conspiratorInstinctTime--;
        }
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }
}