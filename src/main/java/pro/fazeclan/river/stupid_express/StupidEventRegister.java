package pro.fazeclan.river.stupid_express;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistWinChecker;
import pro.fazeclan.river.stupid_express.role.initiate.SEInitiateEventHandler;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

public class StupidEventRegister {

    public static void register() {
        // 死灵
        ArsonistWinChecker.registerEvent();
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var component = SREGameWorldComponent.KEY.get(victim.level());
            if (component.canUseKillerFeatures(victim)) {
                var nc = NecromancerComponent.KEY.get(victim.level());
                nc.increaseAvailableRevives();
                nc.sync();
            }
        });
        // 初学
        SEInitiateEventHandler.register();

        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            LoversComponent component = LoversComponent.KEY.get(victim);

            if (!component.isLover()) {
                return;
            }

            var level = victim.level();
            var lover = level.getPlayerByUUID(component.getLover());
            if (lover != null) {
                if (GameUtils.isPlayerAliveAndSurvival(lover)) {
                    GameUtils.forceKillPlayer(
                            lover,
                            true,
                            victim,
                            StupidExpress.id("broken_heart"));
                }
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            CursedComponent cursedComponent = CursedComponent.KEY.get(victim);

            if (cursedComponent.isCursed() && killer != null) {
                // Transfer curse
                cursedComponent.init();
                WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(victim.level());
                worldModifierComponent.removeModifier(victim.getUUID(), SEModifiers.CURSED);

                CursedComponent killerCursedComponent = CursedComponent.KEY.get(killer);
                killerCursedComponent.setCursed(killer.getUUID());
                killerCursedComponent.sync();
                worldModifierComponent.addModifier(killer.getUUID(), SEModifiers.CURSED);
            }
        });
    }
}
