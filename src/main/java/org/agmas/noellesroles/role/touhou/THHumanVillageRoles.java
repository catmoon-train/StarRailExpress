package org.agmas.noellesroles.role.touhou;

import net.minecraft.resources.ResourceLocation;

import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.roles.*;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;
import org.agmas.noellesroles.role_data.killer.HoujuuNueRoleData;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.resources.ResourceLocation;

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
            true))
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

    public static void init() {
    }
}
