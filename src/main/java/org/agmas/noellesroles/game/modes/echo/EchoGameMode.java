package org.agmas.noellesroles.game.modes.echo;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.result.EchoResultAdapter;
import org.agmas.noellesroles.game.modes.echo.result.EchoResultSnapshot;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRules;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoIntroService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 独立正式模式承载；缺失会话或真实异常仍安全中止。 */
public final class EchoGameMode extends GameMode {
    public EchoGameMode() { super(EchoBootstrap.ID, org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy.MAX_PLAYERS,
            org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy.MIN_PLAYERS); }
    private final java.util.Set<UUID> finalizing=new java.util.HashSet<>();
    public void finishElimination(ServerPlayer victim,@Nullable ServerPlayer killer,ResourceLocation reason) {
        if(!finalizing.add(victim.getUUID())) return;
        try { super.killPlayer(victim,false,killer,reason,true); }
        finally { finalizing.remove(victim.getUUID()); }
    }

    @Override public boolean hasMood() { return false; }
    @Override public boolean suppressMoodTasks() { return true; }
    @Override public boolean minigameReplacesMoodTasks() { return false; }
    @Override public boolean shouldGrantDefaultQuestMoneyOnFinish() { return false; }
    @Override public boolean canHaveMeeting() { return false; }
    @Override public boolean canAllPeopleSeeTime() { return true; }
    @Override public boolean autoTriggerGameTrueStarted() { return false; }
    @Override public boolean shouldRecordPlayerStats() { return false; }
    @Override public boolean shouldStopWhenNoPlayers() { return false; }
    // 独立地图由 EchoMatch 的区域检查和三心伤害链处理，不套用列车环境秒杀。
    @Override public boolean enableEnvironmentDetection() { return false; }

    @Override public void initializeGame(ServerLevel level, SREGameWorldComponent component, List<ServerPlayer> players) {
        EchoSessions.initialize(level, players);
    }

    @Override public void gameStarted(ServerLevel level, SREGameWorldComponent component, ArrayList<ServerPlayer> players) {
        var timer = SREGameTimeComponent.KEY.get(level);
        timer.setResetTime(EchoRules.SAFE_TICKS);
        timer.reset();
        component.isSkillAvailable = false;
        // 本模式自己发开局运镜（追捕方 → 逃脱方 → 设施 → 回到本人），
        // 因此 SREEventRegister 的默认俯视镜头必须跳过，否则两条轨道会互相抢占。
        EchoIntroService.send(level, EchoSessions.get(level));
    }

    @Override public void tickServerGameLoop(ServerLevel level, SREGameWorldComponent component) {
        // 主循环由统一 START/END 边界推进，不能与这里重复扣时。
        if (EchoSessions.get(level) == null && component.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            GameUtils.stopGame(level);
        }
    }

    @Override public void stopGame(ServerLevel level) {
        try {
            var match = EchoSessions.get(level);
            if (match != null) {
                if (match.result() == null) match.settle(EchoResultSnapshot.Outcome.VOID, "EXTERNAL_STOP", false);
                EchoResultAdapter.apply(level, match.result());
            } else {
                EchoResultAdapter.apply(level, new EchoResultSnapshot(UUID.randomUUID(), "unknown",
                        EchoResultSnapshot.Outcome.VOID, "MISSING_SESSION", 0, false, List.of()));
            }
        } finally {
            org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.discard(level);
            EchoSessions.releaseChunks(level);
        }
    }

    @Override public void finalizeGame(ServerLevel level, SREGameWorldComponent component) {
        try {
            var match = EchoSessions.get(level);
            if (match != null) match.finalizeMatch();
        } finally { EchoSessions.releaseChunks(level); }
    }

    @Override public void recordWinStats(ServerLevel level, SREGameRoundEndComponent round, SREGameWorldComponent component) {
        var match = EchoSessions.get(level);
        if (match != null && match.result() != null) EchoResultAdapter.apply(level, match.result());
    }

    @Override public boolean isPlayerWinning(ServerLevel level, ServerPlayer player, SRERole role,
                                             SREGameRoundEndComponent round, SREGameWorldComponent component) {
        var match = EchoSessions.get(level);
        return match != null && match.result() != null && match.result().didWin(player.getUUID());
    }

    @Override public void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer,
                                     ResourceLocation reason, boolean forceDeath) {
        if(finalizing.contains(victim.getUUID()) || !(victim instanceof ServerPlayer player)) return;
        var match=EchoSessions.get(player.serverLevel());
        if(match==null) return;
        // 其它武器的逻辑刺杀不能借用本模式命中；环境/管理员走规范机会路由。
        if(forceDeath || killer==null || reason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
            match.combat().lethal(player,killer instanceof ServerPlayer server?server:null,reason,forceDeath,true);
        }
    }

    @Override public boolean shouldGiveKillerBalance(Player victim, Player killer, ResourceLocation reason) { return false; }
    @Override public boolean shouldReloadDerringer(Player victim, Player killer, ResourceLocation reason) { return false; }
    @Override public boolean shouldRewardKillerTime(SREGameWorldComponent component, Player victim, Player killer,
                                                    ResourceLocation reason, boolean force, boolean body) { return false; }
}
