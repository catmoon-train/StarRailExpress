package io.wifi.starrailexpress.game.modes;

import java.util.ArrayList;

import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class SRERoleAssignDebugGameMode extends SREMurderGameMode {

    public SRERoleAssignDebugGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.TIME;
        SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(new ArrayList<>(serverWorld.players()),
                winStatus);
        GameUtils.stopGame(serverWorld);
    }

}
