package io.wifi.starrailexpress.game.roles;

import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import io.wifi.starrailexpress.SRE;

import java.awt.Color;
import java.util.HashSet;

public class SpecialGameModeModifiers {

    public static SREModifier TNT_TAGGED = HMLModifiers.registerModifier(new SREModifier(
            SRE.wifiId("tnt_tagged"),
            Color.RED.getRGB(),
            null,
            new HashSet<>(),
            false,
            false)).setDefaultMax(0).setOtherModeRole(true);

    public static void init() {

    }
}
