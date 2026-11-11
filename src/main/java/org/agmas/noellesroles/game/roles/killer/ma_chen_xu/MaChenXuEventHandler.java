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

package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/** 统一注册布袋鬼与法器的生命周期；不再维护永久盾和标记返利分支。 */
public final class MaChenXuEventHandler {
    private static boolean registered;
    private MaChenXuEventHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        SackGhostItems.register();
        AfterShieldAllowPlayerDeath.EVENT.register((victim, reason) -> {
            var game = SREGameWorldComponent.KEY.get(victim.level());
            if (!game.isRole(victim, ModRoles.MA_CHEN_XU)) return true;
            var data = SackGhostItems.data(victim);
            if (data != null && data.otherworldActive) return false;
            if (game.isSkillAvailable) RoleUtils.changeRole(victim, TMMRoles.DISCOVERY_CIVILIAN);
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var data = SackGhostItems.data(handler.player);
            if (data != null) data.clear();
            for (var owner : handler.player.serverLevel().players()) {
                var other = SackGhostItems.data(owner);
                if (other != null) other.cleanse(handler.player);
            }
            handler.player.removeEffect(org.agmas.noellesroles.init.ModEffects.GHOST_CONDEMNED);
            SackGhostItems.clearWard(handler.player);
        });
    }
}
