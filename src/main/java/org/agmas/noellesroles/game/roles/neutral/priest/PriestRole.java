package org.agmas.noellesroles.game.roles.neutral.priest;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 神父：平民阵营，由持有「神的使命」的钟表匠在场上只剩一名平民时转变而来。
 * 随平民获胜；完成咏诵并让时间停止后，好人获胜。
 */
public class PriestRole extends CustomWinnerRole {

    public PriestRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        if (winStatus == WinStatus.PASSENGERS || winStatus == WinStatus.TIME) {
            return true;
        }
        return original;
    }

    public static boolean openChant(ServerPlayer player) {
        return PriestHeavenManager.openChantScreen(player);
    }

    public static void submitLyric(ServerPlayer player, String text) {
        PriestHeavenManager.submitLyric(player, text);
    }
}
