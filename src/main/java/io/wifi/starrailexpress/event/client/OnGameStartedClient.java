package io.wifi.starrailexpress.event.client;

import net.fabricmc.fabric.api.event.Event;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;


public interface OnGameStartedClient {



    Event<OnGameStartedClient> EVENT = createArrayBacked(OnGameStartedClient.class,
            listeners -> () -> {


            });



    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    void gameStarted();
}