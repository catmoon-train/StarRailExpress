package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import java.awt.Color;

public class EggRoles {
    public static final String NAMESPACE = "egg";
    public static final ResourceLocation LENGXIAO_ID = id("lengxiao");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * 职业：冷笑
     * 巫毒对立职业
     */
    public static SRERole REMILIA = TMMRoles.registerRole(
            new NormalRole(LENGXIAO_ID, new Color(230, 178, 130).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true)).setDefaultEnableChance(10);

    public static void init() {
    }

}
