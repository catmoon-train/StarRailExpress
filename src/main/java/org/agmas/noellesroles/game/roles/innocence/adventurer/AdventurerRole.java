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

package org.agmas.noellesroles.game.roles.innocence.adventurer;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.bouns.roles.ScoutRole;

public final class AdventurerRole extends NormalRole {
    public AdventurerRole(ResourceLocation id, int color, boolean innocent, boolean killer,
                          MoodType mood, int sprint, boolean hide) {
        super(id, color, innocent, killer, mood, sprint, hide);
    }

    /**
     * 冒险家在 PEAK 爬山图上也复用童子军的攀爬逻辑（其它地图照旧不能爬）。
     *
     * <p>这里只覆写「能否攀爬」的判定：两端都会用 {@link ScoutRole#isPeakMap}，
     * 地图特性已经随 {@code AreasWorldComponent} 同步到客户端（客户端就是
     * {@code SREClient.areaComponent}），所以客户端同样能算。
     * 攀爬的运行状态挂在 Player 上，不需要额外的 RoleData。
     */
    @Override
    public boolean canClimbWalls(Player player) {
        return player != null && player.level() != null && ScoutRole.isPeakMap(player.level());
    }
}
