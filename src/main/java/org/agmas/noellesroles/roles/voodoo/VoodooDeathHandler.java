package org.agmas.noellesroles.roles.voodoo;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;

import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.world.entity.player.Player;

public class VoodooDeathHandler {
    public static void registerEvents() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (NoellesRolesConfig.HANDLER.instance().voodooNonKillerDeaths || killer != null) {
                StarGameWorldComponent gameWorldComponent = (StarGameWorldComponent) StarGameWorldComponent.KEY.get(victim.level());
                if (gameWorldComponent.isRole(victim, ModRoles.VOODOO)) {
                    VoodooPlayerComponent voodooPlayerComponent = (VoodooPlayerComponent) VoodooPlayerComponent.KEY
                            .get(victim);
                    if (voodooPlayerComponent.target != null) {
                        Player voodooed = victim.level().getPlayerByUUID(voodooPlayerComponent.target);
                        if (voodooed != null) {
                            if (GameFunctions.isPlayerAliveAndSurvival(voodooed) && voodooed != victim) {
                                GameFunctions.killPlayer(voodooed, true, null,
                                        Noellesroles.id("voodoo"));
                            }
                        }
                    }
                }
            }
        });
    }
}
