package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameConstants;
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
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.ReplayEventLog;
import org.jetbrains.annotations.NotNull;

/**
 * 逐日者（Daywalker）职业数据。
 *
 * <ul>
 * <li>{@link #storedMoments}：当前储存的「逐日时刻」（秒），每完成任务 +20 秒，上限 120 秒
 * （领袖追随者 150 秒）</li>
 * <li>{@link #spentTicks}：已通过技能快进掉的局内时间（tick），死亡时连同未花费部分一起返还</li>
 * <li>{@link #rateDrainedTicks}：因时间加速倍率而比正常流速多流逝掉的局内时间（tick），
 * 死亡时同样计入返还——即「所有加速掉的时间」都要还回来</li>
 * <li>{@link #fastForwardRate}：当前快进速率——局内计时器每 1 秒所需的现实时间缩减量，
 * 每完成任务 +0.075，上限 0.6（领袖追随者 +0.09 / 上限 0.85）。
 * 即局内 1 秒 = 现实 (1 - 速率) 秒；满速时局内 1 秒 = 现实 0.4 秒（2.5 倍速）</li>
 * <li>{@link #skillCooldownTicks}：技能冷却（tick），服务端与客户端各自递减（客户端本地倒计时）</li>
 * <li>{@link #completedTasks} / {@link #requiredTasks}：任务进度 / 胜利所需任务数（开局人数/3）</li>
 * <li>{@link #finalChaseActive}：终末追逐状态（距胜利还剩 1 个任务时触发，持续至游戏结束）</li>
 * </ul>
 *
 * <p>存活期间除了让局内计时器快进，还会同比例加速世界（昼夜）时间，
 * 因此夜晚到天亮的变化会随加速倍率一同变快。</p>
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
    /** 每完成多少个任务记录一次站位（并在稍后播放音效） */
    public static final int TASKS_PER_MARK = 2;
    /** 站位记录到音效播放之间的延迟（tick）：2.5 秒 */
    public static final int TASK_MARK_DELAY_TICKS = 50;
    /** 技能至多把局内时间快进到还剩多少 tick（5 秒） */
    public static final int SKILL_MIN_TIME_TICKS = 5 * 20;
    /** 局内时间进入最后多少 tick（5 秒）时，未完成目标任务的逐日者死亡 */
    public static final int LAST_CHANCE_TICKS = 5 * 20;
    /**
     * 逐日者存活时，每个现实 tick 让世界（昼夜）时间流逝的基准 tick 数。
     *
     * <p>实际流逝量 = 基准 × 局内时间加速倍率 {@code 1 / (1 - 速率)}，
     * 所以速率越高，夜晚到白天也肉眼可见地越快。</p>
     */
    public static final double WORLD_TIME_TICKS_PER_TICK = 4.0D;
    /** 世界时间加速量的刷新间隔（tick）：0.5 秒一次，避免每 tick 调用 setDayTime 发包 */
    public static final int WORLD_TIME_FLUSH_INTERVAL = 10;

    // ==================== 状态 ====================

    /** 已完成任务数 */
    public int completedTasks = 0;
    /** 胜利所需任务数（开局人数/3，至少 1） */
    public int requiredTasks = 1;
    /** 当前储存的「逐日时刻」（秒） */
    public int storedMoments = 0;
    /** 已通过技能快进掉的局内时间（tick），死亡时返还 */
    public int spentTicks = 0;
    /** 因时间加速倍率而多流逝掉的局内时间（tick），死亡时返还 */
    public int rateDrainedTicks = 0;
    /** 技能冷却剩余（tick） */
    public int skillCooldownTicks = 0;
    /** 是否为领袖的追随者（影响快进速率与逐日时刻上限） */
    public boolean hasLeader = false;
    /** 领袖完成任务计数（每 {@value #LEADER_TASKS_PER_CREDIT} 个计入 1 个逐日者任务） */
    public int leaderTaskCounter = 0;
    /** 终末追逐是否已激活（距胜利还剩 1 个任务，持续至游戏结束） */
    public boolean finalChaseActive = false;
    /** 最后 5 秒的死亡判定是否已触发（每局只触发一次） */
    public boolean lastChanceTriggered = false;

    /**
     * 当前快进速率：局内计时器每 1 秒所需的现实时间缩减量（局内 1 秒 = 现实 (1 - 速率) 秒）。
     */
    private double fastForwardRate = 0.0;
    /** 快进的小数 tick 累积器 */
    private double fastForwardAccumulator = 0.0;
    /** 世界（昼夜）时间加速的小数 tick 累积器 */
    private double worldTimeAccumulator = 0.0;
    /** 世界时间刷新的节流计时（tick） */
    private int worldTimeFlushTimer = 0;
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
        spentTicks = 0;
        rateDrainedTicks = 0;
        skillCooldownTicks = 0;
        hasLeader = false;
        leaderTaskCounter = 0;
        finalChaseActive = false;
        lastChanceTriggered = false;
        fastForwardRate = 0.0;
        fastForwardAccumulator = 0.0;
        worldTimeAccumulator = 0.0;
        worldTimeFlushTimer = 0;
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

    /** 当前快进速率（局内计时器每秒减少的现实秒数），供 HUD 显示 */
    public double getFastForwardRate() {
        return fastForwardRate;
    }

    /** 当前局内时间加速倍率：局内 1 秒 = 现实 (1 - 速率) 秒，故倍率 = 1 / (1 - 速率) */
    public double getTimeMultiplier() {
        if (fastForwardRate <= 0.0 || fastForwardRate >= 1.0) {
            return 1.0D;
        }
        return 1.0D / (1.0D - fastForwardRate);
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

        SREGameTimeComponent timeComp = SREGameTimeComponent.KEY.get(player.level());
        boolean fastForwardActive = rateEnabled && fastForwardRate > 0.0 && fastForwardRate < 1.0
                && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                && !timeComp.isTimeFrozen() && timeComp.getTime() > 0;

        // 时间快进（倍速）：局内计时器 1 秒 = 现实 (1 - 速率) 秒。
        // 每个现实 tick 应额外流逝的 tick 数 = 速率 / (1 - 速率)；
        // 速率 0.6 时每 tick 共流逝 2.5 tick，即局内 1 秒 = 现实 0.4 秒。
        // 仅在对局进行中（ACTIVE）生效：一旦进入结算（STOPPING）立即停止快进；
        // 局内计时器由 finalizeGame 的 reset() 复位，快进速率随 RoleData 于下局重建归零
        // （onEndGame 清空所有职业数据 + 每局 assignRole 新建 RoleData 实例）。
        // （时停/会议期间与主计时器一同暂停）
        if (fastForwardActive) {
            fastForwardAccumulator += fastForwardRate / (1.0 - fastForwardRate);
            while (fastForwardAccumulator >= 1.0) {
                fastForwardAccumulator -= 1.0;
                if (timeComp.time <= 0) {
                    break;
                }
                timeComp.time = Math.max(0, timeComp.time - 1);
                // 记录「因加速倍率而多流逝掉的时间」：这部分死亡时同样要返还
                rateDrainedTicks++;
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

        // 世界（昼夜）时间加速：逐日者存活时世界时间持续加速流逝，
        // 加速倍率与局内时间倍率一致（速率越高，夜晚到白天越快）。
        // setDayTime 会逐玩家发时间包，所以把加速量累积起来每 10 tick（0.5 秒）刷一次，
        // 既保证太阳/月亮明显移动，又不会每 tick 发包。
        // setDayTime 不依赖 daylightCycle 规则，因此即便对局内关闭了昼夜循环也能看到变化。
        // 夜间事件（关灯）期间昼间时间被事件本身锁死在午夜，此时不插手，避免互相对抗。
        boolean nightEventLocking = SREWorldBlackoutComponent.KEY.get(player.level()).isNightEventActive();
        if (rateEnabled && !nightEventLocking
                && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                && !timeComp.isTimeFrozen() && player.level() instanceof ServerLevel serverLevel) {
            worldTimeAccumulator += WORLD_TIME_TICKS_PER_TICK * getTimeMultiplier();
            worldTimeFlushTimer++;
            if (worldTimeFlushTimer >= WORLD_TIME_FLUSH_INTERVAL) {
                worldTimeFlushTimer = 0;
                long worldTicks = (long) worldTimeAccumulator;
                if (worldTicks > 0) {
                    worldTimeAccumulator -= worldTicks;
                    serverLevel.setDayTime(serverLevel.getDayTime() + worldTicks);
                }
            }
        } else {
            worldTimeAccumulator = 0.0D;
            worldTimeFlushTimer = 0;
        }

        // 最后的通牒：局内时间小于等于 5 秒、而逐日者仍未完成目标任务数时死亡，
        // 死因沿用亡语杀手「到达不了那个地方」，每名逐日者只判定一次。
        // 只在 ACTIVE 判定：进入结算（STOPPING）后不再额外制造死亡。
        if (!lastChanceTriggered && requiredTasks > 0 && completedTasks < requiredTasks
                && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                && timeComp.getTime() <= LAST_CHANCE_TICKS) {
            lastChanceTriggered = true;
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.last_chance")
                            .withStyle(ChatFormatting.RED),
                    true);
            sync();
            // 强杀：这段倒计时是逐日者自己加速造成的，不应被护盾等挡下
            GameUtils.forceKillPlayer(serverPlayer, true, null,
                    GameConstants.DeathReasons.INSANE_KILLER_DEATH);
            return;
        }

        // 终末追逐：距胜利目标任务数还剩 1 个任务时全程发光，直至游戏结束；
        // 入场时全场先播一次提示音效，再开始播放逐日者音乐（均为 master 类型）并向全场广播
        if (finalChaseActive || (requiredTasks - completedTasks) == 1) {
            if (!finalChaseActive) {
                finalChaseActive = true;
                changed = true;
                // 回放记录：逐日者时刻触发
                ReplayEventLog.record("replay.event.moment.daywalker", ReplayEventLog.player(player));
                if (player.level() instanceof ServerLevel serverLevel) {
                    for (ServerPlayer listener : serverLevel.players()) {
                        listener.playNotifySound(SoundEvents.ILLUSIONER_PREPARE_BLINDNESS,
                                SoundSource.MASTER, 1.0F, 1.0F);
                    }
                    // 提示音效结束后，再开始播放逐日者音乐（master 类型，同样全场逐人发包）
                    Scheduler.schedule(() -> {
                        if (serverLevel == player.level()
                                && GameUtils.isPlayerAliveAndSurvival(player)) {
                            for (ServerPlayer listener : serverLevel.players()) {
                                listener.playNotifySound(NRSounds.DAYWALKER,
                                        SoundSource.MASTER, 1.0F, 1.0F);
                            }
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
        applyTaskProgress();
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
        // 每完成 2 个任务：记录当前位置，2.5 秒后在该位置播放音效
        scheduleTaskMark();
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
            applyTaskProgress();
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.leader_task_credit",
                            LEADER_TASKS_PER_CREDIT, completedTasks, requiredTasks)
                            .withStyle(ChatFormatting.GOLD),
                    true);
            // 与自行完成任务一致：每完成 2 个任务记录站位并稍后播放音效
            scheduleTaskMark();
        }
        sync();
    }

    /**
     * 应用一次「完成任务」的效果：进度 +1、储存逐日时刻、提升时间快进速率并同步。
     * （站位记录与音效由调用方在「每 {@value #TASKS_PER_MARK} 个任务」时自行触发。）
     */
    private void applyTaskProgress() {
        completedTasks++;
        int momentCap = hasLeader ? MAX_MOMENTS_WITH_LEADER : MAX_MOMENTS;
        storedMoments = Math.min(momentCap, storedMoments + MOMENTS_PER_TASK);
        if (rateEnabled) {
            double perTask = hasLeader ? FAST_FORWARD_PER_TASK_LEADER : FAST_FORWARD_PER_TASK;
            double rateCap = hasLeader ? FAST_FORWARD_CAP_LEADER : FAST_FORWARD_CAP;
            fastForwardRate = Math.min(rateCap, fastForwardRate + perTask);
        }
        sync();
    }

    /**
     * 每完成 {@value #TASKS_PER_MARK} 个任务，记录逐日者当前站位，
     * 并在 {@value #TASK_MARK_DELAY_TICKS} tick（2.5 秒）后于该位置播放
     * {@code mob.elderguardian.curse}（{@link SoundEvents#ELDER_GUARDIAN_CURSE}）音效。
     *
     * <p>延迟到达时若对局已结束、玩家已死亡或换了职业，则不再播放，
     * 避免死亡/结算后凭空响一声。</p>
     */
    private void scheduleTaskMark() {
        if (completedTasks <= 0 || completedTasks % TASKS_PER_MARK != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 记录「完成任务的位置」（世界坐标固定下来，不跟随玩家移动）
        Vec3 mark = player.position();
        Scheduler.schedule(() -> {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (!game.isRunning() || !game.isRole(player, ModRoles.DAYWALKER)) {
                return;
            }
            // 远古守卫者的诅咒音效：在记录的位置向周围播放
            serverLevel.playSound(null, mark.x, mark.y, mark.z,
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.MASTER, 2.0F, 1.0F);
        }, TASK_MARK_DELAY_TICKS);
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
     * <p>最多只能把局内时间快进到还剩 {@value #SKILL_MIN_TIME_TICKS} tick（5 秒）：
     * 到 5 秒时若逐日者仍未完成目标任务数，会由 serverTick 的最后通牒判定令其死亡。</p>
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
        int before = timeComp.getTime();
        // 快进对应数值（明面数值，不做快进换算），但不会把局内时间压到 5 秒以下
        int target = Math.max(SKILL_MIN_TIME_TICKS, before - storedMoments * 20);
        int newTime = Math.min(before, target);
        int drained = before - newTime;
        if (drained <= 0) {
            // 时间已经只剩 5 秒以内：再快进也没有意义，不消耗逐日时刻、不进冷却
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.daywalker.skill_min_time")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        timeComp.setTime(newTime);
        // 只消耗真正用于快进的那部分逐日时刻：撞上 5 秒下限时，没用掉的部分保留下来
        int usedMoments = Math.min(storedMoments, (drained + 19) / 20);
        spentTicks += drained;
        storedMoments -= usedMoments;
        skillCooldownTicks = SKILL_COOLDOWN_TICKS;
        sync();
        String drainedSeconds = String.format(java.util.Locale.ROOT, "%.1f", drained / 20.0D);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.daywalker.skill_used", drainedSeconds)
                        .withStyle(ChatFormatting.AQUA),
                true);
        // 回放记录：逐日者释放技能加速了多久的时间
        ReplayEventLog.record("replay.event.daywalker.skill_accelerate",
                ReplayEventLog.player(player), drainedSeconds);
        return true;
    }

    // ==================== 死亡返还 ====================

    /**
     * 逐日者死亡：返还所有被加速掉的时间——技能快进掉的部分、因加速倍率多流逝的部分，
     * 以及尚未花费的「逐日时刻」，并使时间流速恢复正常（局内 1 秒 = 现实 1 秒）。
     */
    public void refundOnDeath() {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        // 终末追逐音乐随死亡立即停播（全场）。先记录再清零：
        // 音乐只在终末追逐期间播放，未激活时无需发包。
        boolean wasFinalChaseActive = finalChaseActive;
        // 返还 tick 数：技能快进 + 倍率多流逝 + 尚未花费的逐日时刻
        int refundTicks = spentTicks + rateDrainedTicks + storedMoments * 20;
        // 恢复正常流速
        rateEnabled = false;
        fastForwardRate = 0.0;
        fastForwardAccumulator = 0.0;
        worldTimeAccumulator = 0.0;
        storedMoments = 0;
        spentTicks = 0;
        rateDrainedTicks = 0;
        finalChaseActive = false;
        // 移除终末追逐的发光效果
        if (glowApplied) {
            glowApplied = false;
            if (player.hasEffect(MobEffects.GLOWING)) {
                player.removeEffect(MobEffects.GLOWING);
            }
        }
        // 结束终末追逐音效（参考亡命徒 StopSound 的停播写法）：
        // 逐日者音乐是开场时逐名玩家发包播放的一次性音效，死亡后不会自行停止，
        // 这里同样向全场发送停播包，按「音效 ID + MASTER 声道」精确停掉逐日者音乐。
        if (wasFinalChaseActive && player.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer other : serverLevel.players()) {
                other.connection.send(new ClientboundStopSoundPacket(
                        NRSounds.DAYWALKER.getLocation(), SoundSource.MASTER));
            }
        }
        if (refundTicks > 0) {
            SREGameTimeComponent timeComp = SREGameTimeComponent.KEY.get(player.level());
            if (timeComp != null) {
                // 返还时间（不超过本局重置时长）
                int refunded = Math.min(timeComp.getResetTime(), timeComp.getTime() + refundTicks);
                timeComp.setTime(refunded);
            }
            // 回放记录：逐日者死后返还了多久的时间（保留一位小数，倍率多流逝的部分可能不是整秒）
            ReplayEventLog.record("replay.event.daywalker.refund_time",
                    ReplayEventLog.player(player),
                    String.format(java.util.Locale.ROOT, "%.1f", refundTicks / 20.0D));
        }
        sync();
    }

    // ==================== 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("CompletedTasks", completedTasks);
        tag.putInt("RequiredTasks", requiredTasks);
        tag.putInt("StoredMoments", storedMoments);
        tag.putInt("SpentTicks", spentTicks);
        tag.putInt("RateDrainedTicks", rateDrainedTicks);
        tag.putInt("SkillCooldownTicks", skillCooldownTicks);
        tag.putBoolean("HasLeader", hasLeader);
        tag.putBoolean("FinalChaseActive", finalChaseActive);
        tag.putBoolean("LastChanceTriggered", lastChanceTriggered);
        tag.putDouble("FastForwardRate", fastForwardRate);
        tag.putInt("LeaderTaskCounter", leaderTaskCounter);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        completedTasks = tag.getInt("CompletedTasks");
        requiredTasks = Math.max(1, tag.getInt("RequiredTasks"));
        storedMoments = tag.getInt("StoredMoments");
        spentTicks = tag.getInt("SpentTicks");
        rateDrainedTicks = tag.getInt("RateDrainedTicks");
        skillCooldownTicks = tag.getInt("SkillCooldownTicks");
        hasLeader = tag.getBoolean("HasLeader");
        finalChaseActive = tag.getBoolean("FinalChaseActive");
        lastChanceTriggered = tag.getBoolean("LastChanceTriggered");
        fastForwardRate = tag.getDouble("FastForwardRate");
        leaderTaskCounter = tag.getInt("LeaderTaskCounter");
    }
}
