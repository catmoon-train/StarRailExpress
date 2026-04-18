package org.agmas.noellesroles.game.modes.fourthroom.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class FourthRoomGameMode extends GameMode {

    public FourthRoomGameMode(ResourceLocation identifier) {
        super(identifier, 45, 2);
    }

    @Override
    public boolean requiresAssignedRole() {
        return false;
    }

    @Override
    public boolean enforcesPlayAreaElimination() {
        return false;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        FourthRoomGameManager.of(serverWorld).tickServer();
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        FourthRoomGameManager.of(serverWorld).initializeMatch(players);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        FourthRoomGameManager.of(serverWorld).shutdownMatch();
    }

    public boolean hasMood() {
        return false;
    }
}