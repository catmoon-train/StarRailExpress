package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;

/**
 * 彩蛋角色类，受到彩蛋刷新概率影响
 */
public class BounsRoles {
    public static final String NAMESPACE = "egg";
    public static final ResourceLocation LENGXIAO_ID = id("lengxiao");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * 职业：冷笑
     * 巫毒对立职业
     */
    public static SRERole LENGXIAO = TMMRoles.registerRole(
            new NormalRole(LENGXIAO_ID, new Color(230, 178, 130).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true) {
                @Override
                public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
                    ResourceLocation texture = SRE.id("block/plush/lengxiaocn.png");
                    return texture;
                }
            })
            .setDefaultEnableChance(10);

    public static void init() {
    }

}
