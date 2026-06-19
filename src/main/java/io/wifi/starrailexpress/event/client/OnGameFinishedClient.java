package io.wifi.starrailexpress.event.client;

import net.fabricmc.fabric.api.event.Event;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;


public interface OnGameFinishedClient {



    Event<OnGameFinishedClient> EVENT = createArrayBacked(OnGameFinishedClient.class,
            listeners -> () -> {
                for (OnGameFinishedClient listener : listeners) {
                    listener.gameFinished();
                }
            });



    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    void gameFinished();
}