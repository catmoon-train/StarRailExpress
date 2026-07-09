package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.MeetingVoteOutEvent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.meeting.MeetingApi;
import net.exmo.sre.meeting.MeetingManager;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 会议角色事件钩子注册（静态初始化触发）。
 */
public class ModMeetingRoleEvents {
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;

        // 加拿大鹅：被杀时自动发起会议
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            var game = SREGameWorldComponent.KEY.get(sp.serverLevel());
            if (game == null || !game.isRunning()) return;
            if (!game.isRole(sp, ModMeetingRoles.CANADA_GOOSE)) return;
            ServerPlayer reporter = killer instanceof ServerPlayer kp ? kp : sp;
            MeetingApi.startMeeting(sp.serverLevel(), reporter, sp.getGameProfile().getName());
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

        // 政客：游戏开始时设置投票权重（覆盖默认值）
        OnGameTrueStarted.EVENT.register((level) -> {
            long alive = level.players().stream().filter(GameUtils::isPlayerAliveAndSurvival).count();
            for (ServerPlayer p : level.players()) {
                var game = SREGameWorldComponent.KEY.get(level);
                if (game != null && game.isRole(p, ModMeetingRoles.POLITICIAN)) {
                    MeetingManager.setVoteWeight(p, alive > 24 ? 3 : 2);
                }
            }
        });
    }
}
