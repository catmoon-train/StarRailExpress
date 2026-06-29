package org.agmas.noellesroles.game.roles.innocence.voodoo;

import io.wifi.starrailexpress.api.SREGameModes;
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
                    VoodooPlayerComponent voodooPlayerComponent = (VoodooPlayerComponent) VoodooPlayerComponent.KEY
                            .get(victim);
                    if (voodooPlayerComponent.target != null) {
                        Player voodooed = victim.level().getPlayerByUUID(voodooPlayerComponent.target);
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
