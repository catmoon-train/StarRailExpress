package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.game.GameFunctions.WinStatus;

public interface AllowGameEnd {

    /**
     * Event callback to determine if a game is allowed to stop for a specific
     * win status.
     * The game currently has the following death type names defined:
     * NONE - DO NOT END,
     * NOT_MODIFY - DO NOT MODIFY (DEFAULT),
     * KILLERS,
     * PASSENGERS,
     * TIME,
     * LOOSE_END,
     * GAMBLER,
     * RECORDER,
     * CUSTOM - 记得修改 RoundEndComponent.CustomWinnerID 和
     * RoundEndComponent.CustomWinnersPredicates（判断是否为获胜者）
     * 
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<AllowGameEnd> EVENT = createArrayBacked(AllowGameEnd.class,
            listeners -> (serverWorld, winStatus, isLooseEndsMode) -> {
                for (AllowGameEnd listener : listeners) {
                    var a = listener.allowGameEnd(serverWorld, winStatus, isLooseEndsMode);
                    if (a != null)
                        if (!a.equals(WinStatus.NOT_MODIFY)) {
                            return a;
                        }
                }
                return WinStatus.NOT_MODIFY;
            });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    WinStatus allowGameEnd(ServerLevel serverWorld, GameFunctions.WinStatus winStatus, boolean isLooseEndsMode);
}