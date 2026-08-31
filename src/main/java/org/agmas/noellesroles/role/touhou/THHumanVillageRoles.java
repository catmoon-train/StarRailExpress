package org.agmas.noellesroles.role.touhou;

import net.minecraft.resources.ResourceLocation;

import org.agmas.noellesroles.role.touhou.roles.*;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.Color;

public class THHumanVillageRoles {

    public static final String NAMESPACE = "th_human_village";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // 森近霖之助 Morichika Rinnosuke
    public static SRERole RINNOSUKE = TMMRoles.registerRole(new THRinnosukeRole(
            id("morichika_rinnosuke"), // 角色 ID
            new Color(252, 250, 249).getRGB(),
            false, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            MoodType.REAL, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true), "th_human_village")
            .setNeutrals(true)
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(100)
            .setCanUseInstinctAndNightVision(false)
            .setCanPickUpRevolver(false)
            .addBothRelatedRole(THMountainRoles.NITORI)
            .setServerGameTickEvent((player, cca) -> {
                if (player.level().getGameTime() % (20 * 60) == 0) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(50);
                }
            })
            .setAddedVersion("4.4");

    // 东风谷早苗 kotiya_sanae
    public static SRERole KOTIYA_SANAE = TMMRoles.registerRole(new THKotiyaSanaeRole(
            id("kotiya_sanae"), // 角色 ID
            new Color(131, 169, 151).getRGB(),
            RoleType.CIVILIAN,
            MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS,
            false), "th_human_village")
            .setAddedVersion("4.4")
            .setDefaultEnableChance(3000)
            .setDefaultEnableNeededPlayerCount(16);

    public static void init() {
    }
}
