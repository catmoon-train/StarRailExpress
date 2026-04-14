package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameMode;
import io.wifi.starrailexpress.game.modes.*;
import io.wifi.starrailexpress.game.modes.funny.*;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;

import org.agmas.noellesroles.game.ChairWheelRaceGame;

public class SREGameModes {
    public static final HashMap<ResourceLocation, GameMode> GAME_MODES = new HashMap<>();

    // Modified from Harpymodloader
    public static final ResourceLocation MURDER_ID = SRE.shortId("murder");

    // Wathe Original Mode
    public static final ResourceLocation LOOSE_ENDS_ID = SRE.watheId("loose_ends");

    // Author: wifi_left
    public static final ResourceLocation GAMBLER_MODE_ID = SRE.wifiId("gambler");
    public static final ResourceLocation CUSTOM_SELECTED_MODE_ID = SRE.wifiId("role_pick");
    public static final ResourceLocation LOVERS_MODE_ID = SRE.wifiId("lover");
    public static final ResourceLocation REFUGEE_MODE_ID = SRE.wifiId("refugee");

    // Author: canyuesama (catmoon233)
    public static final ResourceLocation FOURTH_ROOM_ID = SRE.canyueId("fourth_room");

    // Author: xiao_hei_hand
    public static final ResourceLocation ANT_WAR_MODE_ID = SRE.xiaoheihandId("ant_war");
    public static final ResourceLocation SNIPER_RIFLE_ID = SRE.xiaoheihandId("sniper_war");

    // Modified from Harpymodloader
    public static final GameMode MURDER = registerGameMode(new SREMurderGameMode(MURDER_ID));

    // Wathe Original Mode
    public static final GameMode LOOSE_ENDS = registerGameMode(new WTLooseEndsGameMode(LOOSE_ENDS_ID));

    // written by wifi
    public static final GameMode GAMBLER_MODE = registerGameMode(new SREGamblerGameMode(GAMBLER_MODE_ID));
    public static final GameMode CUSTOM_SELECTED_MODE = registerGameMode(
            new SRECustomRoleGameMode(CUSTOM_SELECTED_MODE_ID));
    public static final GameMode LOVERS_MODE = registerGameMode(new SRELoverGameMode(LOVERS_MODE_ID));
    public static final GameMode REFUGEE_MODE = registerGameMode(new SRERefugeeGameMode(REFUGEE_MODE_ID));

    // written by canyuesama
    public static final GameMode FOURTH_ROOM = registerGameMode(new FourthRoomGameMode(FOURTH_ROOM_ID));
    public static final GameMode WHEELCHAR_GAME_MODE = registerGameMode(new ChairWheelRaceGame());

    // written by xiao_hei_hand
    public static final GameMode ANT_WAR_MODE = registerGameMode(new SREAntWarGameMode(ANT_WAR_MODE_ID));
    public static final GameMode SNIPER_RIFLE_MODE = registerGameMode(new SniperRifleGameMode(SNIPER_RIFLE_ID));

    // register
    public static GameMode registerGameMode(GameMode gameMode) {
        GAME_MODES.put(gameMode.identifier, gameMode);
        return gameMode;
    }
}
