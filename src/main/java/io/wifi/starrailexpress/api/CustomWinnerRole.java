package io.wifi.starrailexpress.api;

import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public abstract class CustomWinnerRole extends NormalRole {
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

    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus){
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
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus){
        return original;
    }
}
