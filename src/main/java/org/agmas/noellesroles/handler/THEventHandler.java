/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.handler;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants.DeathReasons;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.MAID_SAKUYA)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.REMILIA))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.PACHURI)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU))
                    return false;
            }
            return true;
        });
    }

    public static SRERole getRandomRole() {
        var roles = Noellesroles.getEnableAndAvailableRoles(true);
        if (roles.isEmpty())
            return TMMRoles.KILLER;
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
