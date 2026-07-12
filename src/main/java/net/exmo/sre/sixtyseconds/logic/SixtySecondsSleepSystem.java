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

/**
 * 睡眠结算：每个游戏日尾声（最后 {@link SixtySecondsBalance#NIGHT_WINDOW_TICKS}）为夜晚——
 * 在家（住宅/避难所）床上睡觉回血；不在床或在户外则扣血，户外还有概率生病。
 */
public final class SixtySecondsSleepSystem {
    private static final int MAX = SixtySecondsStatsComponent.MAX;

    private SixtySecondsSleepSystem() {
    }

    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return;
        }
        long now = level.getGameTime();
        long remaining = data.phaseEndTick - now;
        if (remaining == SixtySecondsBalance.NIGHT_WINDOW_TICKS) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.night_fall")
                    .withStyle(ChatFormatting.BLUE));
        }
        boolean night = remaining > 0 && remaining <= SixtySecondsBalance.NIGHT_WINDOW_TICKS;
        if (!night || now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed) {
                continue;
            }
            boolean inHome = isInHome(player, data, stats.teamId);
            if (player.isSleeping() && inHome) {
                if (stats.health < MAX) {
                    stats.health = Math.min(MAX, stats.health + SixtySecondsBalance.SLEEP_HEAL_PER_SEC);
                    stats.sync();
                }
            } else {
                stats.health = Math.max(0, stats.health - SixtySecondsBalance.NIGHT_NO_SLEEP_LOSS_PER_SEC);
                stats.sync();
                if (stats.health <= 0) {
                    SixtySecondsHealthSystem.onHealthZero(player, false, null);
                    continue;
                }
                if (!inHome && now % (20 * 10) == 0
                        && level.getRandom().nextDouble() < SixtySecondsBalance.NIGHT_OUTDOOR_SICK_CHANCE) {
                    SixtySecondsSicknessSystem.makeSick(player);
                }
            }
        }
    }

    private static boolean isInHome(ServerPlayer player, SixtySecondsState.Data data, int teamId) {
        SixtySecondsState.TeamData team = data.teams.get(teamId);
        if (team == null) {
            return false;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        return (team.residentialBox != null && team.residentialBox.contains(x, y, z))
                || (team.shelterBox != null && team.shelterBox.contains(x, y, z));
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
