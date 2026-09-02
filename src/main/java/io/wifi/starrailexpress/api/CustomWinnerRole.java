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

package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.utils.RoleUtils;

public abstract class CustomWinnerRole extends NormalRole implements CustomWinnerRoleInterface {
    public CustomWinnerRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public CustomWinnerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        return WinStatus.NOT_MODIFY;
    };

    /**
     * 调用RoleUtils.customWinnerWin。若
     * {@code checkWin} 返回为 {@code WinStatus.CUSTOM}，将会自动调用此方法
     */
    public void win(ServerPlayer player) {
        RoleUtils.customWinnerWin(player.serverLevel(), this.identifier().getPath(), this.color());
    }

    /**
     * 玩家是否获胜。在获胜统计时被调用。
     */
    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        return original;
    }
}
