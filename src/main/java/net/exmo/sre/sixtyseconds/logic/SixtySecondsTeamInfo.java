package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * {@code /sre:60s info}：向玩家汇总本队状态——成员健康、家门耐久/等级、供电剩余、
 * 已解锁科技、个人游戏币（全部服务端现成数据，零额外同步）。
 */
public final class SixtySecondsTeamInfo {

    private SixtySecondsTeamInfo() {
    }

    public static void send(ServerPlayer player) {
        if (!SixtySecondsMod.isActive(player.level())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"), false);
            return;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.info_no_team"), false);
            return;
        }
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.info_header", team.teamId + 1)
                .withStyle(ChatFormatting.GOLD), false);
        // 成员：名字(身份) 健康 / 状态
        for (UUID uuid : team.members) {
            if (!(level.getPlayerByUUID(uuid) instanceof ServerPlayer member)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(member);
            String stateKey = !GameUtils.isPlayerAliveAndSurvival(member) ? "info_state_dead"
                    : stats.monster ? "info_state_monster"
                            : stats.downed ? "info_state_downed"
                                    : stats.sick ? "info_state_sick" : "info_state_ok";
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.info_member",
                    member.getGameProfile().getName(), stats.health,
                    Component.translatable("message.noellesroles.sixty_seconds." + stateKey)), false);
        }
        // 家门 / 供电 / 代币
        MutableComponent door = Component.translatable("message.noellesroles.sixty_seconds.info_door",
                team.doorHp, team.doorMaxHp, team.doorLevel);
        if (team.doorBroken) {
            door.append(Component.translatable("message.noellesroles.sixty_seconds.info_door_broken")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        player.displayClientMessage(door, false);
        long powerLeft = Math.max(0, (team.powerEndTick - level.getGameTime()) / 20);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.info_power", powerLeft), false);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.info_tokens",
                io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(player).getTokens()), false);
        // 科技
        if (team.unlockedTech.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.info_tech_none"), false);
        } else {
            MutableComponent tech = Component.empty();
            boolean first = true;
            for (String id : team.unlockedTech) {
                if (!first) {
                    tech.append(Component.literal(", "));
                }
                tech.append(Component.translatable("tech.noellesroles.sixty_seconds." + id));
                first = false;
            }
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.info_tech", tech), false);
        }
    }
}
