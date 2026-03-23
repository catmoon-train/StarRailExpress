package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：决定是否允许游戏结束，以及最终的胜利状态。
 * 首个返回非 {@link WinStatus#NOT_MODIFY} 结果的监听器决定最终胜利状态。
 *
 * <p>Event interface to determine whether the game is allowed to end and what the win status should be.
 * The first listener returning a value other than {@link WinStatus#NOT_MODIFY} determines the outcome.
 */
public interface AllowGameEnd {

    /**
     * 决定游戏是否允许结束及最终胜利状态的事件。
     * 当前支持的胜利状态：
     * NONE（不结束）、NOT_MODIFY（不修改，默认）、KILLERS、PASSENGERS、
     * TIME、LOOSE_END、GAMBLER、RECORDER、
     * CUSTOM（记得修改 RoundEndComponent.CustomWinnerID 和
     * RoundEndComponent.CustomWinnersPredicates）。
     *
     * <p>Event callback to determine if a game is allowed to stop and what the win status should be.
     * Supported win statuses:
     * NONE - DO NOT END,
     * NOT_MODIFY - DO NOT MODIFY (DEFAULT),
     * KILLERS, PASSENGERS, TIME, LOOSE_END, GAMBLER, RECORDER,
     * CUSTOM - remember to set RoundEndComponent.CustomWinnerID and
     * RoundEndComponent.CustomWinnersPredicates.
     *
     * @see io.wifi.starrailexpress.game.GameUtils.WinStatus
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

    /**
     * 决定游戏是否允许结束以及最终胜利状态。
     *
     * <p>Determines whether the game is allowed to end and what the final win status should be.
     *
     * @param serverWorld      游戏所在的服务端世界 / the server level where the game is taking place
     * @param winStatus        当前拟定的胜利状态 / the currently proposed win status
     * @param isLooseEndsMode  是否处于散局模式 / whether the game is in loose-ends mode
     * @return 最终胜利状态；返回 {@link WinStatus#NOT_MODIFY} 则不修改 /
     *         the final win status; return {@link WinStatus#NOT_MODIFY} to leave it unchanged
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus, boolean isLooseEndsMode);
}