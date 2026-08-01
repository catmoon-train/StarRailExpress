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

package org.agmas.noellesroles.game.roles.killer.conspirator;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import io.wifi.starrailexpress.game.GameConstants;
import org.agmas.noellesroles.role.ModRoles;

public class ConspiratorKilledPlayer {
    public static void registerEvents() {
        OnGiveKillerBalance.EVENT.register((victim, killer, deathReason) -> {
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(killer, ModRoles.CONSPIRATOR)) {
                if ("heart_attack".equals(deathReason.getPath())) {
                    return -GameConstants.getMoneyPerKill();
                }
            }
            return 0;
        });

    }
}
