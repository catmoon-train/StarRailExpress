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
