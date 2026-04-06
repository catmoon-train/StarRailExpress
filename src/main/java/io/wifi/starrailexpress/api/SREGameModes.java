package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameMode;
import io.wifi.starrailexpress.game.StarRailMurderGameMode;
import io.wifi.starrailexpress.game.WTLooseEndsGameMode;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;

public class SREGameModes {
    public static final HashMap<ResourceLocation, GameMode> GAME_MODES = new HashMap<>();

    public static final ResourceLocation MURDER_ID = SRE.shortId("murder");
    public static final ResourceLocation DISCOVERY_ID = SRE.shortId("discovery");
    public static final ResourceLocation LOOSE_ENDS_ID = SRE.watheId("loose_ends");
    public static final ResourceLocation FOURTH_ROOM_ID = SRE.shortId("fourth_room");
    public static final ResourceLocation FOUTH_ROOM_ID = SRE.shortId("fouth_room");

    public static final GameMode MURDER = registerGameMode(MURDER_ID, new StarRailMurderGameMode(MURDER_ID));
    public static final GameMode LOOSE_ENDS = registerGameMode(LOOSE_ENDS_ID, new WTLooseEndsGameMode(LOOSE_ENDS_ID));
    public static final GameMode FOURTH_ROOM = registerGameMode(FOURTH_ROOM_ID, new FourthRoomGameMode(FOURTH_ROOM_ID));

    static {
        GAME_MODES.put(FOUTH_ROOM_ID, FOURTH_ROOM);
    }

    public static GameMode registerGameMode(ResourceLocation identifier, GameMode gameMode) {
        GAME_MODES.put(identifier, gameMode);
        return gameMode;
    }
}
