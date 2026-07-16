package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.game.modes.funny.*;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.ChairWheelRaceGame;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameMode;
import net.exmo.sre.repair.RepairEscapeGameMode;

import java.util.HashMap;

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
    public static final ResourceLocation REFUGEE_LOVER_MODE_ID = SRE.wifiId("refugee_lover");
    public static final ResourceLocation HIDE_AND_SEEK_MODE_ID = SRE.wifiId("hide_and_seek");
    public static final ResourceLocation TNT_TAG_MODE_ID = SRE.wifiId("tnt_tag");
    public static final ResourceLocation VOLUNTEER_MODE_ID = SRE.wifiId("volunteer");

    // Author: canyuesama (catmoon233)
    public static final ResourceLocation REPAIR_ESCAPE_ID = SRE.canyueId("repair_escape");
    public static final ResourceLocation FOURTH_ROOM_ID = SRE.canyueId("fourth_room");

    // Author: xiao_hei_hand
    public static final ResourceLocation ANT_WAR_MODE_ID = SRE.xiaoheihandId("ant_war");
    public static final ResourceLocation SNIPER_RIFLE_ID = SRE.xiaoheihandId("sniper_war");
    public static final ResourceLocation EVIL_WAR_MODE_ID = SRE.xiaoheihandId("evil_war");
    public static final ResourceLocation DEVIL_ROULETTE_ID = SRE.xiaoheihandId("devil_roulette");
    public static final ResourceLocation THIEF_MODE_ID = SRE.xiaoheihandId("thief_mode");

    // Role Rotation Mode (haiman)
    public static final ResourceLocation ROLE_ROTATION_MODE_ID = SRE.haimanId("role_rotation");

    // Class Change Mode (haiman)
    public static final ResourceLocation CLASS_CHANGE_MODE_ID = SRE.haimanId("class_change");

    // Tradition Mode (sre)
    public static final ResourceLocation TRADITION_MODE_ID = SRE.shortId("tradition");

    // Discovery Mode (sre)
    public static final ResourceLocation DISCOVERY_MODE_ID = SRE.shortId("discovery");

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
    public static final GameMode REFUGEE_LOVER_MODE = registerGameMode(
            new SRERefugeeLoversGameMode(REFUGEE_LOVER_MODE_ID));
    public static final GameMode HIDE_AND_SEEK_MODE = registerGameMode(
            new SREHideAndSeekGameMode(HIDE_AND_SEEK_MODE_ID));
    public static final GameMode TNT_TAG_MODE = registerGameMode(new SRETNTTagGameMode(TNT_TAG_MODE_ID));
    public static final GameMode VOLUNTEER_MODE = registerGameMode(new SREVolunteerGameMode(VOLUNTEER_MODE_ID));
    //
    // written by canyuesama
    public static final GameMode FOURTH_ROOM = registerGameMode(new FourthRoomGameMode(FOURTH_ROOM_ID));
    public static final GameMode WHEELCHAR_GAME_MODE = registerGameMode(new ChairWheelRaceGame());

    // written by xiao_hei_hand
    public static final GameMode ANT_WAR_MODE = registerGameMode(new SREAntWarGameMode(ANT_WAR_MODE_ID));
    public static final GameMode SNIPER_RIFLE_MODE = registerGameMode(new SRESniperRifleGameMode(SNIPER_RIFLE_ID));
    public static final GameMode EVIL_WAR_MODE = registerGameMode(new SREEvilWarGameMode(EVIL_WAR_MODE_ID));
    public static final GameMode DEVIL_ROULETTE_MODE = registerGameMode(
            new SREDevilRouletteGameMode(DEVIL_ROULETTE_ID));
    public static final GameMode REPAIR_ESCAPE_MODE = registerGameMode(new RepairEscapeGameMode(REPAIR_ESCAPE_ID));

    public static final GameMode THIEF_MODE = registerGameMode(new SREThiefWarGameMode(THIEF_MODE_ID));

    // Role Rotation Mode
    public static final GameMode ROLE_ROTATION_MODE = registerGameMode(new SRERoleRotationGameMode(ROLE_ROTATION_MODE_ID));

    // Class Change Mode (haiman)
    public static final GameMode CLASS_CHANGE_MODE = registerGameMode(new SREClassChangeGameMode(CLASS_CHANGE_MODE_ID));

    // Tradition Mode (sre)
    public static final GameMode TRADITION_MODE = registerGameMode(new SRETraditionGameMode(TRADITION_MODE_ID));

    // Discovery Mode (sre)
    public static final GameMode DISCOVERY_MODE = registerGameMode(new SREDiscoveryGameMode(DISCOVERY_MODE_ID));

    // register
    public static GameMode registerGameMode(GameMode gameMode) {
        GAME_MODES.put(gameMode.identifier, gameMode);
        return gameMode;
    }
}
