package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * 中途自动入队：游戏进行中新加入服务器的玩家（且非重连——无
 * {@link SixtySecondsReconnect} 备份、未入队）自动补进一支<b>在线不满四人</b>的队伍，
 * 传送到该队住宅、发放身份并入队播报。所有队伍都已满四人则保持观战（不改变现状）。
 * <p>
 * 与 {@link SixtySecondsReconnect} 的 JOIN 恢复分工：重连玩家（有备份）由 Reconnect 复原原队伍，
 * 本类只接管「全新玩家」。开关 {@code autoJoinEnabled}（默认开，{@code /sre:60s autojoin on|off}）。
 */
public final class SixtySecondsAutoJoin {

    private SixtySecondsAutoJoin() {
    }

    /** 模组初始化时注册一次。 */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            if (!SixtySecondsMod.RUNNING || !SixtySecondsMod.isActive(joined.level())) {
                return;
            }
            // 重连玩家（有备份）交给 SixtySecondsReconnect 恢复；本类只管全新玩家
            if (SixtySecondsReconnect.hasBackup(joined.getUUID())) {
                return;
            }
            // 推迟一 tick：等玩家完全初始化（组件/位置就绪）再入队
            UUID uuid = joined.getUUID();
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null && SixtySecondsMod.RUNNING) {
                    tryAutoJoin(online);
                }
            });
        });
    }

    /**
     * 尝试把该玩家自动补入一支在线不满四人的队伍；成功返回 true。
     * 前置：本模式运行中、玩家未入队、开关打开、局已建好（住宅出生点存在）。
     */
    public static boolean tryAutoJoin(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !SixtySecondsMod.isActive(level)) {
            return false;
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (stats.teamId >= 0) {
            return false; // 已在队伍中
        }
        boolean enabled = SixtySecondsConfigStore.current(level)
                .map(config -> config.autoJoinEnabled).orElse(true);
        if (!enabled) {
            return false;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        // 只在局已建好（住宅出生点存在）的准备/白天阶段补人
        if (data.phase != SixtySecondsPhase.PREPARATION && data.phase != SixtySecondsPhase.DAY) {
            return false;
        }
        SixtySecondsState.TeamData team = pickTeam(level, data);
        if (team == null) {
            return false; // 所有队伍都满四人（或未建好）——保持观战
        }
        joinTeam(player, team, level, data);
        return true;
    }

    /** 选一支在线人数最少且 &lt;4、且住宅已建好的队伍；没有返回 null。 */
    private static SixtySecondsState.TeamData pickTeam(ServerLevel level, SixtySecondsState.Data data) {
        SixtySecondsState.TeamData best = null;
        int bestOnline = Integer.MAX_VALUE;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.residentialSpawn == null) {
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

    /** 把玩家加入指定队伍：登记成员 + 初始化状态 + 冒险模式 + 传送到住宅 + 播报。 */
    private static void joinTeam(ServerPlayer player, SixtySecondsState.TeamData team,
            ServerLevel level, SixtySecondsState.Data data) {
        if (!team.members.contains(player.getUUID())) {
            team.members.add(player.getUUID());
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        stats.init();
        stats.teamId = team.teamId;
        stats.familyPosition = FamilyPosition.byIndex(team.members.indexOf(player.getUUID()));
        stats.dayNumber = data.dayNumber;
        stats.phaseEndTick = data.phaseEndTick;
        stats.sync();

        player.setGameMode(GameType.ADVENTURE);
        // 传送到本队住宅出生点（安全校正，防落在方块里窒息）
        BlockPos spawn = team.residentialSpawn != null ? team.residentialSpawn : team.shelterSpawn;
        if (spawn != null) {
            BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, spawn);
            player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            net.minecraft.world.phys.AABB zone = team.residentialBox != null
                    ? team.residentialBox : team.shelterBox;
            net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(player, zone, spawn, true);
        }
        // 播报：给本人 + 队友
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.autojoin_welcome", team.teamId + 1)
                .withStyle(ChatFormatting.GREEN), false);
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
}
