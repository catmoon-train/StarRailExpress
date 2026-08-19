package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnGameServerTick {

    /**
     * 游戏tick事件。当会议时会暂停（即不会调用）。
     */
    Event<OnGameServerTick> EVENT = createArrayBacked(OnGameServerTick.class,
            listeners -> (serverLevel) -> {
                for (OnGameServerTick listener : listeners) {
                    listener.onGameServerTick(serverLevel);
                }
            });

    /**
     * 游戏tick事件。当会议时会暂停（即不会调用）。
     */
    void onGameServerTick(ServerLevel serverLevel);
}
