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

package org.agmas.noellesroles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.event.EarlyKillPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.item.BombItem;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.ConspiratorRoleData;

public class TrueKillerFinder {

    public static void registerEvents() {
        EarlyKillPlayer.FIND_KILLER_EVENT.register((victim, originalKiller, deathReason, force) -> {
            if (!(victim instanceof ServerPlayer serverVictim))
                return null;
            Player bomber = BombItem.findBomber(victim, originalKiller, deathReason);
            if (bomber != null) {
                BombItem.explode(serverVictim, bomber);
                // GameUtils.killPlayer(victim, false, bomber,
                // GameConstants.DeathReasons.BOMB_DEATH);
                return originalKiller;
            }
            // Noellesroles.LOGGER.info("!!!");
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            var poisonerC = SREPlayerPoisonComponent.KEY.maybeGet(victim).orElse(null);
            if (poisonerC != null) {
                if (poisonerC.poisoner != null && poisonerC.poisonTicks >= 0) {
                    var poisonerP = serverVictim.level().getPlayerByUUID(poisonerC.poisoner);
                    if (poisonerP != null && !deathReason.getPath().equals("poison") && originalKiller != null
                            && !poisonerC.poisoner.equals(originalKiller.getUUID())) {

                        GameUtils.killPlayer(victim, false, poisonerP, SRE.id("poison"));
                        return null;
                    }
                    if (originalKiller != null)
                        return null;
                    return poisonerP;
                }
            }

            if (originalKiller != null)
                return null;
            if (gameWorldComponent.isRole(serverVictim, ModRoles.CONSPIRATOR))
                return null;
            // 是否为阴谋家击杀
            for (var player : serverVictim.level().players()) {
                if (gameWorldComponent.isRole(player, ModRoles.CONSPIRATOR)) {
                    var consC = RoleData.getOptional(ConspiratorRoleData.class, player);
                    if (consC.isPresent()) {
                        if (consC.get().hasBeenGuessedToDie(victim.getUUID())) {
                            return player;
                        }
                    }
                }
            }
            // 没找到
            return null;
        });
    }

}
