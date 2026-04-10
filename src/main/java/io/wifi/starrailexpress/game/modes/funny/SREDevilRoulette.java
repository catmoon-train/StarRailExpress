package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class SREDevilRoulette  extends GameMode {
    /**
     * @param identifier       the game mode identifier
     * @param defaultStartTime the default time at which the timer will be set at
     *                         the start of the game mode, in minutes
     * @param minPlayerCount   the minimum amount of players required to start the
     *                         game mode
     */
    public SREDevilRoulette(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        super(identifier, defaultStartTime, minPlayerCount);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {

    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {

    }
}
