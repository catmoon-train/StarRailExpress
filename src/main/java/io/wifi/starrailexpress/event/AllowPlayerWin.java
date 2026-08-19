package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface AllowPlayerWin {

    /**
     * 玩家是否获胜
     */
    Event<AllowPlayerWin> EVENT = createArrayBacked(AllowPlayerWin.class,
            listeners -> (world, player, playerRole,
                    winStatus, roundEnd,
                    gameComponent) -> {
                for (AllowPlayerWin listener : listeners) {
                    TrueFalseResult result = listener.allowPlayerWin(world, player, playerRole,
                            winStatus, roundEnd,
                            gameComponent);
                    if (result != null && result != TrueFalseResult.PASS) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 玩家是否获胜
     */
    TrueFalseResult allowPlayerWin(ServerLevel world, ServerPlayer player, SRERole playerRole, WinStatus winStatus,
            SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent);
}
