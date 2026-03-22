package org.agmas.noellesroles.roles.voodoo;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;

public class VoodooDeathHandler {
    public static void registerEvents() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (NoellesRolesConfig.HANDLER.instance().voodooNonKillerDeaths || killer != null) {
                SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(victim.level());
                if (gameWorldComponent.isRole(victim, ModRoles.VOODOO)) {
                    VoodooPlayerComponent voodooPlayerComponent = (VoodooPlayerComponent) VoodooPlayerComponent.KEY
                            .get(victim);
                    if (voodooPlayerComponent.target != null) {
                        Player voodooed = victim.level().getPlayerByUUID(voodooPlayerComponent.target);
                        if (voodooed != null) {
                            if (GameUtils.isPlayerAliveAndSurvival(voodooed) && voodooed != victim) {
                                GameUtils.killPlayer(voodooed, true, null,
                                        Noellesroles.id("voodoo"));
                            }
                        }
                    }
                }
            }
        });
    }
}
