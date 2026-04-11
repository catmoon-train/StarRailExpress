package org.agmas.noellesroles.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.RedHouseRoles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants.DeathReasons;
import net.minecraft.resources.ResourceLocation;

public class THEventHandler {
    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return true;
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(killer, RedHouseRoles.FURANDORU)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.REMILIA)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.PACHURI)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.MAID_SAKUYA))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.REMILIA)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.PACHURI))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.MAID_SAKUYA)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.REMILIA))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.PACHURI)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.REMILIA))
                    return false;
            }
            return true;
        });
    }

    public static SRERole getRandomRole() {
        var roles = Noellesroles.getEnableAndAvailableRoles(false);
        if (roles.isEmpty())
            return TMMRoles.CIVILIAN;
        Collections.shuffle(roles);
        return roles.getFirst();
    }

    public static String getRandomDeathReason() {
        ArrayList<ResourceLocation> list = new ArrayList<>(List.of(DeathReasons.KNIFE,
                DeathReasons.REVOLVER,
                DeathReasons.DERRINGER,
                DeathReasons.BAT,
                DeathReasons.GRENADE,
                DeathReasons.POISON,
                DeathReasons.ARROW));
        Collections.shuffle(list);
        return list.getFirst().toString();
    }
}
