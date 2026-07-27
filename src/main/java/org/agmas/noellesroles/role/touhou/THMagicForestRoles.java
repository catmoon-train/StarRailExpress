package org.agmas.noellesroles.role.touhou;

import org.agmas.noellesroles.role.touhou.roles.THMarisaRole;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.resources.ResourceLocation;

public class THMagicForestRoles {
    public static final String NAMESPACE = "th_magic_forest";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // Kirisame Marisa
    public static final ResourceLocation KIRISAME_MARISA_ID = id("kirisame_marisa");
    public static SRERole KIRISAME_MARISA = TMMRoles
            .registerRole(new THMarisaRole(KIRISAME_MARISA_ID, new Color(172, 154, 104).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true),"magic_forest")
            .setCanSetSpawnInfoInConfig(true).setDefaultMax(1)
            .setDefaultEnableNeededPlayerCount(18).setDefaultEnableChance(1000)
            .setFallDamageImmune(true); // 不会因高度限制摔死

    public static void init() {
    }
}
