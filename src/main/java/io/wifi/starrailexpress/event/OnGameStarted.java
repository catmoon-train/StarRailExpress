package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：游戏开始时触发（在安全时间内）。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when the game truly starts (as opposed to the preparation phase).
 * All listeners are invoked (non-cancellable event).
 */
public interface OnGameStarted {

    /**
     * 游戏正式开始时触发的事件。
     *
     * <p>Event callback fired when the game truly starts.
     */
    Event<OnGameStarted> EVENT = createArrayBacked(OnGameStarted.class,
            listeners -> (sl) -> {
                for (OnGameStarted listener : listeners) {
                    listener.onGameStarted(sl);
                }
            });

    /**
     * 游戏正式开始时的回调方法。
     *
     * <p>Callback invoked when the game truly starts.
     *
     * @param serverLevel 游戏所在的服务端世界 / the server level where the game is taking place
     */
    void onGameStarted(ServerLevel serverLevel);
}
