package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 胜负判定：
 * <ul>
 *   <li>存活「幸存者」= 存活(非旁观) 且 <b>非怪物</b> 的玩家（倒地仍算在场，可被救）。</li>
 *   <li>游戏进行中若<b>无任何存活幸存者</b>（全死/全变怪）→ 提前结束，幸存者失败。</li>
 *   <li>活过第 7 天且仍有存活幸存者 → 幸存者胜。</li>
 *   <li>抵达幸存者阵营（{@link #reachSurvivorCamp}）→ 立即幸存者胜（触发点为后续设计）。</li>
 * </ul>
 */
public final class SixtySecondsWinConditions {
    private SixtySecondsWinConditions() {
    }

    /** 游戏日每 tick 调用：无存活幸存者则提前结束。 */
    public static void tick(ServerLevel level, SixtySecondsState.Data data) {
        if (data.phase != SixtySecondsPhase.DAY) {
            return;
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        if (!anySurvivorAlive(level)) {
            endGame(level, data, false);
        }
    }

    /** 第 7 天结束调用：有存活幸存者则胜，否则败。 */
    public static void finish(ServerLevel level, SixtySecondsState.Data data) {
        endGame(level, data, anySurvivorAlive(level));
    }

    /** 抵达幸存者阵营：立即判幸存者胜（供未来触发点调用）。 */
    public static boolean reachSurvivorCamp(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return false;
        }
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.reach_survivors",
                player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
        endGame(level, data, true);
        return true;
    }

    /** 隐藏通关 · 救援抵达（救援信标倒计时结束，见 {@link SixtySecondsRescue}）：立即判幸存者胜。 */
    public static void rescueArrived(ServerLevel level, SixtySecondsState.Data data) {
        if (data.phase != SixtySecondsPhase.DAY) {
            return;
        }
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.rescue_arrived")
                .withStyle(ChatFormatting.GOLD));
        endGame(level, data, true);
    }

    /**
     * 是否还有「存活幸存者」。<b>等待自动复活的玩家也算</b>——否则开着自动复活时，一波团灭会在
     * 谁都还没复活的那几分钟里直接判负，复活功能形同虚设（{@code SixtySecondsAutoRevive.anyPendingRevive}
     * 在开关关闭时恒为 false，旧行为不变）。
     */
    private static boolean anySurvivorAlive(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (!SixtySecondsStatsComponent.KEY.get(player).monster) {
                return true;
            }
        }
        return SixtySecondsAutoRevive.anyPendingRevive(level);
    }

    private static void endGame(ServerLevel level, SixtySecondsState.Data data, boolean survivorsWin) {
        if (data.phase == SixtySecondsPhase.FINISHED) {
            return;
        }
        data.phase = SixtySecondsPhase.FINISHED;
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(level);
        GameUtils.WinStatus status = survivorsWin ? GameUtils.WinStatus.PASSENGERS : GameUtils.WinStatus.KILLERS;
        roundEnd.setRoundEndData(level.players(), status);
        GameUtils.stopGame(level);
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
