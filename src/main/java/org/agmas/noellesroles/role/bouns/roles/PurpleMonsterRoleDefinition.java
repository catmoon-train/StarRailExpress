package org.agmas.noellesroles.role.bouns.roles;

import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Keeps Purple Monster in the special-neutral team while allowing faction attachment after its goal. */
public final class PurpleMonsterRoleDefinition extends NormalRole implements CustomWinnerRoleInterface {
    public PurpleMonsterRoleDefinition(ResourceLocation id, int color, RoleType roleType, MoodType moodType,
                                       int maxSprintTime, boolean canSeeTime) {
        super(id, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus winStatus) {
        if (original) return true;
        return PurpleMonsterRole.hasReachedGoal(player)
                && (winStatus == GameUtils.WinStatus.PASSENGERS
                || winStatus == GameUtils.WinStatus.KILLERS
                || winStatus == GameUtils.WinStatus.TIME);
    }
}
