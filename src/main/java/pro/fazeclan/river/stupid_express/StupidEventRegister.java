package pro.fazeclan.river.stupid_express;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.rules.ChatHudRules;
import io.wifi.starrailexpress.rules.ReplayRules;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent;
import pro.fazeclan.river.stupid_express.modifier.lovers.LoversWinCheckEvent;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.PlayerStatsBeforeRefugee;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistWinChecker;
import pro.fazeclan.river.stupid_express.role.initiate.SEInitiateEventHandler;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

public class StupidEventRegister {

    /**
     * 主类初始化阶段的事件 / 规则注册，从 {@link StupidExpress#onInitialize()} 中剥离，归一化到此处。
     */
    public static void registerInitEvents() {
        GameInitializeEvent.EVENT.register((ServerLevel, gameWorldComponent, serverPlayers) -> {
            var refugeeC = RefugeeComponent.KEY.get(ServerLevel);
            if (refugeeC != null) {
                refugeeC.reset();
            }
        });
        register();
        LoversWinCheckEvent.register();
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.getGameMode() instanceof WTLooseEndsGameMode)
                return;
            var modifierComponent = WorldModifierComponent.KEY.get(victim.level());
            if (gameWorldComponent != null) {
                SRERole role = gameWorldComponent.getRole(victim);
                if (role != null) {
                    if (role.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                        var refugeeComponent = RefugeeComponent.KEY.get(victim.level());
                        refugeeComponent.onLooseEndDeath(victim, deathReason);
                    }
                }
            }
            if (modifierComponent != null) {
                if (modifierComponent.isModifier(victim, SEModifiers.SPLIT_PERSONALITY)) {
                    var splc = SplitPersonalityComponent.KEY.get(victim);
                    if (splc != null && !splc.isDeath()) {
                        splc.setDeath(true);
                    }
                }
            }
        });
        GameInitializeEvent.EVENT.register((ServerLevel, gameWorldComponent, serverPlayers) -> {
            serverPlayers.forEach(serverPlayer -> {
                RemoveStatusBarPayload payload = new RemoveStatusBarPayload("loose_end");
                ServerPlayNetworking.send(serverPlayer, payload);
            });
        });

        ReplayRules.cantSendReplay.add(
                (player -> {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
                    return modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY);
                }));
        ChatHudRules.cantUseChatHud.add(
                (player -> {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
                    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
                    var role = gameComponent.getRole(player);
                    return role != null && !ChatHudRules.canUseChatHud.stream().anyMatch((pre) -> pre.test(role))
                            && modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY);
                }));

        PlayerStatsBeforeRefugee.RegisterDeathEvent();
    }

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
                            StupidExpress.forceDeathReason("broken_heart"));
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
