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

package org.agmas.noellesroles.game.roles.neutral.panda;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.neutral.LeaderRoleData;
import org.agmas.noellesroles.role_data.neutral.MonokumaRoleData;

/**
 * 熊猫外观挂在当前职业的 RoleData 上（黑白熊本人或被转化的领袖）。
 */
public final class PandaState {
    private PandaState() {
    }

    public static void setPanda(Player player, boolean panda) {
        MonokumaRoleData monokuma = RoleData.getNullable(MonokumaRoleData.class, player);
        if (monokuma != null) {
            monokuma.setPandaForm(panda);
            return;
        }
        LeaderRoleData leader = RoleData.getNullable(LeaderRoleData.class, player);
        if (leader != null) {
            leader.setPandaForm(panda);
        }
    }

    public static boolean isPanda(Player player) {
        MonokumaRoleData monokuma = RoleData.getNullable(MonokumaRoleData.class, player);
        if (monokuma != null) {
            return monokuma.isPanda;
        }
        LeaderRoleData leader = RoleData.getNullable(LeaderRoleData.class, player);
        return leader != null && leader.isPanda;
    }
}
