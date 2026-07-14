package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * 隐藏通关 · 救援信标：工程学科技合成「救援信标」，游戏日在<b>户外</b>（自家住宅/避难所之外）激活 →
 * 全服广播 + {@link SixtySecondsBalance#RESCUE_COUNTDOWN_TICKS} 倒计时；期间激活队伍<b>全灭则救援取消</b>，
 * 撑到倒计时结束 → 救援抵达，幸存者胜（{@link SixtySecondsWinConditions#rescueArrived}）。
 * 全局单槽会话（一局同时只有一次救援呼叫）。
 */
public final class SixtySecondsRescue {
    private static long endTick = -1;
    private static int teamId = -1;

    private SixtySecondsRescue() {
    }

    public static boolean isActive() {
        return endTick >= 0;
    }

    /** 激活信标（由物品调用；调用方已校验相位/位置）。 */
    public static void activate(ServerLevel level, ServerPlayer player) {
        endTick = level.getGameTime() + SixtySecondsBalance.RESCUE_COUNTDOWN_TICKS;
        teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        int seconds = SixtySecondsBalance.RESCUE_COUNTDOWN_TICKS / 20;
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.rescue_activated",
                player.getGameProfile().getName(), seconds).withStyle(ChatFormatting.GOLD));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 游戏日每 tick 推进：队伍全灭→取消；到点→救援抵达胜利。 */
    public static void tick(ServerLevel level, SixtySecondsState.Data data) {
        if (endTick < 0 || data.phase != SixtySecondsPhase.DAY) {
            return;
        }
        long now = level.getGameTime();
        if (now % 20 != 0) {
            return;
        }
        // 激活队伍全灭 → 救援取消
        if (!teamAlive(level, data)) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.rescue_cancelled")
                    .withStyle(ChatFormatting.RED));
            reset();
            return;
        }
        long remaining = endTick - now;
        if (remaining <= 0) {
            reset();
            SixtySecondsWinConditions.rescueArrived(level, data);
            return;
        }
        // 最后 10 秒每秒报数，其余每 30 秒提示
        int seconds = (int) (remaining / 20);
        if (seconds <= 10 || seconds % 30 == 0) {
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.rescue_countdown", seconds)
                        .withStyle(seconds <= 10 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
            }
        }
    }

    private static boolean teamAlive(ServerLevel level, SixtySecondsState.Data data) {
        SixtySecondsState.TeamData team = data.teams.get(teamId);
        if (team == null) {
            return false;
        }
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member
                    && GameUtils.isPlayerAliveAndSurvival(member)
                    && !SixtySecondsStatsComponent.KEY.get(member).monster) {
                return true;
            }
        }
        return false;
    }

    public static void reset() {
        endTick = -1;
        teamId = -1;
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
