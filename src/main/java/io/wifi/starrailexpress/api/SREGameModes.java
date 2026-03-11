package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.game.LooseEndsGameMode;
import io.wifi.starrailexpress.game.StarRailMurderGameMode;
import io.wifi.starrailexpress.SRE;

import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;

public class SREGameModes {
    public static final HashMap<ResourceLocation, GameMode> GAME_MODES = new HashMap<>();

    public static final ResourceLocation MURDER_ID = SRE.id("murder");
    public static final ResourceLocation DISCOVERY_ID = SRE.id("discovery");
    public static final ResourceLocation LOOSE_ENDS_ID = SRE.id("loose_ends");

    public static final GameMode MURDER = registerGameMode(MURDER_ID, new StarRailMurderGameMode(MURDER_ID));
    public static final GameMode LOOSE_ENDS = registerGameMode(LOOSE_ENDS_ID, new LooseEndsGameMode(LOOSE_ENDS_ID));

    public static GameMode registerGameMode(ResourceLocation identifier, GameMode gameMode) {
        GAME_MODES.put(identifier, gameMode);
        return gameMode;
    }
}
