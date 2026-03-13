package org.agmas.harpymodloader.events;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import java.util.List;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;

public interface GameInitializeEvent {

    Event<GameInitializeEvent> EVENT = createArrayBacked(GameInitializeEvent.class,
            listeners -> (serverWorld, gameWorldComponent, players) -> {
                for (GameInitializeEvent listener : listeners) {
                    listener.initializeGame(serverWorld, gameWorldComponent, players);
                }
            });

    void initializeGame(ServerLevel serverWorld, StarGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players);
}