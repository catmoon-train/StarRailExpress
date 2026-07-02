package io.wifi.starrailexpress.event.client;

import net.fabricmc.fabric.api.event.Event;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;


public interface OnGameStartedClient {
    Event<OnGameStartedClient> EVENT = createArrayBacked(OnGameStartedClient.class,
            listeners -> () -> {
                for (OnGameStartedClient listener : listeners) {
                    listener.gameStarted();
                }
            });



    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    void gameStarted();
}