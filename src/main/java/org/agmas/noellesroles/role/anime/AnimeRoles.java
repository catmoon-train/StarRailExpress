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

package org.agmas.noellesroles.role.anime;

import org.agmas.noellesroles.role.anime.roles.*;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.resources.ResourceLocation;

public class AnimeRoles {
    public static final String NAMESPACE = "anime";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final SRERole KAFU_CHINO = new ChinoRole(
            id("kafu_chino"), new Color(235, 238, 255).getRGB(), RoleType.CIVILIAN, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false)
            .setDefaultEnableChance(7000);
    public static final SRERole HOTO_KOKOA = new KokoaRole(
            id("hoto_kokoa"), new Color(250, 204, 165).getRGB(), RoleType.CIVILIAN, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false)
            .setDefaultEnableChance(7000);

    public static void init() {
    }
}
