package org.agmas.harpymodloader.events;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface GameInitializeEvent {

    Event<GameInitializeEvent> EVENT = createArrayBacked(GameInitializeEvent.class,
            listeners -> (serverWorld, gameWorldComponent, players) -> {
                for (GameInitializeEvent listener : listeners) {
                    listener.initializeGame(serverWorld, gameWorldComponent, players);
                }
            });

    void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players);
}