package org.agmas.noellesroles;

import org.agmas.noellesroles.component.ConspiratorPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.event.EarlyKillPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.level.ServerPlayer;

public class TrueKillerFinder {

    public static void registerEvents() {
        EarlyKillPlayer.FIND_KILLER_EVENT.register((victim, originalKiller, deathReason) -> {
            if (!(victim instanceof ServerPlayer serverVictim))
                return null;
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
                    var consC = ConspiratorPlayerComponent.KEY.maybeGet(player).orElse(null);
                    if (consC != null) {
                        if (consC.hasBeenGuessedToDie(victim.getUUID())) {
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
