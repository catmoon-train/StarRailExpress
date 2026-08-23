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

package org.agmas.noellesroles.game.roles.innocence.voodoo;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.VoodooRoleData;

import java.util.UUID;

public class VoodooDeathHandler {
    public static void registerEvents() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (NoellesRolesConfig.HANDLER.instance().voodooNonKillerDeaths || killer != null
                    || SREGameWorldComponent.KEY.get(victim.level()).gameMode.identifier
                            .equals(SREGameModes.TNT_TAG_MODE.identifier)) {
                SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                        .get(victim.level());
                boolean isLengxiao = gameWorldComponent.isRole(victim, BounsRoles.LENGXIAO);
                boolean isVoodoo = gameWorldComponent.isRole(victim, ModRoles.VOODOO);
                if (isLengxiao || isVoodoo) {
                    UUID voodooTarget = RoleData.getOptional(VoodooRoleData.class, victim)
                            .map(d -> d.target).orElse(null);
                    if (voodooTarget != null) {
                        Player voodooed = victim.level().getPlayerByUUID(voodooTarget);
                        if (voodooed != null) {
                            if (GameUtils.isPlayerAliveAndSurvival(voodooed) && voodooed != victim) {
                                ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) voodooed);
                                if (isVoodoo) {
                                    GameUtils.forceKillPlayer(voodooed, true, null, Noellesroles.id("voodoo"));
                                } else {
                                    GameUtils.killPlayer(voodooed, false, victim, GameConstants.DeathReasons.GOD_COMMAND);
                                }

                            }
                        }
                    }
                }
            }
        });
    }
}
