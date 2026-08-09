package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.server.level.ServerPlayer;

/**
 * CustomWinnerRoleInterface
 */
public interface CustomWinnerRoleInterface {

    default WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        return WinStatus.NOT_MODIFY;
    };

    /**
     * 玩家是否获胜。在获胜统计时被调用。
     */
    default boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        return original;
    }
}
