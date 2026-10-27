package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 逐日者（Daywalker）职业数据。
 *
 * <ul>
 * <li>{@link #storedMoments}：当前储存的「逐日时刻」（秒），每完成任务 +20 秒，上限 120 秒
 * （领袖追随者 150 秒）</li>
 * <li>{@link #spentMoments}：已通过技能花费的「逐日时刻」（秒），死亡时连同未花费部分一起返还</li>
 * <li>{@link #fastForwardRate}：当前快进速率——局内计时器每 1 秒所需的现实时间缩减量，
 * 每完成任务 +0.075，上限 0.6（领袖追随者 +0.09 / 上限 0.85）。
 * 即局内 1 秒 = 现实 (1 - 速率) 秒；满速时局内 1 秒 = 现实 0.4 秒（2.5 倍速）</li>
 * <li>{@link #skillCooldownTicks}：技能冷却（tick），服务端与客户端各自递减（客户端本地倒计时）</li>
 * <li>{@link #completedTasks} / {@link #requiredTasks}：任务进度 / 胜利所需任务数（开局人数/3）</li>
 * <li>{@link #finalChaseActive}：终末追逐状态（距胜利还剩 1 个任务时触发，持续至游戏结束）</li>
 * </ul>
 *
 * <p>仅在状态变化时 {@link #sync()}，绝不每 tick 同步。</p>
 */
public class DaywalkerRoleData extends SimpleRoleData {

    // ==================== 常量 ====================

    /** 每完成 1 个任务获得的「逐日时刻」（秒） */
    public static final int MOMENTS_PER_TASK = 20;
    /** 「逐日时刻」储存上限（秒） */
    public static final int MAX_MOMENTS = 120;
    /** 领袖追随者的「逐日时刻」储存上限（秒） */
    public static final int MAX_MOMENTS_WITH_LEADER = 150;
    /**
     * 每完成 1 个任务的时间快进速率：局内计时器每 1 秒所需的现实时间减少 0.075 秒
     * （即局内 1 秒 = 现实 0.925 秒，约 1.08 倍速）。
     */
    public static final double FAST_FORWARD_PER_TASK = 0.075;
    /** 时间快进速率上限：局内计时器每 1 秒所需的现实时间至多减少 0.6 秒（即局内 1 秒 = 现实 0.4 秒，2.5 倍速） */
    public static final double FAST_FORWARD_CAP = 0.6;
    /** 领袖追随者：每完成 1 个任务的时间快进速率（局内 1 秒 = 现实 0.91 秒） */
    public static final double FAST_FORWARD_PER_TASK_LEADER = 0.09;
    /** 领袖追随者：时间快进速率上限（局内 1 秒 = 现实 0.15 秒） */
    public static final double FAST_FORWARD_CAP_LEADER = 0.85;
    /** 技能冷却（tick）：150 秒 */
    public static final int SKILL_COOLDOWN_TICKS = 150 * 20;
    /** 终末追逐发光效果刷新间隔（tick） */
    public static final int FINAL_CHASE_GLOW_INTERVAL = 20;
    /** 领袖每完成多少个任务为逐日者计入 1 个任务 */
    public static final int LEADER_TASKS_PER_CREDIT = 4;
    /** 其他玩家只能透视逐日者的范围（格） */
    public static final int XRAY_RANGE = 20;

    // ==================== 状态 ====================

    /** 已完成任务数 */
    public int completedTasks = 0;
    /** 胜利所需任务数（开局人数/3，至少 1） */
    public int requiredTasks = 1;
    /** 当前储存的「逐日时刻」（秒） */
    public int storedMoments = 0;
    /** 已通过技能花费的「逐日时刻」（秒），死亡时返还 */
    public int spentMoments = 0;
    /** 技能冷却剩余（tick） */
    public int skillCooldownTicks = 0;
    /** 是否为领袖的追随者（影响快进速率与逐日时刻上限） */
    public boolean hasLeader = false;
    /** 领袖完成任务计数（每 {@value #LEADER_TASKS_PER_CREDIT} 个计入 1 个逐日者任务） */
    public int leaderTaskCounter = 0;
    /** 终末追逐是否已激活（距胜利还剩 1 个任务，持续至游戏结束） */
    public boolean finalChaseActive = false;

    /**
     * 当前快进速率：局内计时器每 1 秒所需的现实时间缩减量（局内 1 秒 = 现实 (1 - 速率) 秒）。
     */
    private double fastForwardRate = 0.0;
    /** 快进的小数 tick 累积器 */
    private double fastForwardAccumulator = 0.0;
    /** 快进是否生效（死亡后关闭并恢复正常流速） */
    private boolean rateEnabled = true;
    /** 终末追逐发光计时（tick） */
    private int glowTimer = 0;
    /** 是否由本职业施加了发光效果（死亡时精确移除） */
    private boolean glowApplied = false;
    /** 局内时间同步节流计时（tick） */
    private int timeSyncTimer = 0;

    public DaywalkerRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        completedTasks = 0;
        storedMoments = 0;
        spentMoments = 0;
        skillCooldownTicks = 0;
        hasLeader = false;
        leaderTaskCounter = 0;
        finalChaseActive = false;
        fastForwardRate = 0.0;
        fastForwardAccumulator = 0.0;
        rateEnabled = true;
        glowTimer = 0;
        glowApplied = false;
        timeSyncTimer = 0;
        int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
        requiredTasks = Math.max(1, totalPlayers / 3);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == player;
    }

    /** 当前快进速率（局内游戏秒 / 现实秒），供 HUD 显示 */
    public double getFastForwardRate() {
        return fastForwardRate;
    }

    @Override
    public void clientTick() {
        // 客户端本地倒计时（服务端会在开始/结束时同步校正）
        if (skillCooldownTicks > 0) {
            skillCooldownTicks--;
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRole(player, ModRoles.DAYWALKER)) {
            return;
        }
        if (!game.isRunning()) {
            return;
        }

        boolean changed = false;

        // 技能冷却（无论存活与否都递减，与渡鸦等职业一致）
        if (skillCooldownTicks > 0) {
            skillCooldownTicks--;
            if (skillCooldownTicks <= 0) {
                changed = true;
            }
        }

        // 胜利所需任务数：开局人数/3（自愈式刷新，防止 init 时人数未就绪）
        int playerCount = game.getPlayerCount();
        int newRequired = Math.max(1, playerCount / 3);
        if (requiredTasks != newRequired) {
            requiredTasks = newRequired;
            changed = true;
        }

        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            if (changed) {
                sync();
            }
            return;
        }

        // 时间快进（倍速）：局内计时器 1 秒 = 现实 (1 - 速率) 秒。
        // 每个现实 tick 应额外流逝的 tick 数 = 速率 / (1 - 速率)；
        // 速率 0.6 时每 tick 共流逝 2.5 tick，即局内 1 秒 = 现实 0.4 秒。
        // 仅在对局进行中（ACTIVE）生效：一旦进入结算（STOPPING）立即停止快进；
        // 局内计时器由 finalizeGame 的 reset() 复位，快进速率随 RoleData 于下局重建归零
        // （onEndGame 清空所有职业数据 + 每局 assignRole 新建 RoleData 实例）。
        // （时停/会议期间与主计时器一同暂停）
        SREGameTimeComponent timeComp = SREGameTimeComponent.KEY.get(player.level());
        if (rateEnabled && fastForwardRate > 0.0 && fastForwardRate < 1.0
                && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                && !timeComp.isTimeFrozen() && timeComp.getTime() > 0) {
            fastForwardAccumulator += fastForwardRate / (1.0 - fastForwardRate);
            while (fastForwardAccumulator >= 1.0) {
                fastForwardAccumulator -= 1.0;
                timeComp.time = Math.max(0, timeComp.time - 1);
            }
            // 节流同步：每秒同步一次局内时间，保证客户端计时器显示跟得上快进
            timeSyncTimer++;
            if (timeSyncTimer >= 20) {
                timeSyncTimer = 0;
                timeComp.sync();
            }
        } else {
            timeSyncTimer = 0;
        }

        // 终末追逐：距胜利目标任务数还剩 1 个任务时全程发光，直至游戏结束；
        // 入场时全场先播一次提示音效，再开始播放逐日者音乐（均为 master 类型）并向全场广播
        if (finalChaseActive || (requiredTasks - completedTasks) == 1) {
            if (!finalChaseActive) {
                finalChaseActive = true;
                changed = true;
                // 全场先播放一次提示音效（master 类型）
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null,
                            player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ILLUSIONER_PREPARE_BLINDNESS,
                            SoundSource.MASTER, 1.0F, 1.0F);
                    // 提示音效结束后，再开始播放逐日者音乐（master 类型）
                    Scheduler.schedule(() -> {
                        if (player.level() instanceof ServerLevel sl
                                && GameUtils.isPlayerAliveAndSurvival(player)) {
                            sl.playSound(null,
                                    player.getX(), player.getY(), player.getZ(),
                                    NRSounds.DAYWALKER,
                                    SoundSource.MASTER, 1.0F, 1.0F);
                        }
                    }, 30);
                    // 全场广播：终末追逐已经开始（不透露逐日者身份）
                    Component broadcast = Component
                            .translatable("message.noellesroles.daywalker.final_chase_broadcast")
                            .withStyle(ChatFormatting.GOLD);
                    for (ServerPlayer other : serverLevel.players()) {
                        SRENetworkMessageUtils.sendBroadcast(other, broadcast);
                    }
                }
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.daywalker.final_chase")
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
            // 维持发光（无限续期，直至死亡或游戏结束）
            glowTimer++;
            if (glowTimer >= FINAL_CHASE_GLOW_INTERVAL) {
                glowTimer = 0;
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, FINAL_CHASE_GLOW_INTERVAL * 2 + 2, 0,
                        false, true, true));
                glowApplied = true;
            }
        } else if (finalChaseActive) {
            finalChaseActive = false;
            changed = true;
        }

        if (changed) {
            sync();
        }
    }

    // ==================== 任务进度 ====================

    /**
     * 逐日者完成 1 个任务：累计进度、获得 20 秒逐日时刻、提升时间快进速率。
     */
    public void onTaskCompleted() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.DAYWALKER)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        completedTasks++;
        int momentCap = hasLeader ? MAX_MOMENTS_WITH_LEADER : MAX_MOMENTS;
        storedMoments = Math.min(momentCap, storedMoments + MOMENTS_PER_TASK);
        if (rateEnabled) {
            double perTask = hasLeader ? FAST_FORWARD_PER_TASK_LEADER : FAST_FORWARD_PER_TASK;
            double rateCap = hasLeader ? FAST_FORWARD_CAP_LEADER : FAST_FORWARD_CAP;
            fastForwardRate = Math.min(rateCap, fastForwardRate + perTask);
        }
        sync();
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.daywalker.task_complete",
                        completedTasks, requiredTasks, MOMENTS_PER_TASK)
                        .withStyle(ChatFormatting.GOLD),
                true);
        if ((requiredTasks - completedTasks) == 1) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.final_chase")
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
    }

    /**
     * 领袖每完成 {@value #LEADER_TASKS_PER_CREDIT} 个任务，为「追随者」逐日者计入 1 个任务
     * （视为逐日者完成了 1 个任务，并应用逐日者的相关效果）。
     */
    public void creditLeaderTask() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.DAYWALKER)) {
            return;
        }
        leaderTaskCounter++;
        if (leaderTaskCounter >= LEADER_TASKS_PER_CREDIT) {
            leaderTaskCounter = 0;
            // 领袖代为完成：不要求逐日者存活（视为完成，直接应用效果）
            completedTasks++;
            int momentCap = hasLeader ? MAX_MOMENTS_WITH_LEADER : MAX_MOMENTS;
            storedMoments = Math.min(momentCap, storedMoments + MOMENTS_PER_TASK);
            if (rateEnabled) {
                double perTask = hasLeader ? FAST_FORWARD_PER_TASK_LEADER : FAST_FORWARD_PER_TASK;
                double rateCap = hasLeader ? FAST_FORWARD_CAP_LEADER : FAST_FORWARD_CAP;
                fastForwardRate = Math.min(rateCap, fastForwardRate + perTask);
            }
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.leader_task_credit",
                            LEADER_TASKS_PER_CREDIT, completedTasks, requiredTasks)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
        sync();
    }

    /**
     * 被领袖招募为追随者：立即获得 50 金币，此后每任务快进 0.09 秒/秒（至多 0.85），
     * 逐日时刻上限提升至 150 秒。
     */
    public void enableLeaderBoost() {
        if (hasLeader) {
            return;
        }
        hasLeader = true;
        sync();
    }

    // ==================== 技能 ====================

    /**
     * 释放技能：立即消耗当前所有「逐日时刻」，使局内时间快进对应数值（明面数值，不做快进换算），
     * 之后进入 150 秒冷却。没有储存逐日时刻时无法释放。
     *
     * @return 是否释放成功（返回 true 才消耗冷却）
     */
    public boolean useSkill() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.DAYWALKER)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SREGameTimeComponent timeComp = SREGameTimeComponent.KEY.get(player.level());
        if (timeComp.isTimeFrozen()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.skill_frozen")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (skillCooldownTicks > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.skill_cooldown",
                            (skillCooldownTicks + 19) / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (storedMoments <= 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.skill_no_moments")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        int consumed = storedMoments;
        // 局内时间快进对应数值（仅明面数值，不进行快进换算）
        int newTime = Math.max(0, timeComp.getTime() - consumed * 20);
        timeComp.setTime(newTime);
        spentMoments += consumed;
        storedMoments = 0;
        skillCooldownTicks = SKILL_COOLDOWN_TICKS;
        sync();
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.daywalker.skill_used", consumed)
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    // ==================== 死亡返还 ====================

    /**
     * 逐日者死亡：返还所有逐日时刻（含已花费与未花费的数值的时间），
     * 并使快进的时间恢复正常（局内 1 秒 = 现实 1 秒）。
     */
    public void refundOnDeath() {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        // 终末追逐音乐随死亡立即停播（全场）。先记录再清零：
        // 音乐只在终末追逐期间播放，未激活时无需发包。
        boolean wasFinalChaseActive = finalChaseActive;
        int refundSeconds = spentMoments + storedMoments;
        // 恢复正常流速
        rateEnabled = false;
        fastForwardRate = 0.0;
        fastForwardAccumulator = 0.0;
        storedMoments = 0;
        spentMoments = 0;
        finalChaseActive = false;
        // 移除终末追逐的发光效果
        if (glowApplied) {
            glowApplied = false;
            if (player.hasEffect(MobEffects.GLOWING)) {
                player.removeEffect(MobEffects.GLOWING);
            }
        }
        // 结束终末追逐音效（参考亡命徒 StopSound 的停播写法）：
        // 逐日者音乐是以全场广播方式播放的一次性音效，死亡后不会自行停止，
        // 这里向全场发送停播包，按「音效 ID + MASTER 声道」精确停掉逐日者音乐。
        if (wasFinalChaseActive && player.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer other : serverLevel.players()) {
                other.connection.send(new ClientboundStopSoundPacket(
                        NRSounds.DAYWALKER.getLocation(), SoundSource.MASTER));
            }
        }
        if (refundSeconds > 0) {
            SREGameTimeComponent timeComp = SREGameTimeComponent.KEY.get(player.level());
            if (timeComp != null) {
                // 返还时间（不超过本局重置时长）
                int refunded = Math.min(timeComp.getResetTime(), timeComp.getTime() + refundSeconds * 20);
                timeComp.setTime(refunded);
            }
        }
        sync();
    }

    // ==================== 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("CompletedTasks", completedTasks);
        tag.putInt("RequiredTasks", requiredTasks);
        tag.putInt("StoredMoments", storedMoments);
        tag.putInt("SpentMoments", spentMoments);
        tag.putInt("SkillCooldownTicks", skillCooldownTicks);
        tag.putBoolean("HasLeader", hasLeader);
        tag.putBoolean("FinalChaseActive", finalChaseActive);
        tag.putDouble("FastForwardRate", fastForwardRate);
        tag.putInt("LeaderTaskCounter", leaderTaskCounter);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        completedTasks = tag.getInt("CompletedTasks");
        requiredTasks = Math.max(1, tag.getInt("RequiredTasks"));
        storedMoments = tag.getInt("StoredMoments");
        spentMoments = tag.getInt("SpentMoments");
        skillCooldownTicks = tag.getInt("SkillCooldownTicks");
        hasLeader = tag.getBoolean("HasLeader");
        finalChaseActive = tag.getBoolean("FinalChaseActive");
        fastForwardRate = tag.getDouble("FastForwardRate");
        leaderTaskCounter = tag.getInt("LeaderTaskCounter");
    }
}
