package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.PacketTracker;
import net.exmo.sre.camera.AdvancedCameraCommand;
import net.exmo.sre.camera.AdvancedCameraPayload;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsArena;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 末日60秒模式相位机的编排核心：家庭分队 → 按队克隆建图 → 60s 准备 → 7 个游戏日 → 结算。
 */
public final class SixtySecondsManager {
    public static final int PREP_TICKS = 20 * 60;          // 60s 准备
    /** 每游戏日 9.5 分钟：清晨 1 + 白天 6 + 晚上 2.5（含末尾 45s 睡觉时间），见 {@link net.exmo.sre.sixtyseconds.SixtySecondsDayCycle}。 */
    public static final int DAY_TICKS = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.DAY_TOTAL_TICKS;
    /** 总游戏日数的默认值；实际值可按图配置，读 {@link #totalDays(ServerLevel)}。 */
    public static final int DEFAULT_TOTAL_DAYS = 7;
    /** 可配置总日数的上限（防手滑配成天文数字）。 */
    public static final int MAX_TOTAL_DAYS = 30;
    /** 每天发放避难所代币（随机 6~12，原 10~18 的 -35%）。 */
    public static final int DAILY_TOKENS_MIN = 6;
    public static final int DAILY_TOKENS_MAX = 12;
    public static final int COINS_PER_DAY = 80;            // 每天 80 金币
    /** 准备阶段结束 → 第 1 天开始前的过渡动画时长（tick）。期间播放运镜 + 字幕 + 音效。 */
    private static final int PREP_TRANSITION_TICKS = 100;
    /** 记录各维度准备→Day 过渡动画的结束时间戳（gameTime）；null=不在过渡中。 */
    private static final Map<ServerLevel, Long> prepTransitionEnd = new WeakHashMap<>();
    /** 时间预警去重：key = "{dayNumber}:{warningType}"，每日重置。 */
    private static final Map<ServerLevel, Set<String>> warnedTimeAlerts = new WeakHashMap<>();

    private SixtySecondsManager() {
    }

    /**
     * 本局总游戏日数（按图配置 {@code totalDays}，缺省 {@value #DEFAULT_TOTAL_DAYS}，
     * clamp 到 1..{@value #MAX_TOTAL_DAYS}）。撑过第 totalDays 天即幸存者胜。
     * <p>客户端 HUD 不读本方法（拿不到服务端配置），改用<b>按玩家同步</b>的
     * {@code SixtySecondsStatsComponent.totalDays}（见 {@link #syncDayNumber}）。
     */
    public static int totalDays(ServerLevel level) {
        int configured = SixtySecondsConfigStore.current(level)
                .map(config -> config.totalDays).orElse(DEFAULT_TOTAL_DAYS);
        return Math.max(1, Math.min(MAX_TOTAL_DAYS, configured));
    }

    /** 开局：先分队槽位 → 异步建图（聊天栏广播进度）→ 建完分配家庭身份 → 传住宅 → 进 PREPARATION。 */
    public static void begin(ServerLevel level, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        data.teams.clear();
        net.exmo.sre.sixtyseconds.SixtySecondsMod.RUNNING = true;
        // 末日生存允许跳跃：覆盖地图默认的禁跳（startGame 按 AreasSettings.canJump 设，60s 地图多为 false）。
        // setJumpAvailable 会 sync——客户端 KeyBindingMixin 按键抑制与服务端 NRGameStateEvents 拉回都自动放行。
        gameWorldComponent.setJumpAvailable(true);
        SixtySecondsHealthSystem.reset(level);
        SixtySecondsVisitSystem.reset();
        SixtySecondsVisitChat.reset();
        SixtySecondsVisiting.reset();
        SixtySecondsTrade.reset();
        SixtySecondsEventSystem.reset(level);
        SixtySecondsDailyEvents.reset(level);
        SixtySecondsNewspaper.reset(level);
        SixtySecondsNpcKnock.reset();
        net.exmo.sre.sixtyseconds.content.block.SixtySecondsShowerBlock.reset();
        SixtySecondsSleepSystem.reset(level);
        SixtySecondsMinigameRotation.reset(level);
        SixtySecondsDoorHighlight.reset(level);
        SixtySecondsLootSearch.reset();
        SixtySecondsReconnect.reset();
        SixtySecondsAutoJoin.reset(); // 清「本局已进过游戏」名单 + 待入队队列
        SixtySecondsRescue.reset();
        SixtySecondsRockets.reset();
        SixtySecondsAirdrop.reset();
        net.exmo.sre.sixtyseconds.content.item.SixtySecondsRopeItem.reset();
        net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrapplingHookItem.reset();
        SixtySecondsDefenseSystem.reset(level);
        SixtySecondsPowerSystem.reset(level);
        SixtySecondsWhisperSystem.clear(level);
        net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.clearAll(); // 清跨局区域记忆
        // 配置不完整 = 建不出住宅/避难所 → 直接终止开局（否则玩家原地/虚空卡死）
        var config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || !config.isComplete()) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.config_incomplete")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            Noellesroles.LOGGER.warn("[60s] 区域配置不完整（sixty_seconds_config.json），终止开局。用 /sre:60s_area show 检查。");
            net.exmo.sre.sixtyseconds.SixtySecondsMod.RUNNING = false;
            // 延迟一 tick 停止：此刻仍处于核心 initializeGame 中途，直接 stopGame 会被随后的
            // setGameStatus(ACTIVE) 覆盖；等本次开局流程走完再完整地走停止流程。
            level.getServer().execute(() -> GameUtils.stopGame(level));
            return;
        }

        // ★ 新流程：先建图，后分配
        // 1) 计算队伍分配（确定队数） + 创建空队伍槽位（建图需要 teamId + 队伍数）
        java.util.Map<java.util.UUID, ServerPlayer> byUuid = new java.util.HashMap<>();
        java.util.List<java.util.UUID> participants = new java.util.ArrayList<>();
        for (ServerPlayer player : players) {
            byUuid.put(player.getUUID(), player);
            participants.add(player.getUUID());
        }
        SixtySecondsTeamAllocator.Result allocResult = SixtySecondsTeamAllocator.allocate(participants,
                SixtySecondsTeamLobby.partiesForAllocation(level.getServer()),
                new java.util.Random(level.getRandom().nextLong()));
        for (int teamId = 0; teamId < allocResult.teams().size(); teamId++) {
            data.teams.put(teamId, new SixtySecondsState.TeamData(teamId));
        }

        // 2) 异步建图（聊天栏广播进度）；期间 phase=INACTIVE 空转、玩家原地待命（beforeInitializeGame
        // 已覆写掉 baseInitialize 的房间传送），建完在回调里分配身份 → 传送 → 进准备阶段。
        data.phase = SixtySecondsPhase.INACTIVE;
        // 建图前的一次性提示与后续进度条同走 actionbar（黄色），与其他模式（tmm:start）的地图初始化观感一致，
        // 不再往聊天栏留痕；随后 SixtySecondsArena.build 的 BuildTask 会在同一条 actionbar 上刷新百分比。
        Component buildingHint = Component.translatable("message.noellesroles.sixty_seconds.building_start")
                .withStyle(net.minecraft.ChatFormatting.YELLOW);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(buildingHint, true);
        }
        SixtySecondsArena.build(level, data, config, () -> {
            // 3) 建图完成 → 分配家庭身份 → 传送进家 → 进准备阶段
            assignFamilies(level, data, byUuid, allocResult);
            onBuildComplete(level, data);
        });
    }

    /** 异步建图完成回调：清理建图过程中被顶掉的掉落物 → 传送各队到住宅出生点 → 进入 60s 准备阶段。 */
    private static void onBuildComplete(ServerLevel level, SixtySecondsState.Data data) {
        // 建图克隆时会替换掉既有方块（包括容器），容器内物品会变成 ItemEntity 洒落一地。
        // 必须在传送前全图清理，否则玩家出生点四周全是上局残留掉落物。
        clearAllDroppedItems(level);
        // 建图后各队避难所/搜索区区块已加载：清掉上一局遗留在其中的夜袭者（和平难度下被 mixin 豁免、
        // 自身永不消失——「开始游戏时避难所的僵尸/突袭者不消失」根因）。须在传送玩家进家前扫。
        SixtySecondsDefenseSystem.discardTaggedMobs(level);
        teleportTeams(level, data, true);
        // 海岛模式：清跨局解锁态，为每队默认解锁 1 级港湾岛，并把海图发给全员
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.onGameStart(level);
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.syncChartAll(level);
        data.phase = SixtySecondsPhase.PREPARATION;
        data.dayNumber = 0;
        data.phaseEndTick = level.getGameTime() + PREP_TICKS;
        syncDayNumber(level, data, 0); // 同步 phaseEndTick：客户端 HUD 显示 60s 准备倒计时
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.prep_start"));
    }

    /** 清除当前世界所有掉落物实体（ItemEntity），防止建图容器替换残留。 */
    private static void clearAllDroppedItems(ServerLevel level) {
        // 先收集再删除：遍历 getAllEntities() 途中 discard 会并发修改实体存储（迭代器可能吐 null 直接 NPE）
        java.util.List<net.minecraft.world.entity.Entity> toRemove = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity) {
                toRemove.add(entity);
            }
        }
        for (net.minecraft.world.entity.Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        if (!toRemove.isEmpty()) {
            Noellesroles.LOGGER.info("[60s] 建图完毕，清理了 {} 个残留掉落物", toRemove.size());
        }
    }

    public static void tick(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        // 建图期间（异步）phase=INACTIVE：空转，等 onBuildComplete 切到 PREPARATION。
        if (data.phase == SixtySecondsPhase.INACTIVE) {
            return;
        }
        SixtySecondsSearchZones.tick(level);
        // 低频（2s）把所有队伍避难所门补发给创造模式玩家：区域包只在传送等时机下发，
        // 玩家开局后切到创造时不会自动重发门列表（「创造看不到别人的避难所门」根因）。
        if (level.getGameTime() % 40 == 0) {
            net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.refreshCreativeDoors(level);
        }
        // 物资箱搜刮改由 SixtySecondsLootSearch.register() 的全局世界 tick 推进（游戏外也可搜刮）
        SixtySecondsHealthSystem.tick(level);
        SixtySecondsInventoryLimit.tick(level);
        // 世界时间跟随日内子相位（清晨=日出/白天/晚上），准备阶段固定清晨
        net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.applyWorldTime(level, data);
        switch (data.phase) {
            case PREPARATION -> {
                // 过渡动画中：冻结所有系统，仅等待动画结束
                Long transEnd = prepTransitionEnd.get(level);
                if (transEnd != null) {
                    if (level.getGameTime() >= transEnd) {
                        prepTransitionEnd.remove(level);
                        startDay(level, data, 1);
                    }
                    break;
                }
                SixtySecondsDoorHighlight.tick(level);
                if (level.getGameTime() >= data.phaseEndTick) {
                    startPrepTransition(level, data);
                }
            }
            case DAY -> {
                SixtySecondsStatsSystem.tick(level);
                SixtySecondsSicknessSystem.tick(level);
                SixtySecondsMonsterSystem.tick(level);
                SixtySecondsSleepSystem.tick(level);
                SixtySecondsVisitSystem.tick(level);
                SixtySecondsVisiting.tick(level);        // 做客者 USED_BANED 续期/异常解除
                SixtySecondsEventSystem.tick(level);
                SixtySecondsDailyEvents.tick(level);     // 每日事件门：抉择超时/探险结算
                SixtySecondsMinigameRotation.tick(level);
                SixtySecondsWhisperSystem.tick(level);   // 夜间黑暗处刷低语怪
                SixtySecondsDefenseSystem.tick(level);   // 夜袭冲门/路障
                SixtySecondsPveSystem.tick(level);       // PVE：探索区游荡怪/Boss/哨戒炮/陷阱结算
                net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.tick(level); // 海岛：登岛沿检测/报幕/解锁
                SixtySecondsPowerSystem.tick(level);     // 发电机断电边沿
                // 小游戏代币不再全队共享（SixtySecondsTokenShare 已移除）：
                // SREPlayerMinigameTaskComponent.tokens 本就按玩家独立存储/同步

                reconcileHomeMapZones(level, data);      // 兜底：已回到家的玩家若区域地图未同步则补发（修复回来地图不显示坐标）
                net.exmo.sre.sixtyseconds.content.item.SixtySecondsClockItem.tickHeld(level);
                net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.tickHeldCompass(level); // 罗盘坐标
                SixtySecondsRescue.tick(level, data);    // 隐藏通关：救援信标倒计时
                tickSubPhaseNotify(level, data);         // 清晨/白天/晚上/睡觉 切换提示
                tickTimeWarning(level, data);           // 提前预警：夜晚/睡觉将至（聊天栏）
                SixtySecondsWinConditions.tick(level, data); // 无存活幸存者→提前结束
                // 若已被 WinConditions 提前结束(相位变 FINISHED)则跳过换日/结算
                if (data.phase == SixtySecondsPhase.DAY && level.getGameTime() >= data.phaseEndTick) {
                    if (data.dayNumber >= totalDays(level)) {
                        finish(level, data);
                    } else {
                        startDay(level, data, data.dayNumber + 1);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** 管理指令：跳到指定游戏日（1..{@link #totalDays}），按新日重置相位计时。 */
    public static boolean forceDay(ServerLevel level, int day) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY && data.phase != SixtySecondsPhase.PREPARATION) {
            return false;
        }
        startDay(level, data, Math.max(1, Math.min(totalDays(level), day)));
        return true;
    }

    /** 管理指令：把当日时间跳到指定子相位起点；{@code sleep} = 晚上最后 45 秒睡觉时间。 */
    public static boolean forceTime(ServerLevel level, String timeName) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return false;
        }
        long remaining;
        if ("sleep".equalsIgnoreCase(timeName)) {
            remaining = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SLEEP_WINDOW_TICKS;
        } else {
            net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase sub;
            try {
                sub = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase
                        .valueOf(timeName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return false;
            }
            remaining = DAY_TICKS - net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.startOf(sub);
        }
        data.phaseEndTick = level.getGameTime() + remaining;
        resyncPhaseEnd(level, data);
        return true;
    }

    /** 准备阶段 60 秒结束 → 冻结系统 100 tick 过渡 → 进入第 1 天（运镜在传送进住宅后播放）。 */
    private static void startPrepTransition(ServerLevel level, SixtySecondsState.Data data) {
        long now = level.getGameTime();
        prepTransitionEnd.put(level, now + PREP_TRANSITION_TICKS);
        // 播报过渡提示（不播运镜，等传送进住宅后再播——见 startDay）
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 0.5F, 0.8F);
        }
    }

    private static void startDay(ServerLevel level, SixtySecondsState.Data data, int day) {
        boolean firstDay = day == 1;
        data.phase = SixtySecondsPhase.DAY;
        data.dayNumber = day;
        data.phaseEndTick = level.getGameTime() + DAY_TICKS;
        data.lastDayStage = 0; // 新的一天从清晨开始（清晨由 day_start 播报，不重复提示）
        clearTimeWarnings(level); // 新的一天重置预警去重
        // 门锁/门陷阱按 6 分钟时效自然过期（endTick 时间戳），不再按日重置。
        if (firstDay) {
            teleportTeams(level, data, false);
            placeSupplyChests(level, data);
            // 传送完成后播放运镜动画（从远处拉回，时长=80tick=4s，距离30格，高10格）
            for (ServerPlayer player : level.players()) {
                AdvancedCameraCommand.sendIntro(player, 80, 30.0, 10.0);
                net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                        Component.translatable("message.noellesroles.sixty_seconds.day_start", 1, totalDays(level)),
                        Component.translatable("message.noellesroles.sixty_seconds.prep_end_sub"),
                        80, false);
            }
        } else {
            // Day 2-7：SubtitleHUD TOP 日报幕（无运镜，直接显示天数 + 每日提示）
            for (ServerPlayer player : level.players()) {
                String subKey = "message.noellesroles.sixty_seconds.day_start_sub_" + day;
                net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                        Component.translatable("message.noellesroles.sixty_seconds.day_start", day, totalDays(level)),
                        Component.translatable(subKey),
                        60, false);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.AMBIENT, 0.5F, 1.2F);
            }
        }
        syncDayNumber(level, data, day);
        dailyPlayerUpdates(level, data);
        // 清晨黑暗惩罚：家中仍有未照亮的黑暗区块 → 全队 san -15（第 2 天起）
        if (!firstDay) {
            SixtySecondsWhisperSystem.applyDawnDarkPenalty(level, data);
        }
        // 物资箱每日刷新为惰性：交互时按当前 dayNumber 自动重掷（见 SupplyBoxBlockEntity）。
        // 清空前日的日级修正（每日事件 buff/debuff 仅持续一天）
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            team.clearDailyModifiers();
        }
        SixtySecondsDailyEvents.onDayStart(level); // 每日事件门：重置隔日状态（事件在傍晚触发）
        // 收集稿纸投稿 + 发布末日日报（含邮箱投递）
        SixtySecondsNewspaper.collectDrafts(level, data);
        SixtySecondsNewspaper.publish(level, data); // 末日日报：每日一期，聊天栏点击阅读
        SixtySecondsRoleAwakening.awaken(level, data);
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.day_start", day, totalDays(level)));
        // PvP 状态聊天栏广播
        if (day <= 4) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.pvp_peace_early",
                    totalDays(level)).withStyle(ChatFormatting.GREEN));
        } else if (day == 5) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.pvp_peace_over",
                    totalDays(level)).withStyle(ChatFormatting.RED));
        }
    }

    private static void finish(ServerLevel level, SixtySecondsState.Data data) {
        // 清除所有玩家的倒地姿态（防止游戏结束后趴下残留）
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed) {
                stats.downed = false;
                stats.sync();
            }
            player.setSwimming(false);
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.removeEffect(ModEffects.MOVE_BANED);
            player.removeEffect(ModEffects.USED_BANED);
            player.removeEffect(ModEffects.BREAK_IN_INTRUDER);
        }
        // 第7天结束：有存活幸存者→幸存者胜，否则败（详见 SixtySecondsWinConditions）。
        SixtySecondsWinConditions.finish(level, data);
        Noellesroles.LOGGER.info("[60s] 第七天结束，游戏结算。");
    }

    /**
     * 开局智能分队（{@link SixtySecondsTeamAllocator}）：赛前预组队伍（{@link SixtySecondsTeamLobby}）
     * 整队优先落位、未满用散人补足（队数无上限）；3 人预组队可能被拆散（被拆者会收到提示）。
     * 要求 {@code data.teams} 中队伍槽位已创建（空队伍含建图时写入的 spawn/box/door）；
     * 此方法仅填充成员并回写玩家 stats。
     */
    private static void assignFamilies(ServerLevel level, SixtySecondsState.Data data,
            java.util.Map<java.util.UUID, ServerPlayer> byUuid, SixtySecondsTeamAllocator.Result result) {
        // 参与名单在开局瞬间（begin）捕获，而本方法在异步建图完成后才执行——中间可能隔几十秒。
        // 这里按当前状态复核：期间点了「不参与」的玩家不入队不给身份（转旁观），掉线的玩家跳过。
        var participation = io.wifi.starrailexpress.cca.ParticipationComponent.KEY.get(level);
        for (int teamId = 0; teamId < result.teams().size(); teamId++) {
            SixtySecondsState.TeamData team = data.teams.get(teamId);
            java.util.List<java.util.UUID> members = result.teams().get(teamId);
            int slot = 0;
            for (java.util.UUID uuid : members) {
                ServerPlayer player = byUuid.get(uuid);
                if (player == null || player.hasDisconnected()) {
                    continue;
                }
                if (!participation.isParticipating(player)) {
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    continue;
                }
                team.members.add(player.getUUID());
                // 登记「本局已进过游戏」：这些玩家退服再进也不会被中途自动入队（见 SixtySecondsAutoJoin）
                SixtySecondsAutoJoin.markPlayed(player.getUUID());
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                stats.init();
                stats.teamId = team.teamId;
                stats.familyPosition = FamilyPosition.byIndex(slot);
                stats.sync();
                slot++;
            }
        }
        announceFamilies(level, data, byUuid, result.splitPlayers());
    }

    /** 开局把家庭成员构成发给每名队员；预组队伍被拆散的玩家额外收到提示。 */
    private static void announceFamilies(ServerLevel level, SixtySecondsState.Data data,
            java.util.Map<UUID, ServerPlayer> byUuid, java.util.Set<UUID> splitPlayers) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            net.minecraft.network.chat.MutableComponent roster = Component.empty();
            for (int i = 0; i < team.members.size(); i++) {
                ServerPlayer member = byUuid.get(team.members.get(i));
                if (i > 0) {
                    roster.append(Component.literal(", "));
                }
                roster.append(Component.literal(member.getGameProfile().getName()))
                        .append(Component.literal("("))
                        .append(Component.translatable("hud.noellesroles.sixty_seconds.family."
                                + FamilyPosition.byIndex(i).name().toLowerCase(java.util.Locale.ROOT)))
                        .append(Component.literal(")"));
            }
            for (UUID uuid : team.members) {
                ServerPlayer member = byUuid.get(uuid);
                member.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.team_assigned", team.teamId + 1, roster), false);
                if (splitPlayers.contains(uuid)) {
                    member.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.team_split_notice"), false);
                }
            }
        }
    }

    /** 换日：重置本日倒地次数，发放每日 80 金币 + 避难所代币（每人随机 6~12）。 */
    private static void dailyPlayerUpdates(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                    stats.downedCountToday = 0;
                    stats.sync();
                    SREPlayerShopComponent.KEY.get(player).addToBalance(COINS_PER_DAY);
                    int todayTokens = DAILY_TOKENS_MIN + level.getRandom().nextInt(
                            DAILY_TOKENS_MAX - DAILY_TOKENS_MIN + 1);
                    SREPlayerMinigameTaskComponent.KEY.get(player).addTokens(todayTokens);
                }
            }
        }
    }

    /** 准备结束：在各队避难所出生点旁放置箱子，装入 开局保底物资（按图开关，默认关）+ 本队搜刮记录的物资。 */
    private static void placeSupplyChests(ServerLevel level, SixtySecondsState.Data data) {
        // 保底物资开关：按图配置 starterSuppliesEnabled（/sre:60s starter on|off，默认关=全靠搜刮）
        boolean starterEnabled = SixtySecondsConfigStore.current(level)
                .map(config -> config.starterSuppliesEnabled).orElse(false);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.shelterSpawn == null) {
                continue;
            }
            // 保底物资在前（开启时搜刮再差也有第一天的底子），搜刮所得在后
            List<ItemStack> supplies = starterEnabled
                    ? starterSupplies(team.members.size()) : new java.util.ArrayList<>();
            supplies.addAll(team.storedSupplies);
            if (supplies.isEmpty()) {
                continue;
            }
            fillSuppliesIntoChests(level, team.shelterSpawn.offset(1, 0, 0), team.shelterSpawn, supplies);
        }
    }

    /**
     * 开局保底物资（数值见 {@link net.exmo.sre.sixtyseconds.SixtySecondsBalance} STARTER_*）：
     * 人均 水/罐头/绷带 + 每队 废料/破布/火把/污染水。消耗品逐件入箱——本模式食物/水
     * 不可堆叠（见 {@code SixtySecondsMod.RUNNING} 的不可堆叠 mixin），合并堆叠会出怪。
     */
    private static List<ItemStack> starterSupplies(int memberCount) {
        List<ItemStack> list = new java.util.ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            for (int n = 0; n < net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_WATER_PER_MEMBER; n++) {
                list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_WATER_SMALL));
            }
            for (int n = 0; n < net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_FOOD_PER_MEMBER; n++) {
                list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_FOOD));
            }
            for (int n = 0; n < net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_BANDAGE_PER_MEMBER; n++) {
                list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BANDAGE));
            }
        }
        list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP,
                net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_SCRAP_PER_TEAM));
        list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RAG,
                net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_RAG_PER_TEAM));
        list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_TORCH,
                net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_TORCH_PER_TEAM));
        for (int n = 0; n < net.exmo.sre.sixtyseconds.SixtySecondsBalance.STARTER_DIRTY_WATER_PER_TEAM; n++) {
            list.add(new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DIRTY_WATER));
        }
        return list;
    }

    /**
     * 把记录的物资装进避难所出生点旁的箱子。优先建成【大箱子】（双联箱，54 格）；一箱装满仍有剩余则向上叠更多箱子，
     * 直到全部装完——库存里的物资是逐次存入的未合并堆叠（{@code depositSupplies}），一支活跃队伍常远超单箱 27 格，
     * 旧实现装满即丢弃 → 「开局部分物资消失」。逐箱兜底保证一件不丢。
     */
    private static void fillSuppliesIntoChests(ServerLevel level, BlockPos basePos, BlockPos avoid,
            List<ItemStack> supplies) {
        int index = 0;
        for (int layer = 0; index < supplies.size() && layer < 16; layer++) {
            BlockPos pos = basePos.above(layer);
            // 找一个水平相邻空位组成双联大箱子（避开出生点，别把玩家埋进箱子）；找不到就退化成单箱
            Direction dir = freeAdjacentForChest(level, pos, avoid);
            if (dir != null) {
                Direction facing = dir.getCounterClockWise(); // 使 LEFT 箱按 getConnectedDirection 连到 dir 方向的 RIGHT 箱
                level.setBlock(pos, Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT),
                        Block.UPDATE_ALL);
                level.setBlock(pos.relative(dir), Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.RIGHT),
                        Block.UPDATE_ALL);
                index = fillChest(level, pos, supplies, index);
                index = fillChest(level, pos.relative(dir), supplies, index);
            } else {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                index = fillChest(level, pos, supplies, index);
            }
        }
    }

    /** 把 supplies[index..] 逐格装入 pos 处的箱子（一堆叠占一格），返回推进后的下标。 */
    private static int fillChest(ServerLevel level, BlockPos pos, List<ItemStack> supplies, int index) {
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int slot = 0; slot < chest.getContainerSize() && index < supplies.size(); slot++) {
                chest.setItem(slot, supplies.get(index++).copy());
            }
        }
        return index;
    }

    /** 找一个水平方向使 {@code pos} 的相邻位可被箱子占用（可替换且不是 {@code avoid}）；无则返回 null（退化单箱）。 */
    private static Direction freeAdjacentForChest(ServerLevel level, BlockPos pos, BlockPos avoid) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (!side.equals(avoid) && level.getBlockState(side).canBeReplaced()) {
                return dir;
            }
        }
        return null;
    }

    /**
     * 日内阶段切换提示（每秒检查一次）：白天（可 PvP）/ 晚上（危险）/ 睡觉时间 开始时
     * 通过 SubtitleHUD TOP 模式向全员显示，配合音效。清晨由 day_start 广播承担，不重复提示。
     */
    private static void tickSubPhaseNotify(ServerLevel level, SixtySecondsState.Data data) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long gameTime = level.getGameTime();
        int stage;
        if (net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.isSleepWindow(data, gameTime)) {
            stage = 3;
        } else {
            stage = switch (net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhase(data, gameTime)) {
                case MORNING -> 0;
                case DAYTIME -> 1;
                case NIGHT -> 2;
            };
        }
        if (stage == data.lastDayStage) {
            return;
        }
        int previous = data.lastDayStage;
        data.lastDayStage = stage;
        if (previous < 0) {
            return; // 开日初始化，由 startDay 的 SubtitleHUD 负责播报
        }
        // 傍晚切换（白天→晚上）：播报家庭状态 + 触发每日事件 + 检测妹妹外出
        if (previous == 1 && stage == 2) {
            // 1. 先播报家庭成员状态
            SixtySecondsDailyEvents.broadcastFamilyStatus(level);
            // 2. 触发每日事件
            SixtySecondsDailyEvents.fireEveningEvents(level);
            // 3. 检测妹妹外出事件状态
            for (SixtySecondsState.TeamData team : data.teams.values()) {
                if (!team.sisterOutside || team.sisterUUID == null) continue;
                // 妹妹是否在庇护所内（即已回家）
                net.minecraft.server.level.ServerPlayer sister = null;
                if (level.getPlayerByUUID(team.sisterUUID) instanceof net.minecraft.server.level.ServerPlayer sp
                        && !GameUtils.isPlayerEliminated(sp)) {
                    sister = sp;
                }
                if (sister == null) {
                    // 妹妹不在线（离线或死了）
                    teamBroadcast(level, team, Component.translatable(
                            "message.noellesroles.sixty_seconds.devent.sister_outside.never_return",
                            team.teamId).withStyle(ChatFormatting.DARK_RED));
                    team.sisterOutside = false;
                    team.sisterUUID = null;
                    continue;
                }
                net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent stats =
                        net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(sister);
                if (stats.downed || stats.monster) {
                    // 妹妹倒地或已变怪
                    teamBroadcast(level, team, Component.translatable(
                            "message.noellesroles.sixty_seconds.devent.sister_outside.never_return",
                            team.teamId).withStyle(ChatFormatting.DARK_RED));
                    team.sisterOutside = false;
                    team.sisterUUID = null;
                    continue;
                }
                // 妹妹还活着——检查是否回到了庇护所（坐标判定）
                boolean backHome = false;
                double sx = sister.getX(), sy = sister.getY(), sz = sister.getZ();
                if (team.shelterBox != null && team.shelterBox.contains(sx, sy, sz)) backHome = true;
                if (!backHome && team.residentialBox != null && team.residentialBox.contains(sx, sy, sz)) backHome = true;
                if (backHome) {
                    // 回家了：立即变异
                    if (!stats.monster) {
                        stats.monster = true;
                        stats.health = 1;
                        stats.sanity = 0;
                        stats.sync();
                        sister.setHealth(1.0F); // 原版血量同步，确保一击即死
                        net.exmo.sre.sixtyseconds.logic.SixtySecondsMonsterSystem.applyMonsterEffects(sister);
                        teamBroadcast(level, team, Component.translatable(
                                "message.noellesroles.sixty_seconds.devent.sister_outside.back_but_changed",
                                sister.getGameProfile().getName(), team.teamId)
                                .withStyle(ChatFormatting.DARK_RED));
                    }
                    team.sisterOutside = false;
                    team.sisterUUID = null;
                } else {
                    // 还没回来，播报等待
                    teamBroadcast(level, team, Component.translatable(
                            "message.noellesroles.sixty_seconds.devent.sister_outside.still_waiting",
                            team.teamId).withStyle(ChatFormatting.GOLD));
                }
            }
        }
        String key = switch (stage) {
            case 0 -> "message.noellesroles.sixty_seconds.stage_morning";
            case 1 -> "message.noellesroles.sixty_seconds.stage_daytime";
            case 2 -> "message.noellesroles.sixty_seconds.stage_night";
            default -> "message.noellesroles.sixty_seconds.stage_sleep";
        };
        // Subtitle HUD TOP 模式显示阶段切换
        for (ServerPlayer player : level.players()) {
            net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                    Component.translatable(key),
                    Component.translatable(key + ".sub"),
                    80, false);
            switch (stage) {
                case 0 -> player.playNotifySound(SoundEvents.BELL_RESONATE,
                        SoundSource.AMBIENT, 0.4F, 1.0F);
                case 1 -> player.playNotifySound(SoundEvents.PLAYER_LEVELUP,
                        SoundSource.AMBIENT, 0.45F, 1.5F);
                case 2 -> player.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE,
                        SoundSource.AMBIENT, 0.35F, 0.7F);
                default -> player.playNotifySound(SoundEvents.BEACON_ACTIVATE,
                        SoundSource.AMBIENT, 0.6F, 1.4F);
            }
        }
    }

    /**
     * 提前预警：夜晚/睡觉时间将至时在聊天栏广播自然语言提示。
     * 每个阈值每天只播报一次（用 {@link #warnedTimeAlerts} 去重，换日清空）。
     */
    private static void tickTimeWarning(ServerLevel level, SixtySecondsState.Data data) {
        if (level.getGameTime() % 20 != 0) {
            return; // 每秒检查一次
        }
        long remaining = data.phaseEndTick - level.getGameTime();
        if (remaining <= 0) {
            return;
        }
        net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase currentSub =
                net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhase(data, level.getGameTime());
        Set<String> warned = warnedTimeAlerts.computeIfAbsent(level, ignored -> new HashSet<>());
        String dayPrefix = data.dayNumber + ":";

        // ── 夜晚预警（当前在白天，提醒即将入夜）──
        if (currentSub == net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase.DAYTIME) {
            long daytimeRemaining = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhaseRemaining(data,
                    level.getGameTime());
            // 夜晚即将来临——大约还有1分半（白天剩余 ≤ 1800 ticks = 90s）
            if (daytimeRemaining <= 1800 && warned.add(dayPrefix + "night_90s")) {
                warnSubtitle(level,
                        Component.translatable("message.noellesroles.sixty_seconds.warn_night",
                                humanTimeDesc(daytimeRemaining)).withStyle(ChatFormatting.GOLD));
            }
            // 夜晚即将来临——大约还有1分钟（白天剩余 ≤ 1200 ticks = 60s）
            else if (daytimeRemaining <= 1200 && warned.add(dayPrefix + "night_60s")) {
                warnSubtitle(level,
                        Component.translatable("message.noellesroles.sixty_seconds.warn_night",
                                humanTimeDesc(daytimeRemaining)).withStyle(ChatFormatting.GOLD));
            }
            // 夜晚即将来临——大约还有半分钟（白天剩余 ≤ 600 ticks = 30s）
            else if (daytimeRemaining <= 600 && warned.add(dayPrefix + "night_30s")) {
                warnSubtitle(level,
                        Component.translatable("message.noellesroles.sixty_seconds.warn_night",
                                humanTimeDesc(daytimeRemaining)).withStyle(ChatFormatting.RED));
            }
            return;
        }

        // ── 睡觉时间预警（当前在晚上，提醒睡眠窗口将至）──
        if (currentSub == net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase.NIGHT) {
            boolean alreadySleep = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.isSleepWindow(data,
                    level.getGameTime());
            if (alreadySleep) {
                return; // 已在睡眠窗口，不再提醒
            }
            long sleepStartsAt = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SLEEP_WINDOW_TICKS;
            // 睡觉时间即将来临——大约还有1分钟（距离睡觉窗口还有 1200 ticks = 60s）
            if (remaining <= sleepStartsAt + 1200 && remaining > sleepStartsAt
                    && warned.add(dayPrefix + "sleep_60s")) {
                warnSubtitle(level,
                        Component.translatable("message.noellesroles.sixty_seconds.warn_sleep",
                                humanTimeDesc(remaining - sleepStartsAt)).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            // 马上就到睡觉时间了——大约还有半分钟（距离睡觉窗口还有 600 ticks = 30s）
            else if (remaining <= sleepStartsAt + 600 && remaining > sleepStartsAt
                    && warned.add(dayPrefix + "sleep_30s")) {
                warnSubtitle(level,
                        Component.translatable("message.noellesroles.sixty_seconds.warn_sleep",
                                humanTimeDesc(remaining - sleepStartsAt)).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /** 时间预警：SubtitleHUD BOTTOM 模式 + 提示音，3 秒后自动消失。 */
    private static void warnSubtitle(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerBottom(player,
                    message, Component.empty(), 60);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.AMBIENT, 0.3F, 1.4F);
        }
    }

    /** 把 tick 数转成人话（大致时间描述）。 */
    private static String humanTimeDesc(long ticks) {
        long seconds = ticks / 20;
        if (seconds >= 120) {
            long minutes = seconds / 60;
            return minutes + "分钟";
        }
        if (seconds >= 90) {
            return "1分半";
        }
        if (seconds >= 70) {
            return "1分钟多一点";
        }
        if (seconds >= 50) {
            return "1分钟";
        }
        if (seconds >= 35) {
            return "半分钟多一点";
        }
        if (seconds >= 25) {
            return "半分钟";
        }
        if (seconds >= 15) {
            return "十几秒";
        }
        return seconds + "秒";
    }

    /** 换日时清空预警去重，各阈值新的一天可重新播报。 */
    private static void clearTimeWarnings(ServerLevel level) {
        Set<String> warned = warnedTimeAlerts.get(level);
        if (warned != null) {
            warned.clear();
        }
    }

    /**
     * 换日时把 dayNumber + totalDays + phaseEndTick 同步给各队成员，供客户端 HUD 本地推算时钟
     * （每日一次，低频）。totalDays 随之下发——客户端读不到服务端配置，HUD 的「第 X/N 天」靠它。
     */
    private static void syncDayNumber(ServerLevel level, SixtySecondsState.Data data, int day) {
        int total = totalDays(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                    stats.dayNumber = day;
                    stats.totalDays = total;
                    stats.phaseEndTick = data.phaseEndTick;
                    stats.sync();
                }
            }
        }
    }

    /**
     * 局中用 {@code /sre:60s days <n>} 改总日数后，把新值补发给全场玩家（HUD 的「第 X/N 天」立即更新）。
     * 不改 dayNumber/phaseEndTick——只刷总数；是否已到最终日由 tick 里的 {@code dayNumber >= totalDays(level)} 自然判定。
     */
    public static void resyncTotalDays(ServerLevel level) {
        int total = totalDays(level);
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.totalDays != total) {
                stats.totalDays = total;
                stats.sync();
            }
        }
    }

    /** 时间被指令跳转后重推 phaseEndTick（低频，仅指令触发）。 */
    private static void resyncPhaseEnd(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                    stats.phaseEndTick = data.phaseEndTick;
                    stats.sync();
                }
            }
        }
    }

    private static void teleportTeams(ServerLevel level, SixtySecondsState.Data data, boolean residential) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            BlockPos spawn = residential ? team.residentialSpawn : team.shelterSpawn;
            if (spawn == null) {
                continue;
            }
            // 同步区域地图范围：住宅/避难所盒 +「家」点位（客户端据此扫描并显示家点）
            net.minecraft.world.phys.AABB zone = residential ? team.residentialBox : team.shelterBox;
            // 落点安全校正：克隆区若叠进复杂地形，出生点可能被方块占住（直传=窒息卡死）
            BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, spawn);
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                            player.getYRot(), player.getXRot());
                    net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(player, zone, spawn, true);
                }
            }
        }
    }

    /**
     * 区域地图兜底对账：已回到<b>本队住宅/避难所盒内</b>、且不在探索中/做客中的存活玩家，
     * 若其区域地图未指向自己家（某条传送回家的路径漏发了区域包，如拜访离开/门外事件/探险归来），
     * 补发一次家的区域包（{@code ensureZone} 幂等去重，不刷屏发包）。
     * 修复「敲了别人家的门、回来后地图不显示坐标」——区域仍指着别人家、客户端扫远处未加载区块出不了图。
     */
    private static void reconcileHomeMapZones(ServerLevel level, SixtySecondsState.Data data) {
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || SixtySecondsVisiting.isVisiting(player)
                    || SixtySecondsSearchZones.isInSearchZone(player)) {
                continue; // 做客/探索中的玩家地图指向别处是正常的，别打断
            }
            SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
            if (team == null) {
                continue;
            }
            double x = player.getX(), y = player.getY(), z = player.getZ();
            if (team.shelterBox != null && team.shelterBox.contains(x, y, z) && team.shelterSpawn != null) {
                net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.ensureZone(
                        player, team.shelterBox, team.shelterSpawn, true);
            } else if (team.residentialBox != null && team.residentialBox.contains(x, y, z)
                    && team.residentialSpawn != null) {
                net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.ensureZone(
                        player, team.residentialBox, team.residentialSpawn, true);
            }
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }

    /** 仅向指定队伍的在线成员发送消息。 */
    private static void teamBroadcast(ServerLevel level, SixtySecondsState.TeamData team, Component message) {
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(message, false);
            }
        }
    }
}
