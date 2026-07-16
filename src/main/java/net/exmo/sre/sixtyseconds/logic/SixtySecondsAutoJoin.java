package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 中途自动入队：游戏进行中<b>首次</b>加入服务器的玩家自动补进一支在线不满四人的队伍。
 *
 * <h3>完整流程（对齐开局体验：住宅 → 60s 准备 → 避难所）</h3>
 * <ol>
 *   <li><b>预告（{@value #WARN_TICKS} tick = 1 分钟）</b>：进服即用大字幕+音效提示「1 分钟后加入」，
 *       期间转旁观自由观察（{@link #WARN_TICKS}）。</li>
 *   <li><b>进住宅</b>：到点分配队伍 → 传送到该队<b>住宅</b>（{@code residentialSpawn}）→ 冒险模式。</li>
 *   <li><b>60s 准备倒计时</b>：设 {@code stats.dayNumber=0} + {@code phaseEndTick=now+PREP_TICKS}，
 *       客户端 HUD 据此显示与开局同款的 60s 准备倒计时（两者都是<b>按玩家</b>同步的字段）。</li>
 *   <li><b>进避难所</b>：60s 到点传送到该队<b>避难所</b>（{@code shelterSpawn}）并把 dayNumber/phaseEndTick
 *       恢复成全局当前值（HUD 回到正常日程）。</li>
 * </ol>
 *
 * <h3>本场只能进一次</h3>
 * {@link #PLAYED_THIS_ROUND} 记录本局<b>已入过队</b>的玩家（开局分队 + 中途入队都登记）：
 * 这些玩家即便退服再进也<b>不会</b>再次中途加入（防死亡/淘汰后重进刷新身份）。按局清空。
 *
 * <p>与 {@link SixtySecondsReconnect} 分工：有备份的掉线重连玩家由 Reconnect 复原原队伍，本类只接管全新玩家。
 * 开关 {@code autoJoinEnabled}（默认开，{@code /sre:60s autojoin on|off}）。
 */
public final class SixtySecondsAutoJoin {

    /** 进服后到真正入队的预告时长（1 分钟）。 */
    public static final int WARN_TICKS = 20 * 60;

    /** 本局已入过队的玩家（开局分队 + 中途入队）；退服再进也不再中途加入。按局清空。 */
    private static final Set<UUID> PLAYED_THIS_ROUND = new HashSet<>();
    /** 预告中：uuid → 真正入队的 gameTime。 */
    private static final Map<UUID, Long> PENDING = new HashMap<>();
    /** 住宅准备中：uuid → 传送进避难所的 gameTime。 */
    private static final Map<UUID, Long> PREPPING = new HashMap<>();

    private SixtySecondsAutoJoin() {
    }

    /** 模组初始化时注册一次。 */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            if (!SixtySecondsMod.RUNNING || !SixtySecondsMod.isActive(joined.level())) {
                return;
            }
            UUID uuid = joined.getUUID();
            // 重连玩家（有备份）交给 SixtySecondsReconnect 恢复原队伍
            if (SixtySecondsReconnect.hasBackup(uuid)) {
                return;
            }
            // 本局已进过游戏：不再中途加入（死亡/淘汰后重进也不刷新身份）
            if (PLAYED_THIS_ROUND.contains(uuid)) {
                return;
            }
            // 推迟一 tick：等玩家完全初始化（组件/位置就绪）再判定
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null && SixtySecondsMod.RUNNING) {
                    scheduleJoin(online);
                }
            });
        });
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsAutoJoin::tick);
    }

    /** 开局分队时登记（{@code SixtySecondsManager.assignFamilies}）：这些玩家本局已进过游戏。 */
    public static void markPlayed(UUID uuid) {
        PLAYED_THIS_ROUND.add(uuid);
    }

    /** 本局是否已进过游戏（已入过队）。 */
    public static boolean hasPlayedThisRound(UUID uuid) {
        return PLAYED_THIS_ROUND.contains(uuid);
    }

    /** 换局清空：上一局的记录不得泄漏到下一局。 */
    public static void reset() {
        PLAYED_THIS_ROUND.clear();
        PENDING.clear();
        PREPPING.clear();
    }

    /** 预告：1 分钟后入队（大字幕 + 音效 + 转旁观自由观察）。不合条件则不预告。 */
    private static void scheduleJoin(ServerPlayer player) {
        if (!eligible(player) || pickTeam(player.serverLevel(),
                SixtySecondsState.get(player.serverLevel())) == null) {
            return; // 关闭/未建好/所有队伍已满四人 → 保持观战
        }
        UUID uuid = player.getUUID();
        long now = player.serverLevel().getGameTime();
        PENDING.put(uuid, now + WARN_TICKS);
        player.setGameMode(GameType.SPECTATOR); // 预告期间自由观察
        net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                Component.translatable("message.noellesroles.sixty_seconds.autojoin_warn_title")
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable("message.noellesroles.sixty_seconds.autojoin_warn_sub",
                        WARN_TICKS / 20),
                80, false);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.autojoin_warn_chat", WARN_TICKS / 20)
                .withStyle(ChatFormatting.GOLD), false);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** 每 tick 推进：预告到点 → 进住宅开始 60s 准备；准备到点 → 进避难所。 */
    private static void tick(ServerLevel level) {
        if (PENDING.isEmpty() && PREPPING.isEmpty()) {
            return;
        }
        if (!SixtySecondsMod.isActive(level)) {
            return;
        }
        long now = level.getGameTime();
        SixtySecondsState.Data data = SixtySecondsState.get(level);

        for (Iterator<Map.Entry<UUID, Long>> it = PENDING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove(); // 预告期间退服：取消（再进服会重新预告）
                continue;
            }
            if (now < entry.getValue()) {
                continue;
            }
            it.remove();
            enterResidence(player, level, data, now);
        }

        for (Iterator<Map.Entry<UUID, Long>> it = PREPPING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove(); // 准备期间退服：其状态已由 Reconnect 备份，重连即复原
                continue;
            }
            if (now < entry.getValue()) {
                continue;
            }
            it.remove();
            enterShelter(player, level, data);
        }
    }

    /** 预告到点：分配队伍 → 传送进<b>住宅</b> → 开始按玩家的 60s 准备倒计时。 */
    private static void enterResidence(ServerPlayer player, ServerLevel level,
            SixtySecondsState.Data data, long now) {
        if (!eligible(player)) {
            return;
        }
        SixtySecondsState.TeamData team = pickTeam(level, data);
        if (team == null) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.autojoin_no_room").withStyle(ChatFormatting.RED), false);
            return; // 等待期间队伍被填满 → 保持观战
        }
        if (!team.members.contains(player.getUUID())) {
            team.members.add(player.getUUID());
        }
        markPlayed(player.getUUID());

        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        stats.init();
        stats.teamId = team.teamId;
        stats.familyPosition = FamilyPosition.byIndex(team.members.indexOf(player.getUUID()));
        // 按玩家的 60s 准备倒计时：HUD 的准备条判定就是 dayNumber==0 && phaseEndTick-now>0（都是按玩家同步）
        stats.dayNumber = 0;
        stats.totalDays = SixtySecondsManager.totalDays(level); // HUD「第 X/N 天」的 N
        stats.phaseEndTick = now + SixtySecondsManager.PREP_TICKS;
        stats.sync();

        player.setGameMode(GameType.ADVENTURE);
        teleport(player, level, team.residentialSpawn != null ? team.residentialSpawn : team.shelterSpawn,
                team.residentialBox != null ? team.residentialBox : team.shelterBox,
                team.residentialSpawn != null ? team.residentialSpawn : team.shelterSpawn);

        PREPPING.put(player.getUUID(), now + SixtySecondsManager.PREP_TICKS);
        net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                Component.translatable("message.noellesroles.sixty_seconds.autojoin_prep_title",
                        team.teamId + 1).withStyle(ChatFormatting.GREEN),
                Component.translatable("message.noellesroles.sixty_seconds.autojoin_prep_sub",
                        SixtySecondsManager.PREP_TICKS / 20),
                80, false);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

        Component notice = Component.translatable(
                "message.noellesroles.sixty_seconds.autojoin_teammate",
                player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA);
        for (UUID uuid : team.members) {
            if (!uuid.equals(player.getUUID())
                    && level.getPlayerByUUID(uuid) instanceof ServerPlayer teammate) {
                teammate.displayClientMessage(notice, false);
            }
        }
    }

    /** 准备到点：传送进<b>避难所</b>，HUD 日程恢复成全局当前值。 */
    private static void enterShelter(ServerPlayer player, ServerLevel level, SixtySecondsState.Data data) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
        if (team == null) {
            return;
        }
        stats.dayNumber = data.dayNumber;
        stats.totalDays = SixtySecondsManager.totalDays(level);
        stats.phaseEndTick = data.phaseEndTick;
        stats.sync();
        teleport(player, level, team.shelterSpawn, team.shelterBox, team.shelterSpawn);
        net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                Component.translatable("message.noellesroles.sixty_seconds.autojoin_shelter_title")
                        .withStyle(ChatFormatting.AQUA),
                Component.translatable("message.noellesroles.sixty_seconds.autojoin_shelter_sub"),
                80, false);
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    /** 安全落点传送 + 同步区域地图（所有进出住宅/避难所的传送统一走 findSafeSpot，防落进方块窒息）。 */
    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos spawn,
            net.minecraft.world.phys.AABB zone, BlockPos home) {
        if (spawn == null) {
            return;
        }
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, spawn);
        player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        if (zone != null) {
            net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(player, zone, home, true);
        }
    }

    /** 可自动入队的前置：本模式运行中、未入队、本局没进过、开关开、局已建好（准备/白天阶段）。 */
    private static boolean eligible(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !SixtySecondsMod.isActive(level)) {
            return false;
        }
        if (SixtySecondsStatsComponent.KEY.get(player).teamId >= 0) {
            return false; // 已在队伍中
        }
        if (PLAYED_THIS_ROUND.contains(player.getUUID())) {
            return false;
        }
        if (!SixtySecondsConfigStore.current(level).map(c -> c.autoJoinEnabled).orElse(true)) {
            return false;
        }
        SixtySecondsPhase phase = SixtySecondsState.get(level).phase;
        return phase == SixtySecondsPhase.PREPARATION || phase == SixtySecondsPhase.DAY;
    }

    /** 选一支在线人数最少且 &lt;4、且住宅已建好的队伍；没有返回 null。 */
    private static SixtySecondsState.TeamData pickTeam(ServerLevel level, SixtySecondsState.Data data) {
        SixtySecondsState.TeamData best = null;
        int bestOnline = Integer.MAX_VALUE;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.residentialSpawn == null && team.shelterSpawn == null) {
                continue; // 该队未建好
            }
            int online = onlineMemberCount(level, team);
            if (online < SixtySecondsTeamAllocator.TEAM_SIZE && online < bestOnline) {
                best = team;
                bestOnline = online;
            }
        }
        return best;
    }

    /** 队伍当前在线成员数（离线/永久退出的成员不计，以便补位）。 */
    private static int onlineMemberCount(ServerLevel level, SixtySecondsState.TeamData team) {
        int count = 0;
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer) {
                count++;
            }
        }
        return count;
    }
}
