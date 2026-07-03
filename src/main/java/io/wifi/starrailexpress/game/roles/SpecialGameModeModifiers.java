package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SRE;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;

import java.awt.*;
import java.util.HashSet;

public class SpecialGameModeModifiers {

    public static SREModifier TNT_TAGGED = HMLModifiers.registerModifier(new SREModifier(
            SRE.wifiId("tnt_tagged"),
            Color.RED.getRGB(),
            null,
            new HashSet<>(),
            false,
            false)).setDefaultMax(0).setOtherModeRole(true).setCanSetSpawnInfoInConfig(false)
            .setSpawnInfo(new SpawnInfo().setMax(0));

    public static void init() {

    }
}
