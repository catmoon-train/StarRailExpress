package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 撬棍/撬锁器闯入别队避难所：
 * <ul>
 *   <li><b>撬棍</b>（alarms=true）：强行闯入并<b>触发目标队报警</b>。</li>
 *   <li><b>撬锁器</b>（alarms=false）：<b>潜行进入不报警</b>。</li>
 * </ul>
 * 均由物品一次性消耗（见 {@code SixtySecondsBreakInItem}）。P0：随机选一支别队；指定目标(选人 GUI)为后续。
 */
public final class SixtySecondsBreakIn {
    private SixtySecondsBreakIn() {
    }

    /** @return 是否成功（成功才消耗物品）。 */
    public static boolean use(ServerPlayer player, boolean alarms) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        int myTeam = SixtySecondsStatsComponent.KEY.get(player).teamId;

        List<SixtySecondsState.TeamData> targets = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.teamId != myTeam && team.shelterSpawn != null) {
                targets.add(team);
            }
        }
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_no_target"), true);
            return false;
        }
        SixtySecondsState.TeamData target = targets.get(level.getRandom().nextInt(targets.size()));
        player.teleportTo(level, target.shelterSpawn.getX() + 0.5D, target.shelterSpawn.getY(),
                target.shelterSpawn.getZ() + 0.5D, player.getYRot(), player.getXRot());

        if (alarms) {
            for (UUID uuid : target.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                    member.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_alarm")
                            .withStyle(ChatFormatting.RED), false);
                    member.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.7F);
                }
            }
            player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_forced")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_sneak")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return true;
    }
}
