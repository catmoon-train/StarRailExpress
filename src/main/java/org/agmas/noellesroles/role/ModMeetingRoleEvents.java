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

package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.VoteSession.VoteResultOption;
import io.wifi.starrailexpress.event.MeetingVoteEndEvent;
import io.wifi.starrailexpress.event.MeetingVoteOutEvent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.exmo.sre.meeting.MeetingApi;
import net.exmo.sre.meeting.MeetingManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

/**
 * 会议角色事件钩子注册（静态初始化触发）。
 */
public class ModMeetingRoleEvents {
    private static boolean registered;

    public static record MeetingReportInfo(ServerPlayer reporter, long startTime) {
    };

    private static final ConcurrentHashMap<ServerPlayer, MeetingReportInfo> pendingMeetings = new ConcurrentHashMap<>();
    private static final long CANADA_MEETING_START_GAP = 40; // 2s

    public static void register() {
        if (registered)
            return;
        registered = true;

        OnGameInitialized.EVENT.register((t) -> {
            pendingMeetings.clear();
        });

        OnGameEnd.EVENT.register((a, b) -> {
            pendingMeetings.clear();
        });
        // 加拿大死后延迟2s开会
        OnGameServerTick.EVENT.register((world) -> {
            if (pendingMeetings.isEmpty())
                return;
            long now = GameUtils.getTicksFromGameStart(world);
            var it = pendingMeetings.entrySet().iterator();
            while (it.hasNext()) {
                var t = it.next();
                ServerPlayer victim = t.getKey();
                MeetingReportInfo info = t.getValue();
                if (now >= info.startTime) {
                    it.remove();
                    ServerPlayer rep = info.reporter();
                    if (rep == null) {
                        rep = victim;
                    }
                    if (victim != null && victim.getGameProfile() != null) {
                        MeetingApi.startMeeting(world, rep,
                                victim.getGameProfile().getName(), true);
                        PlayerBodyEntity victimBody = GameUtils.findPlayerBodyEntity(victim);
                        if (victimBody != null)
                            MeetingManager.addReportedBody(victimBody.getUUID());
                    }
                }
            }
        });

        // 加拿大鹅：被杀时自动发起会议
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer sp))
                return;
            var game = SREGameWorldComponent.KEY.get(sp.serverLevel());
            if (game == null || !game.isRunning())
                return;
            if (!game.isRole(sp, ModMeetingRoles.CANADA_GOOSE))
                return;
            // 亡命徒期间（难民触发）：加拿大鹅不强制启用/发起会议
            if (RefugeeComponent.KEY.get(sp.serverLevel()).isAnyRevivals)
                return;

            ServerPlayer reporter = killer instanceof ServerPlayer kp ? kp : sp;
            pendingMeetings.put(sp, new MeetingReportInfo(reporter,
                    GameUtils.getTicksFromGameStart(sp.serverLevel()) + CANADA_MEETING_START_GAP));
        });

        // 呆呆鸟：被投票出局时独立胜利
        MeetingVoteOutEvent.EVENT.register((level, player) -> {
            var game = SREGameWorldComponent.KEY.get(level);
            if (game != null && game.isRole(player, ModMeetingRoles.DUMMY_BIRD)) {
                RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM,
                        "dummy_bird", java.util.OptionalInt.of(ModMeetingRoles.DUMMY_BIRD.color()));
                return false;
            }
            return true;
        });

        // 政客：不会因投票出局
        MeetingVoteOutEvent.EVENT.register((level, player) -> {
            var game = SREGameWorldComponent.KEY.get(level);
            if (game != null && game.isRole(player, ModMeetingRoles.POLITICIAN)) {
                return false;
            }
            return true;
        });
        MeetingVoteEndEvent.EVENT.register((level, session) -> {
            var game = SREGameWorldComponent.KEY.get(level);
            final long alivePlayerCount = GameUtils.getAlivePlayerCount(level);
            for (final var player : new ArrayList<>(level.players())) {
                if (game.isRole(player, ModMeetingRoles.POLITICIAN)) {
                    {
                        for (VoteOption opt : session.getOptions()) {
                            if (opt instanceof VoteOption.PlayerOption po) {
                                UUID puid = po.uuid();
                                if (puid != null && puid.equals(player.getUUID())) {
                                    VoteResultOption result = session.getResults().getOrDefault(opt.resultId(), null);
                                    if (result != null) {
                                        int count = result.count();
                                        if (count >= alivePlayerCount / 4) {
                                            MCItemsUtils.insertStackInFreeSlot(player,
                                                    TMMItems.REVOLVER.getDefaultInstance());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        // 政客：游戏开始时设置投票权重（覆盖默认值）
        // 呆呆鸟：游戏开始时设置"被投票倍率"——投给呆呆鸟的每一票实际按 1.5 票计算（显示仍为 1 票）
        OnGameTrueStarted.EVENT.register((level) ->

        {
            long alive = level.players().stream().filter(GameUtils::isPlayerAliveAndSurvival).count();
            for (ServerPlayer p : level.players()) {
                var game = SREGameWorldComponent.KEY.get(level);
                if (game == null)
                    continue;
                if (game.isRole(p, ModMeetingRoles.POLITICIAN)) {
                    MeetingManager.setVoteWeight(p, alive > 24 ? 3 : 2);
                }
                if (game.isRole(p, ModMeetingRoles.DUMMY_BIRD)) {
                    MeetingManager.setReceivedVoteMultiplier(p, 1.5);
                }
            }
        });
    }
}
